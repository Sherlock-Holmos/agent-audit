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
