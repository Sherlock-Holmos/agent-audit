package com.audit.data.service.orchestration;

import com.audit.data.service.api.IDataProcessAsyncService;
import com.audit.data.service.api.IDataProcessService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * 异步任务编排服务：负责任务入队、状态流转、幂等控制、失败重试与告警发送。
 */
public class DataProcessAsyncService implements IDataProcessAsyncService {

    private static final Logger log = LoggerFactory.getLogger(DataProcessAsyncService.class);

    private final JdbcTemplate jdbcTemplate;
    private final IDataProcessService dataProcessService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final Counter jobFailedCounter;
    private final Counter jobCompletedCounter;
    private final MeterRegistry meterRegistry;
    private final List<String> alertWebhookUrls;
    private final long alertSuppressSeconds;
    private final Map<String, Long> alertSuppressionState = new ConcurrentHashMap<>();

    public DataProcessAsyncService(
        JdbcTemplate jdbcTemplate,
        IDataProcessService dataProcessService,
        ObjectMapper objectMapper,
        @Qualifier("processTaskExecutor") TaskExecutor taskExecutor,
        MeterRegistry meterRegistry,
        @Value("${app.alert.webhook-url:}") String alertWebhookUrl,
        @Value("${app.alert.webhook-urls:}") String alertWebhookUrls,
        @Value("${app.alert.suppress-seconds:300}") long alertSuppressSeconds
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataProcessService = dataProcessService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.meterRegistry = meterRegistry;
        this.alertWebhookUrls = mergeAlertUrls(alertWebhookUrl, alertWebhookUrls);
        this.alertSuppressSeconds = Math.max(alertSuppressSeconds, 0L);
        this.jobFailedCounter = Counter.builder("audit.process.job.failed").register(meterRegistry);
        this.jobCompletedCounter = Counter.builder("audit.process.job.completed").register(meterRegistry);
        ensureJobColumns();
    }

    public Map<String, Object> startCleanTask(String ownerUsername, Long taskId, String idempotencyKey) {
        ensureTaskExists(ownerUsername, taskId, "clean_task_record", "娓呮礂浠诲姟涓嶅瓨鍦");
        return startTask(ownerUsername, "CLEAN", taskId, idempotencyKey, () -> dataProcessService.runCleanTask(ownerUsername, taskId));
    }

    public Map<String, Object> startFusionTask(String ownerUsername, Long taskId, String idempotencyKey) {
        ensureTaskExists(ownerUsername, taskId, "fusion_task_record", "铻嶅悎浠诲姟涓嶅瓨鍦");
        return startTask(ownerUsername, "FUSION", taskId, idempotencyKey, () -> dataProcessService.runFusionTask(ownerUsername, taskId));
    }

    public Map<String, Object> getJobStatus(String ownerUsername, String jobId) {
        var rows = jdbcTemplate.query(
            "SELECT * FROM process_job_record WHERE owner_username=? AND job_id=?",
            (rs, i) -> jobRow(rs),
            ownerUsername,
            jobId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("浠诲姟涓嶅瓨鍦");
        }
        return rows.get(0);
    }

    private Map<String, Object> startTask(
        String ownerUsername,
        String taskType,
        Long taskId,
        String idempotencyKey,
        Supplier<Map<String, Object>> action
    ) {
        // 写任务记录后交给线程池执行，接口层立即返回 jobId。
        if (ownerUsername == null || ownerUsername.isBlank() || "anonymous".equalsIgnoreCase(ownerUsername)) {
            throw new IllegalArgumentException("鏈璇佺敤鎴蜂笉鍏佽鎻愪氦寮傛浠诲姟");
        }
        String idemKey = normalizeIdempotencyKey(idempotencyKey);
        if (idemKey != null) {
            String existingJobId = findExistingJobId(ownerUsername, taskType, taskId, idemKey);
            if (existingJobId != null) {
                return getJobStatus(ownerUsername, existingJobId);
            }
        }

        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO process_job_record(job_id,owner_username,task_type,task_ref_id,status,error_message,result_json,created_at,updated_at)
            VALUES(?,?,?,?, 'QUEUED','',NULL,?,?)
            """,
            jobId,
            ownerUsername,
            taskType,
            taskId,
            now,
            now
        );

        if (idemKey != null) {
            jdbcTemplate.update(
                """
                INSERT INTO task_idempotency_record(owner_username,task_type,task_ref_id,idempotency_key,job_id,status,created_at,updated_at)
                VALUES(?,?,?,?,?, 'PROCESSING',?,?)
                """,
                ownerUsername,
                taskType,
                taskId,
                idemKey,
                jobId,
                now,
                now
            );
        }

        taskExecutor.execute(() -> runJob(ownerUsername, taskType, taskId, jobId, idemKey, action));
        return getJobStatus(ownerUsername, jobId);
    }

    private void runJob(
        String ownerUsername,
        String taskType,
        Long taskId,
        String jobId,
        String idemKey,
        Supplier<Map<String, Object>> action
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        markRunning(jobId);
        try {
            Map<String, Object> result = runWithRetry(action);
            markCompleted(ownerUsername, taskType, taskId, jobId, idemKey, result);
        } catch (RuntimeException ex) {
            markFailed(ownerUsername, taskType, taskId, jobId, idemKey, ex);
        } finally {
            sample.stop(Timer.builder("audit.process.job.duration")
                .tag("taskType", taskType)
                .register(meterRegistry));
        }
    }

    private Map<String, Object> runWithRetry(Supplier<Map<String, Object>> action) {
        // 对瞬时失败做一次短间隔重试，避免偶发抖动直接失败。
        RuntimeException lastError = null;
        for (int i = 0; i < 2; i++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                lastError = ex;
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw lastError == null ? new IllegalStateException("鎵ц澶辫触") : lastError;
    }

    private void markRunning(String jobId) {
        jdbcTemplate.update(
            "UPDATE process_job_record SET status='RUNNING', updated_at=? WHERE job_id=?",
            Instant.now(),
            jobId
        );
    }

    private void markCompleted(
        String ownerUsername,
        String taskType,
        Long taskId,
        String jobId,
        String idemKey,
        Map<String, Object> result
    ) {
        String resultJson = toJson(result);
        Instant now = Instant.now();
        jdbcTemplate.update(
            "UPDATE process_job_record SET status='COMPLETED', error_message='', result_json=?, updated_at=? WHERE job_id=?",
            resultJson,
            now,
            jobId
        );
        if (idemKey != null) {
            jdbcTemplate.update(
                """
                UPDATE task_idempotency_record
                   SET status='COMPLETED', updated_at=?
                 WHERE owner_username=? AND task_type=? AND task_ref_id=? AND idempotency_key=?
                """,
                now,
                ownerUsername,
                taskType,
                taskId,
                idemKey
            );
        }
        jobCompletedCounter.increment();
    }

    private void markFailed(
        String ownerUsername,
        String taskType,
        Long taskId,
        String jobId,
        String idemKey,
        RuntimeException ex
    ) {
        String message = ex.getMessage() == null ? "鎵ц澶辫触" : ex.getMessage();
        String failureCategory = classifyFailureCategory(message);
        String alertStatus = sendFailureAlert(ownerUsername, taskType, taskId, jobId, message, failureCategory) ? "SENT" : "SKIPPED";
        Instant now = Instant.now();
        jdbcTemplate.update(
            "UPDATE process_job_record SET status='FAILED', error_message=?, failure_category=?, alert_status=?, updated_at=? WHERE job_id=?",
            message,
            failureCategory,
            alertStatus,
            now,
            jobId
        );
        if (idemKey != null) {
            jdbcTemplate.update(
                """
                UPDATE task_idempotency_record
                   SET status='FAILED', updated_at=?
                 WHERE owner_username=? AND task_type=? AND task_ref_id=? AND idempotency_key=?
                """,
                now,
                ownerUsername,
                taskType,
                taskId,
                idemKey
            );
        }
        log.warn("process async task failed owner={} taskType={} taskId={} jobId={} error={}", ownerUsername, taskType, taskId, jobId, message);
        jobFailedCounter.increment();
    }

    private String findExistingJobId(String ownerUsername, String taskType, Long taskId, String idempotencyKey) {
        var rows = jdbcTemplate.query(
            """
            SELECT job_id
              FROM task_idempotency_record
             WHERE owner_username=? AND task_type=? AND task_ref_id=? AND idempotency_key=?
             ORDER BY id DESC
             LIMIT 1
            """,
            (rs, i) -> rs.getString("job_id"),
            ownerUsername,
            taskType,
            taskId,
            idempotencyKey
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void ensureTaskExists(String ownerUsername, Long taskId, String tableName, String notFoundMessage) {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM " + tableName + " WHERE owner_username=? AND id=?",
            Integer.class,
            ownerUsername,
            taskId
        );
        if (cnt == null || cnt == 0) {
            throw new IllegalArgumentException(notFoundMessage);
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> jobRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", rs.getString("job_id"));
        row.put("taskType", rs.getString("task_type"));
        row.put("taskRefId", rs.getLong("task_ref_id"));
        row.put("status", rs.getString("status"));
        row.put("errorMessage", rs.getString("error_message"));
        row.put("failureCategory", rs.getString("failure_category"));
        row.put("alertStatus", rs.getString("alert_status"));
        row.put("createdAt", rs.getTimestamp("created_at") == null ? "" : rs.getTimestamp("created_at").toInstant().toString());
        row.put("updatedAt", rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toInstant().toString());
        row.put("result", parseJsonMap(rs.getString("result_json")));
        return row;
    }

    private void ensureJobColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE process_job_record ADD COLUMN failure_category VARCHAR(64)");
        } catch (Exception ignore) {
            // ignore if exists
        }
        try {
            jdbcTemplate.execute("ALTER TABLE process_job_record ADD COLUMN alert_status VARCHAR(16)");
        } catch (Exception ignore) {
            // ignore if exists
        }
    }

    private String classifyFailureCategory(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        if (normalized.contains("瓒呮椂") || normalized.contains("timeout")) return "TIMEOUT";
        if (normalized.contains("鏉冮檺") || normalized.contains("unauthorized") || normalized.contains("forbidden")) return "AUTH";
        if (normalized.contains("杩炴帴") || normalized.contains("connection") || normalized.contains("缃戠粶")) return "CONNECTIVITY";
        if (normalized.contains("sql") || normalized.contains("table") || normalized.contains("鏁版嵁搴")) return "DATA";
        if (normalized.contains("鍙傛暟") || normalized.contains("invalid") || normalized.contains("涓嶅悎娉")) return "VALIDATION";
        return "UNKNOWN";
    }

    private boolean sendFailureAlert(String ownerUsername, String taskType, Long taskId, String jobId, String message, String category) {
        if (alertWebhookUrls.isEmpty()) {
            return false;
        }
        // 相同任务 + 分类在抑制窗口内仅发送一次，防止告警风暴。
        String suppressKey = taskType + ":" + taskId + ":" + category;
        long nowEpoch = System.currentTimeMillis();
        Long lastAlertTime = alertSuppressionState.get(suppressKey);
        if (lastAlertTime != null && alertSuppressSeconds > 0 && (nowEpoch - lastAlertTime) < (alertSuppressSeconds * 1000)) {
            return false;
        }

        boolean anySent = false;
        for (String endpoint : alertWebhookUrls) {
            if (postAlert(endpoint, ownerUsername, taskType, taskId, jobId, message, category)) {
                anySent = true;
            }
        }
        if (anySent) {
            alertSuppressionState.put(suppressKey, nowEpoch);
        }
        return anySent;
    }

    private boolean postAlert(String endpoint, String ownerUsername, String taskType, Long taskId, String jobId, String message, String category) {
        try {
            URL url = URI.create(endpoint).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String payload = objectMapper.writeValueAsString(Map.of(
                "event", "process_job_failed",
                "owner", ownerUsername,
                "taskType", taskType,
                "taskId", taskId,
                "jobId", jobId,
                "category", category,
                "message", message,
                "timestamp", Instant.now().toString()
            ));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ex) {
            log.warn("send alert failed endpoint={} jobId={} error={}", endpoint, jobId, ex.getMessage());
            return false;
        }
    }

    private List<String> mergeAlertUrls(String single, String multi) {
        List<String> urls = new ArrayList<>();
        String one = single == null ? "" : single.trim();
        if (!one.isEmpty()) {
            urls.add(one);
        }
        String many = multi == null ? "" : multi.trim();
        if (!many.isEmpty()) {
            for (String part : many.split(",")) {
                String url = part.trim();
                if (!url.isEmpty() && !urls.contains(url)) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}


