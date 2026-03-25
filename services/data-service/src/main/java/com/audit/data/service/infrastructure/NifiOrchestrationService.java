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
import java.util.HashMap;
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
