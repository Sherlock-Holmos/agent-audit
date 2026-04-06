package com.audit.data.service.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
/**
 * NiFi 控制平面编排服务：提供连通性检查与流程触发能力。
 */
public class NifiOrchestrationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final String defaultProcessGroupId;

    public NifiOrchestrationService(
        ObjectMapper objectMapper,
        @Value("${app.nifi.enabled:false}") boolean enabled,
        @Value("${app.nifi.base-url:http://localhost:8090}") String baseUrl,
        @Value("${app.nifi.timeout-seconds:5}") int timeoutSeconds,
        @Value("${app.nifi.default-process-group-id:}") String defaultProcessGroupId
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds))).build();
        this.enabled = enabled;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.defaultProcessGroupId = text(defaultProcessGroupId);
    }

    public Map<String, Object> getStatus() {
        if (!enabled) {
            return Map.of(
                "enabled", false,
                "reachable", false,
                "baseUrl", baseUrl,
                "message", "NiFi integration disabled"
            );
        }

        String url = baseUrl + "/nifi-api/flow/about";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            Map<String, Object> body = parseJsonMap(response.body());

            if (code == 401 || code == 403) {
                return Map.of(
                    "enabled", true,
                    "reachable", true,
                    "authRequired", true,
                    "baseUrl", baseUrl,
                    "httpStatus", code,
                    "message", "NiFi reachable but requires authentication"
                );
            }

            if (code >= 200 && code < 300) {
                return Map.of(
                    "enabled", true,
                    "reachable", true,
                    "authRequired", false,
                    "baseUrl", baseUrl,
                    "httpStatus", code,
                    "about", body
                );
            }

            return Map.of(
                "enabled", true,
                "reachable", false,
                "baseUrl", baseUrl,
                "httpStatus", code,
                "message", "NiFi endpoint returned non-success status"
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NiFi status check interrupted");
        } catch (IOException ex) {
            return Map.of(
                "enabled", true,
                "reachable", false,
                "baseUrl", baseUrl,
                "message", "NiFi status check failed: " + ex.getMessage()
            );
        }
    }

    public Map<String, Object> triggerFlow(String flowType, String processGroupId, Map<String, Object> parameters) {
        if (!enabled) {
            throw new IllegalArgumentException("NiFi integration is disabled");
        }

        String normalizedFlowType = normalizeFlowType(flowType);
        String effectiveProcessGroupId = text(processGroupId);
        if (effectiveProcessGroupId.isBlank()) {
            effectiveProcessGroupId = defaultProcessGroupId;
        }
        if (effectiveProcessGroupId.isBlank()) {
            throw new IllegalArgumentException("processGroupId 不能为空，且未配置默认 APP_NIFI_DEFAULT_PROCESS_GROUP_ID");
        }

        String encodedId = URLEncoder.encode(effectiveProcessGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/flow/process-groups/" + encodedId;

        Map<String, Object> body = new HashMap<>();
        body.put("id", effectiveProcessGroupId);
        body.put("state", "RUNNING");

        if (parameters != null && !parameters.isEmpty()) {
            body.put("variables", parameters);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            Map<String, Object> responseBody = parseJsonMap(response.body());

            if (code < 200 || code >= 300) {
                throw new IllegalStateException("NiFi flow trigger failed, httpStatus=" + code + ", body=" + response.body());
            }

            String externalRunId = extractExternalRunId(responseBody, effectiveProcessGroupId);
            Map<String, Object> result = new HashMap<>();
            result.put("flowType", normalizedFlowType);
            result.put("processGroupId", effectiveProcessGroupId);
            result.put("dispatchStatus", "SUBMITTED");
            result.put("externalRunId", externalRunId);
            result.put("httpStatus", code);
            result.put("submittedAt", now());
            result.put("response", responseBody);
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NiFi flow trigger interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("NiFi flow trigger failed: " + ex.getMessage());
        }
    }

    public String autoDiscoverProcessGroupId(String flowType) {
        if (!enabled) {
            return "";
        }

        String normalizedFlowType = normalizeFlowType(flowType);
        String url = baseUrl + "/nifi-api/flow/process-groups/root";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }

            Map<String, Object> root = parseJsonMap(response.body());
            Map<String, Object> processGroupFlow = asMap(root.get("processGroupFlow"));
            Map<String, Object> flow = asMap(processGroupFlow.get("flow"));
            List<Map<String, Object>> processGroups = asMapList(flow.get("processGroups"));

            for (Map<String, Object> processGroup : processGroups) {
                Map<String, Object> component = asMap(processGroup.get("component"));
                String name = text(component.get("name")).toUpperCase();
                String id = text(component.get("id"));
                if (!name.isBlank() && !id.isBlank() && name.contains(normalizedFlowType)) {
                    return id;
                }
            }
            return "";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        } catch (IOException ex) {
            return "";
        }
    }

    public String ensureProcessGroupForFlowType(String flowType) {
        if (!enabled) {
            throw new IllegalArgumentException("NiFi integration is disabled");
        }

        String normalizedFlowType = normalizeFlowType(flowType);
        String existing = autoDiscoverProcessGroupId(normalizedFlowType);
        if (!existing.isBlank()) {
            return existing;
        }

        String rootId = loadRootProcessGroupId();
        String groupName = "AUDIT_" + normalizedFlowType;
        return createProcessGroup(rootId, groupName);
    }

    public Map<String, Object> provisionFlowBlueprint(Map<String, Object> blueprint) {
        if (!enabled) {
            throw new IllegalArgumentException("NiFi integration is disabled");
        }

        Map<String, Object> safeBlueprint = blueprint == null ? Map.of() : blueprint;
        String groupName = text(safeBlueprint.get("groupName"));
        if (groupName.isBlank()) {
            throw new IllegalArgumentException("groupName 不能为空");
        }

        String parentProcessGroupId = text(safeBlueprint.get("parentProcessGroupId"));
        if (parentProcessGroupId.isBlank()) {
            parentProcessGroupId = defaultProcessGroupId;
        }
        if (parentProcessGroupId.isBlank()) {
            parentProcessGroupId = loadRootProcessGroupId();
        }

        String processGroupId = createProcessGroup(parentProcessGroupId, groupName);
        Map<String, String> createdIds = new LinkedHashMap<>();
        Map<String, String> createdComponentTypes = new LinkedHashMap<>();

        String parameterContextId = "";
        Object parameterContextSpec = safeBlueprint.get("parameterContext");
        if (parameterContextSpec instanceof Map<?, ?> map) {
            parameterContextId = createParameterContext(castMap(map));
            if (!parameterContextId.isBlank()) {
                attachParameterContext(processGroupId, parameterContextId);
            }
        }

        List<Map<String, Object>> controllerServices = asMapList(safeBlueprint.get("controllerServices"));
        for (Map<String, Object> spec : controllerServices) {
            String id = createControllerService(processGroupId, spec);
            String name = text(spec.get("name"));
            if (!name.isBlank()) {
                createdIds.put(name, id);
                createdComponentTypes.put(name, "CONTROLLER_SERVICE");
            }
            if (!id.isBlank()) {
                createdIds.put(id, id);
                createdComponentTypes.put(id, "CONTROLLER_SERVICE");
            }
        }

        List<Map<String, Object>> processors = asMapList(safeBlueprint.get("processors"));
        for (Map<String, Object> spec : processors) {
            String id = createProcessor(processGroupId, spec);
            String name = text(spec.get("name"));
            if (!name.isBlank()) {
                createdIds.put(name, id);
                createdComponentTypes.put(name, "PROCESSOR");
            }
            if (!id.isBlank()) {
                createdIds.put(id, id);
                createdComponentTypes.put(id, "PROCESSOR");
            }
        }

        List<Map<String, Object>> outputPorts = asMapList(safeBlueprint.get("outputPorts"));
        for (Map<String, Object> spec : outputPorts) {
            String id = createOutputPort(processGroupId, spec);
            String name = text(spec.get("name"));
            if (!name.isBlank()) {
                createdIds.put(name, id);
                createdComponentTypes.put(name, "OUTPUT_PORT");
            }
            if (!id.isBlank()) {
                createdIds.put(id, id);
                createdComponentTypes.put(id, "OUTPUT_PORT");
            }
        }

        List<Map<String, Object>> connections = asMapList(safeBlueprint.get("connections"));
        for (Map<String, Object> spec : connections) {
            String id = createConnection(processGroupId, spec, createdIds, createdComponentTypes);
            String name = text(spec.get("name"));
            if (!name.isBlank()) {
                createdIds.put(name, id);
                createdComponentTypes.put(name, "CONNECTION");
            }
            if (!id.isBlank()) {
                createdIds.put(id, id);
                createdComponentTypes.put(id, "CONNECTION");
            }
        }

        boolean startAfterCreate = Boolean.TRUE.equals(safeBlueprint.get("startAfterCreate"));
        if (startAfterCreate) {
            startProcessGroup(processGroupId);
        }

        return Map.of(
            "processGroupId", processGroupId,
            "parameterContextId", parameterContextId,
            "createdIds", createdIds,
            "groupName", groupName,
            "parentProcessGroupId", parentProcessGroupId
        );
    }

    public int getProcessGroupProcessorCount(String processGroupId) {
        String safeId = text(processGroupId);
        if (safeId.isBlank()) {
            return 0;
        }
        String encodedId = URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/flow/process-groups/" + encodedId;
        Map<String, Object> payload = httpGetJson(url);
        Map<String, Object> processGroupFlow = asMap(payload.get("processGroupFlow"));
        Map<String, Object> flow = asMap(processGroupFlow.get("flow"));
        List<Map<String, Object>> processors = asMapList(flow.get("processors"));
        return processors.size();
    }

    public boolean isFusionNativeProcessGroup(String processGroupId) {
        String safeId = text(processGroupId);
        if (safeId.isBlank()) {
            return false;
        }
        String encodedId = URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/flow/process-groups/" + encodedId;
        Map<String, Object> payload = httpGetJson(url);
        Map<String, Object> processGroupFlow = asMap(payload.get("processGroupFlow"));
        Map<String, Object> flow = asMap(processGroupFlow.get("flow"));
        List<Map<String, Object>> processors = asMapList(flow.get("processors"));
        for (Map<String, Object> processorEntity : processors) {
            Map<String, Object> component = asMap(processorEntity.get("component"));
            String type = text(component.get("type"));
            String name = text(component.get("name")).toUpperCase();
            if ("org.apache.nifi.processors.script.ExecuteScript".equals(type)
                && name.contains("FUSION")
                && name.contains("NATIVE")) {
                return true;
            }
        }
        return false;
    }

    public boolean isCleanNativeProcessGroup(String processGroupId) {
        String safeId = text(processGroupId);
        if (safeId.isBlank()) {
            return false;
        }
        String encodedId = URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/flow/process-groups/" + encodedId;
        Map<String, Object> payload = httpGetJson(url);
        Map<String, Object> processGroupFlow = asMap(payload.get("processGroupFlow"));
        Map<String, Object> flow = asMap(processGroupFlow.get("flow"));
        List<Map<String, Object>> processors = asMapList(flow.get("processors"));
        for (Map<String, Object> processorEntity : processors) {
            Map<String, Object> component = asMap(processorEntity.get("component"));
            String type = text(component.get("type"));
            String name = text(component.get("name")).toUpperCase();
            if ("org.apache.nifi.processors.script.ExecuteScript".equals(type)
                && name.contains("CLEAN")
                && name.contains("NATIVE")) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> ensureCallbackProcessorsReady(String processGroupId) {
        String safeId = text(processGroupId);
        if (safeId.isBlank()) {
            return Map.of(
                "processGroupId", "",
                "callbackProcessors", 0,
                "repairedCallbacks", 0,
                "invalidCallbacksBefore", 0,
                "invalidCallbacksAfter", 0
            );
        }

        String encodedId = URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/flow/process-groups/" + encodedId;
        Map<String, Object> payload = httpGetJson(url);
        Map<String, Object> processGroupFlow = asMap(payload.get("processGroupFlow"));
        Map<String, Object> flow = asMap(processGroupFlow.get("flow"));
        List<Map<String, Object>> processors = asMapList(flow.get("processors"));

        int callbackProcessors = 0;
        int repairedCallbacks = 0;
        int invalidCallbacksBefore = 0;
        for (Map<String, Object> processorEntity : processors) {
            Map<String, Object> component = asMap(processorEntity.get("component"));
            String type = text(component.get("type"));
            String name = text(component.get("name"));
            if (!"org.apache.nifi.processors.standard.InvokeHTTP".equals(type)) {
                continue;
            }
            if (!(name.toUpperCase().contains("CALLBACK") || name.toUpperCase().contains("POLL"))) {
                continue;
            }

            callbackProcessors++;
            Map<String, Object> status = asMap(processorEntity.get("status"));
            String runStatus = text(status.get("runStatus")).toUpperCase();
            String state = text(component.get("state")).toUpperCase();
            if ("INVALID".equals(runStatus) || "STOPPED".equals(state)) {
                invalidCallbacksBefore++;
            }

            String processorId = text(component.get("id"));
            List<String> allRelationships = asMapList(component.get("relationships")).stream()
                .map(item -> text(item.get("name")))
                .filter(item -> !item.isBlank())
                .toList();
            if (!processorId.isBlank() && !allRelationships.isEmpty()) {
                updateProcessorAutoTerminatedRelationships(processorId, allRelationships);
                repairedCallbacks++;
            }
        }

        if (callbackProcessors > 0) {
            startProcessGroup(safeId);
        }

        Map<String, Object> refreshed = httpGetJson(url);
        Map<String, Object> refreshedPgf = asMap(refreshed.get("processGroupFlow"));
        Map<String, Object> refreshedFlow = asMap(refreshedPgf.get("flow"));
        List<Map<String, Object>> refreshedProcessors = asMapList(refreshedFlow.get("processors"));
        int invalidCallbacksAfter = 0;
        for (Map<String, Object> processorEntity : refreshedProcessors) {
            Map<String, Object> component = asMap(processorEntity.get("component"));
            String type = text(component.get("type"));
            String name = text(component.get("name"));
            if (!"org.apache.nifi.processors.standard.InvokeHTTP".equals(type)) {
                continue;
            }
            if (!(name.toUpperCase().contains("CALLBACK") || name.toUpperCase().contains("POLL"))) {
                continue;
            }
            Map<String, Object> status = asMap(processorEntity.get("status"));
            String runStatus = text(status.get("runStatus")).toUpperCase();
            String state = text(component.get("state")).toUpperCase();
            if ("INVALID".equals(runStatus) || "STOPPED".equals(state)) {
                invalidCallbacksAfter++;
            }
        }

        return Map.of(
            "processGroupId", safeId,
            "callbackProcessors", callbackProcessors,
            "repairedCallbacks", repairedCallbacks,
            "invalidCallbacksBefore", invalidCallbacksBefore,
            "invalidCallbacksAfter", invalidCallbacksAfter
        );
    }

    private String createParameterContext(Map<String, Object> spec) {
        String name = text(spec.get("name"));
        if (name.isBlank()) {
            throw new IllegalArgumentException("parameterContext.name 不能为空");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", Map.of("version", 0));
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", name);
        component.put("description", text(spec.get("description")));

        Map<String, Object> parameters = new LinkedHashMap<>();
        Map<String, Object> rawParameters = castMap(spec.get("parameters"));
        for (Map.Entry<String, Object> entry : rawParameters.entrySet()) {
            String key = text(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("value", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            parameter.put("sensitive", Boolean.FALSE);
            parameters.put(key, Map.of("parameter", parameter));
        }
        component.put("parameters", parameters);
        body.put("component", component);

        String url = baseUrl + "/nifi-api/parameter-contexts";
        Map<String, Object> response = invokeJson("POST", url, body);
        Map<String, Object> created = asMap(response.get("component"));
        String id = text(created.get("id"));
        if (id.isBlank()) {
            id = text(response.get("id"));
        }
        return id;
    }

    private void updateProcessorAutoTerminatedRelationships(String processorId, List<String> relationships) {
        if (processorId == null || processorId.isBlank() || relationships == null || relationships.isEmpty()) {
            return;
        }

        String encodedId = URLEncoder.encode(processorId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/processors/" + encodedId;
        Map<String, Object> entity = invokeJson("GET", url, null);
        Map<String, Object> revision = asMap(entity.get("revision"));
        Map<String, Object> component = asMap(entity.get("component"));
        boolean wasRunning = "RUNNING".equalsIgnoreCase(text(component.get("state")));
        if (wasRunning) {
            updateProcessorRunStatus(processorId, "STOPPED", revision);
            entity = invokeJson("GET", url, null);
            revision = asMap(entity.get("revision"));
            component = asMap(entity.get("component"));
        }

        Set<String> target = relationships.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .collect(Collectors.toSet());
        List<String> autoTerminatedRelationships = new ArrayList<>(target);

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> config = asMap(component.get("config"));
        asMap(config.get("properties")).forEach((k, v) -> properties.put(String.valueOf(k), v));

        Map<String, Object> configBody = new LinkedHashMap<>();
        configBody.put("properties", properties);
        configBody.put("autoTerminatedRelationships", autoTerminatedRelationships);

        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> revisionBody = new LinkedHashMap<>();
        revisionBody.put("version", revision.getOrDefault("version", 0));
        if (revision.containsKey("clientId")) {
            revisionBody.put("clientId", text(revision.get("clientId")));
        }
        body.put("revision", revisionBody);
        body.put("component", Map.of(
            "id", processorId,
            "config", configBody
        ));
        Map<String, Object> updated = invokeJson("PUT", url, body);
        if (wasRunning) {
            updateProcessorRunStatus(processorId, "RUNNING", asMap(updated.get("revision")));
        }
    }

    private void updateProcessorRunStatus(String processorId, String state, Map<String, Object> revision) {
        String encodedId = URLEncoder.encode(processorId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/processors/" + encodedId + "/run-status";
        Map<String, Object> revisionBody = new LinkedHashMap<>();
        revisionBody.put("version", revision.getOrDefault("version", 0));
        if (revision.containsKey("clientId")) {
            revisionBody.put("clientId", text(revision.get("clientId")));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", revisionBody);
        body.put("state", text(state).toUpperCase());
        body.put("disconnectedNodeAcknowledged", Boolean.FALSE);
        invokeJson("PUT", url, body);
    }

    private void attachParameterContext(String processGroupId, String parameterContextId) {
        String encodedId = URLEncoder.encode(processGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/process-groups/" + encodedId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", Map.of("version", 0));
        body.put("component", Map.of(
            "id", processGroupId,
            "parameterContext", Map.of("id", parameterContextId)
        ));
        invokeJson("PUT", url, body);
    }

    private String createControllerService(String processGroupId, Map<String, Object> spec) {
        String type = text(spec.get("type"));
        String name = text(spec.get("name"));
        if (type.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("controllerServices 需要 type 和 name");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", Map.of("version", 0));
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", name);
        component.put("type", type);
        component.put("properties", castMap(spec.get("properties")));
        body.put("component", component);

        String encodedId = URLEncoder.encode(processGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/process-groups/" + encodedId + "/controller-services";
        Map<String, Object> response = invokeJson("POST", url, body);
        Map<String, Object> created = asMap(response.get("component"));
        String id = text(created.get("id"));
        if (id.isBlank()) {
            id = text(response.get("id"));
        }
        return id;
    }

    private String createProcessor(String processGroupId, Map<String, Object> spec) {
        String type = text(spec.get("type"));
        String name = text(spec.get("name"));
        if (type.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("processors 需要 type 和 name");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", Map.of("version", 0));
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", name);
        component.put("type", type);
        component.put("position", Map.of(
            "x", spec.getOrDefault("x", 0.0),
            "y", spec.getOrDefault("y", 0.0)
        ));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("properties", castMap(spec.get("properties")));
        if (spec.containsKey("schedulingStrategy")) {
            config.put("schedulingStrategy", text(spec.get("schedulingStrategy")));
        }
        if (spec.containsKey("schedulingPeriod")) {
            config.put("schedulingPeriod", text(spec.get("schedulingPeriod")));
        }
        if (spec.containsKey("runDurationMillis")) {
            config.put("runDurationMillis", spec.get("runDurationMillis"));
        }
        if (spec.containsKey("autoTerminatedRelationships")) {
            config.put("autoTerminatedRelationships", castStringList(spec.get("autoTerminatedRelationships")));
        }
        component.put("config", config);
        body.put("component", component);

        String encodedId = URLEncoder.encode(processGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/process-groups/" + encodedId + "/processors";
        Map<String, Object> response = invokeJson("POST", url, body);
        Map<String, Object> created = asMap(response.get("component"));
        String id = text(created.get("id"));
        if (id.isBlank()) {
            id = text(response.get("id"));
        }
        return id;
    }

    private String createConnection(String processGroupId, Map<String, Object> spec, Map<String, String> createdIds, Map<String, String> createdComponentTypes) {
        Object sourceRef = spec.get("source");
        Object destinationRef = spec.get("destination");
        String sourceId = resolveCreatedComponentId(createdIds, sourceRef);
        String destinationId = resolveCreatedComponentId(createdIds, destinationRef);
        if (sourceId.isBlank() || destinationId.isBlank()) {
            throw new IllegalArgumentException("connection.source 或 destination 无法解析");
        }
        String sourceType = resolveComponentType(createdComponentTypes, sourceRef, sourceId, "PROCESSOR");
        String destinationType = resolveComponentType(createdComponentTypes, destinationRef, destinationId, "PROCESSOR");

        List<String> selectedRelationships = castStringList(spec.get("selectedRelationships"));
        if (selectedRelationships.isEmpty()) {
            selectedRelationships = List.of("success");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", Map.of("version", 0));
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", text(spec.get("name")));
        component.put("source", Map.of("id", sourceId, "type", sourceType, "groupId", processGroupId));
        component.put("destination", Map.of("id", destinationId, "type", destinationType, "groupId", processGroupId));
        component.put("selectedRelationships", selectedRelationships);
        component.put("backPressureObjectThreshold", text(spec.getOrDefault("backPressureObjectThreshold", "1000")));
        component.put("backPressureDataSizeThreshold", text(spec.getOrDefault("backPressureDataSizeThreshold", "1 GB")));
        component.put("flowFileExpiration", text(spec.getOrDefault("flowFileExpiration", "0 sec")));
        body.put("component", component);

        String encodedId = URLEncoder.encode(processGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/process-groups/" + encodedId + "/connections";
        Map<String, Object> response = invokeJson("POST", url, body);
        Map<String, Object> created = asMap(response.get("component"));
        String id = text(created.get("id"));
        if (id.isBlank()) {
            id = text(response.get("id"));
        }
        return id;
    }

    private String createOutputPort(String processGroupId, Map<String, Object> spec) {
        String name = text(spec.get("name"));
        if (name.isBlank()) {
            throw new IllegalArgumentException("outputPorts 需要 name");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", Map.of("version", 0));
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", name);
        component.put("position", Map.of(
            "x", spec.getOrDefault("x", 0.0),
            "y", spec.getOrDefault("y", 0.0)
        ));
        body.put("component", component);

        String encodedId = URLEncoder.encode(processGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/process-groups/" + encodedId + "/output-ports";
        Map<String, Object> response = invokeJson("POST", url, body);
        Map<String, Object> created = asMap(response.get("component"));
        String id = text(created.get("id"));
        if (id.isBlank()) {
            id = text(response.get("id"));
        }
        return id;
    }

    private String resolveComponentType(Map<String, String> createdComponentTypes, Object ref, String resolvedId, String fallback) {
        String byRef = text(createdComponentTypes.get(text(ref)));
        if (!byRef.isBlank()) {
            return byRef;
        }
        String byId = text(createdComponentTypes.get(resolvedId));
        if (!byId.isBlank()) {
            return byId;
        }
        return fallback;
    }

    private void startProcessGroup(String processGroupId) {
        String encodedId = URLEncoder.encode(processGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/flow/process-groups/" + encodedId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", processGroupId);
        body.put("state", "RUNNING");
        invokeJson("PUT", url, body);
    }

    private Map<String, Object> invokeJson(String method, String url, Map<String, Object> body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json");

        String normalizedMethod = text(method).toUpperCase();
        HttpRequest request;
        if ("GET".equals(normalizedMethod)) {
            request = builder.GET().build();
        } else if ("POST".equals(normalizedMethod)) {
            request = builder.POST(HttpRequest.BodyPublishers.ofString(toJson(body))).build();
        } else if ("PUT".equals(normalizedMethod)) {
            request = builder.PUT(HttpRequest.BodyPublishers.ofString(toJson(body))).build();
        } else if ("PATCH".equals(normalizedMethod)) {
            request = builder.method("PATCH", HttpRequest.BodyPublishers.ofString(toJson(body))).build();
        } else {
            throw new IllegalArgumentException("不支持的方法: " + method);
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("NiFi API request failed, httpStatus=" + response.statusCode() + ", url=" + url + ", body=" + response.body());
            }
            return parseJsonMap(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NiFi API request interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("NiFi API request failed: " + ex.getMessage());
        }
    }

    private String loadRootProcessGroupId() {
        String url = baseUrl + "/nifi-api/flow/process-groups/root";
        Map<String, Object> root = httpGetJson(url);
        Map<String, Object> processGroupFlow = asMap(root.get("processGroupFlow"));
        String rootId = text(processGroupFlow.get("id"));
        if (rootId.isBlank()) {
            throw new IllegalStateException("Unable to resolve NiFi root process group id");
        }
        return rootId;
    }

    private String createProcessGroup(String parentProcessGroupId, String groupName) {
        String encodedId = URLEncoder.encode(parentProcessGroupId, StandardCharsets.UTF_8);
        String url = baseUrl + "/nifi-api/process-groups/" + encodedId + "/process-groups";

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("revision", Map.of("version", 0));
        requestBody.put("component", Map.of(
            "name", groupName,
            "position", Map.of("x", 0.0, "y", 0.0)
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("NiFi process group creation failed, httpStatus=" + response.statusCode() + ", body=" + response.body());
            }
            Map<String, Object> created = parseJsonMap(response.body());
            Map<String, Object> component = asMap(created.get("component"));
            String id = text(component.get("id"));
            if (id.isBlank()) {
                id = text(created.get("id"));
            }
            if (id.isBlank()) {
                throw new IllegalStateException("NiFi process group created but id is missing");
            }
            return id;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NiFi process group creation interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("NiFi process group creation failed: " + ex.getMessage());
        }
    }

    private Map<String, Object> httpGetJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("NiFi API request failed, httpStatus=" + response.statusCode() + ", url=" + url);
            }
            return parseJsonMap(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NiFi API request interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("NiFi API request failed: " + ex.getMessage());
        }
    }

    private String normalizeFlowType(String flowType) {
        String normalized = text(flowType).toUpperCase();
        return normalized.isBlank() ? "INGEST" : normalized;
    }

    private String resolveCreatedComponentId(Map<String, String> createdIds, Object ref) {
        if (ref == null) {
            return "";
        }
        if (ref instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id != null && !text(id).isBlank()) {
                return text(id);
            }
            Object name = map.get("name");
            if (name != null) {
                String resolved = createdIds.get(text(name));
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        String textRef = text(ref);
        if (textRef.isBlank()) {
            return "";
        }
        String resolved = createdIds.get(textRef);
        return resolved == null ? textRef : resolved;
    }

    private String normalizeBaseUrl(String rawUrl) {
        String normalized = text(rawUrl);
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String extractExternalRunId(Map<String, Object> responseBody, String fallback) {
        Object directId = responseBody.get("id");
        if (directId != null) {
            return String.valueOf(directId);
        }
        Object processGroupFlow = responseBody.get("processGroupFlow");
        if (processGroupFlow instanceof Map<?, ?> pg) {
            Object pgId = pg.get("id");
            if (pgId != null) {
                return String.valueOf(pgId);
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                out.add(typed);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String textValue = text(item);
            if (!textValue.isBlank()) {
                out.add(textValue);
            }
        }
        return out;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON序列化失败");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String now() {
        return DATE_TIME_FORMATTER.format(Instant.now());
    }
}
