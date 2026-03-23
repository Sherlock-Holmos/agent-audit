package com.audit.data.service.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * 治理与审计服务：维护血缘、质量报告、快照以及审计动作记录。
 */
public class GovernanceAuditService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Pattern SAFE_TABLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String stagingSchema;

    public GovernanceAuditService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
    }

    public List<Map<String, Object>> listLineageRecords(String ownerUsername, String tenantId, String taskType, Long taskId) {
        return jdbcTemplate.query(
            """
            SELECT id,task_type,task_id,source_table,source_field,target_table,target_field,created_at
              FROM etl_field_lineage
             WHERE owner_username=? AND (tenant_id=? OR tenant_id IS NULL)
                             AND (? IS NULL OR task_type=?)
                             AND (? IS NULL OR task_id=?)
             ORDER BY id DESC
            """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("taskType", rs.getString("task_type"));
                row.put("taskId", rs.getLong("task_id"));
                row.put("sourceTable", rs.getString("source_table"));
                row.put("sourceField", rs.getString("source_field"));
                row.put("targetTable", rs.getString("target_table"));
                row.put("targetField", rs.getString("target_field"));
                row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
                return row;
            },
            ownerUsername,
            tenantId,
            taskType,
            taskType,
            taskId,
            taskId
        );
    }

    public List<Map<String, Object>> listQualityReports(String ownerUsername, String tenantId, String taskType, Long taskId) {
        return jdbcTemplate.query(
            """
            SELECT id,task_type,task_id,table_name,total_rows,unknown_rows,duplicate_rows,quality_score,created_at
              FROM etl_quality_report
             WHERE owner_username=? AND (tenant_id=? OR tenant_id IS NULL)
                             AND (? IS NULL OR task_type=?)
                             AND (? IS NULL OR task_id=?)
             ORDER BY id DESC
            """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("taskType", rs.getString("task_type"));
                row.put("taskId", rs.getLong("task_id"));
                row.put("tableName", rs.getString("table_name"));
                row.put("totalRows", rs.getInt("total_rows"));
                row.put("unknownRows", rs.getInt("unknown_rows"));
                row.put("duplicateRows", rs.getInt("duplicate_rows"));
                row.put("qualityScore", rs.getInt("quality_score"));
                row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
                return row;
            },
            ownerUsername,
            tenantId,
            taskType,
            taskType,
            taskId,
            taskId
        );
    }

    public List<Map<String, Object>> listSnapshotRecords(String ownerUsername, String tenantId, String taskType, Long taskId) {
        return jdbcTemplate.query(
            """
            SELECT id,task_type,task_id,table_name,snapshot_version,row_count,schema_json,created_at
              FROM etl_table_snapshot
             WHERE owner_username=? AND (tenant_id=? OR tenant_id IS NULL)
                             AND (? IS NULL OR task_type=?)
                             AND (? IS NULL OR task_id=?)
             ORDER BY id DESC
            """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("taskType", rs.getString("task_type"));
                row.put("taskId", rs.getLong("task_id"));
                row.put("tableName", rs.getString("table_name"));
                row.put("snapshotVersion", rs.getInt("snapshot_version"));
                row.put("rowCount", rs.getInt("row_count"));
                row.put("schema", parseJsonMap(rs.getString("schema_json")));
                row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
                return row;
            },
            ownerUsername,
            tenantId,
            taskType,
            taskType,
            taskId,
            taskId
        );
    }

    public List<Map<String, Object>> listAuditRecords(String ownerUsername, String tenantId, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 100 : Math.min(limit, 500);
        return jdbcTemplate.query(
            """
            SELECT id,action_type,resource_type,resource_id,result_status,detail_json,created_at
              FROM audit_action_record
             WHERE actor_username=? AND (tenant_id=? OR tenant_id IS NULL)
             ORDER BY id DESC
             LIMIT ?
            """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("actionType", rs.getString("action_type"));
                row.put("resourceType", rs.getString("resource_type"));
                row.put("resourceId", rs.getString("resource_id"));
                row.put("resultStatus", rs.getString("result_status"));
                row.put("detail", parseJsonMap(rs.getString("detail_json")));
                row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
                return row;
            },
            ownerUsername,
            tenantId,
            safeLimit
        );
    }

    public void recordAudit(
        String tenantId,
        String ownerUsername,
        String actionType,
        String resourceType,
        String resourceId,
        String resultStatus,
        Map<String, Object> detail
    ) {
        jdbcTemplate.update(
            "INSERT INTO audit_action_record(tenant_id,actor_username,action_type,resource_type,resource_id,result_status,detail_json,created_at) VALUES(?,?,?,?,?,?,?,?)",
            tenantId,
            ownerUsername,
            actionType,
            resourceType,
            resourceId,
            resultStatus,
            toJson(detail),
            now()
        );
    }

    public void persistGovernanceArtifacts(
        String tenantId,
        String ownerUsername,
        String taskType,
        Long taskId,
        String targetTable,
        List<String> sourceTables
    ) {
        String targetTableRef = stagingTableRef(targetTable);
        Set<String> fields = inferFieldsFromTable(targetTableRef, 200);
        for (String source : sourceTables) {
            String sourceTable = sanitizeTableName(source);
            for (String field : fields) {
                jdbcTemplate.update(
                    "INSERT INTO etl_field_lineage(tenant_id,owner_username,task_type,task_id,source_table,source_field,target_table,target_field,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    tenantId,
                    ownerUsername,
                    taskType,
                    taskId,
                    sourceTable,
                    field,
                    targetTable,
                    field,
                    now()
                );
            }
        }

        Integer totalRowsObj = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + targetTableRef, Integer.class);
        int totalRows = totalRowsObj == null ? 0 : totalRowsObj;
        Integer unknownRowsObj = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM " + targetTableRef + " WHERE normalized_json LIKE '%\\\"UNKNOWN\\\"%'",
            Integer.class
        );
        int unknownRows = unknownRowsObj == null ? 0 : unknownRowsObj;
        Integer duplicateRowsObj = jdbcTemplate.queryForObject(
            "SELECT GREATEST(COUNT(1)-COUNT(DISTINCT normalized_json),0) FROM " + targetTableRef,
            Integer.class
        );
        int duplicateRows = duplicateRowsObj == null ? 0 : duplicateRowsObj;

        int qualityScore = 100;
        if (totalRows > 0) {
            qualityScore -= (unknownRows * 60 / totalRows);
            qualityScore -= (duplicateRows * 40 / totalRows);
        }
        qualityScore = Math.max(0, Math.min(100, qualityScore));

        jdbcTemplate.update(
            "INSERT INTO etl_quality_report(tenant_id,owner_username,task_type,task_id,table_name,total_rows,unknown_rows,duplicate_rows,quality_score,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            tenantId,
            ownerUsername,
            taskType,
            taskId,
            targetTable,
            totalRows,
            unknownRows,
            duplicateRows,
            qualityScore,
            now()
        );

        Integer latestVersion = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(snapshot_version),0) FROM etl_table_snapshot WHERE owner_username=? AND (tenant_id=? OR tenant_id IS NULL) AND task_type=? AND task_id=? AND table_name=?",
            Integer.class,
            ownerUsername,
            tenantId,
            taskType,
            taskId,
            targetTable
        );
        int nextVersion = (latestVersion == null ? 0 : latestVersion) + 1;
        List<String> columns = jdbcTemplate.query(
            "SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? ORDER BY ordinal_position",
            (rs, i) -> rs.getString("column_name"),
            stagingSchema,
            targetTable
        );
        jdbcTemplate.update(
            "INSERT INTO etl_table_snapshot(tenant_id,owner_username,task_type,task_id,table_name,snapshot_version,row_count,schema_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
            tenantId,
            ownerUsername,
            taskType,
            taskId,
            targetTable,
            nextVersion,
            totalRows,
            toJson(Map.of("columns", columns)),
            now()
        );
    }

    private Set<String> inferFieldsFromTable(String tableRef, int limit) {
        Set<String> fields = new LinkedHashSet<>();
        List<String> rows = jdbcTemplate.query(
            "SELECT normalized_json FROM " + tableRef + " ORDER BY id DESC LIMIT " + limit,
            (rs, i) -> nvl(rs.getString("normalized_json"))
        );
        for (String json : rows) {
            Map<String, Object> parsed = parseJsonMap(json);
            fields.addAll(parsed.keySet());
        }
        if (fields.isEmpty()) {
            fields.add("normalized_json");
        }
        return fields;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON搴忓垪鍖栧け璐");
        }
    }

    private static String now() {
        return DATE_TIME_FORMATTER.format(Instant.now());
    }

    private static String formatDateTime(Timestamp ts) {
        if (ts == null) return "";
        return DATE_TIME_FORMATTER.format(ts.toInstant());
    }

    private String stagingTableRef(String tableName) {
        return stagingSchema + "." + sanitizeTableName(tableName);
    }

    private String sanitizeSchemaName(String schemaName) {
        String normalized = text(schemaName);
        if (!Pattern.compile("^[a-zA-Z0-9_]+$").matcher(normalized).matches()) {
            throw new IllegalArgumentException("schema 鍚嶄笉鍚堟硶: " + schemaName);
        }
        return normalized;
    }

    private String sanitizeTableName(String tableName) {
        String normalized = text(tableName);
        if (!SAFE_TABLE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("琛ㄥ悕涓嶅悎娉? " + tableName);
        }
        return normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}

