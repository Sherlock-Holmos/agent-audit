package com.audit.data.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
/**
 * 数据处理任务仓储：集中管理清洗/融合任务记录的读写与状态更新。
 */
public class DataProcessTaskRepository {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataProcessTaskRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listCleanTasks(String ownerUsername) {
        return jdbcTemplate.query(
            "SELECT * FROM clean_task_record WHERE owner_username=? ORDER BY id DESC",
            (rs, i) -> cleanTaskRow(rs),
            ownerUsername
        );
    }

    public List<Map<String, Object>> listRunningCleanTasks(String ownerUsername, int limit) {
        return jdbcTemplate.query(
            "SELECT * FROM clean_task_record WHERE owner_username=? AND status='RUNNING' ORDER BY updated_at ASC, id ASC LIMIT ?",
            (rs, i) -> cleanTaskRow(rs),
            ownerUsername,
            limit
        );
    }

    public Map<String, Object> getCleanTaskById(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT * FROM clean_task_record WHERE owner_username=? AND id=?",
            (rs, i) -> cleanTaskRow(rs),
            ownerUsername,
            id
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("清洗任务不存在");
        return rows.get(0);
    }

    public Long insertCleanTask(
        String ownerUsername,
        String taskName,
        String cleanObjectsJson,
        String cleanObjectNamesJson,
        String cleanRuleNamesJson,
        String strategyCode,
        String strategyName,
        String outputTable,
        String remark
    ) {
        String now = now();
        return insertAndGetId(
            """
            INSERT INTO clean_task_record(
              owner_username,task_name,clean_objects_json,clean_object_names_json,clean_rule_names_json,
              strategy_code,strategy_name,standard_table,status,cleaned_rows,remark,created_at,updated_at
                        ) VALUES(?,?,?,?,?,?,?,?, 'READY',0,?,?,?)
            """,
            ownerUsername,
            taskName,
            cleanObjectsJson,
            cleanObjectNamesJson,
            cleanRuleNamesJson,
            strategyCode,
            strategyName,
            outputTable,
            remark,
            now,
            now
        );
    }

    public void markCleanTaskRunning(String ownerUsername, Long id) {
        jdbcTemplate.update(
            "UPDATE clean_task_record SET status='RUNNING', updated_at=? WHERE owner_username=? AND id=?",
            now(), ownerUsername, id
        );
    }

    public void markCleanTaskCompleted(String ownerUsername, Long id, int cleanedRows) {
        jdbcTemplate.update(
            "UPDATE clean_task_record SET status='COMPLETED', cleaned_rows=?, updated_at=? WHERE owner_username=? AND id=?",
            cleanedRows,
            now(),
            ownerUsername,
            id
        );
    }

    public void markCleanTaskFailed(String ownerUsername, Long id) {
        jdbcTemplate.update(
            "UPDATE clean_task_record SET status='FAILED', updated_at=? WHERE owner_username=? AND id=?",
            now(),
            ownerUsername,
            id
        );
    }

    public int updateCleanTask(
        String ownerUsername,
        Long id,
        String taskName,
        String cleanObjectsJson,
        String cleanObjectNamesJson,
        String cleanRuleNamesJson,
        String strategyCode,
        String strategyName,
        String outputTable,
        String remark
    ) {
        return jdbcTemplate.update(
            """
            UPDATE clean_task_record
               SET task_name=?,
                   clean_objects_json=?,
                   clean_object_names_json=?,
                   clean_rule_names_json=?,
                   strategy_code=?,
                   strategy_name=?,
                   standard_table=?,
                   remark=?,
                   status='READY',
                   cleaned_rows=0,
                   updated_at=?
             WHERE owner_username=? AND id=?
            """,
            taskName,
            cleanObjectsJson,
            cleanObjectNamesJson,
            cleanRuleNamesJson,
            strategyCode,
            strategyName,
            outputTable,
            remark,
            now(),
            ownerUsername,
            id
        );
    }

    public int deleteCleanTask(String ownerUsername, Long id) {
        return jdbcTemplate.update("DELETE FROM clean_task_record WHERE owner_username=? AND id=?", ownerUsername, id);
    }

    public List<Map<String, Object>> listFusionTasks(String ownerUsername) {
        return jdbcTemplate.query(
            "SELECT * FROM fusion_task_record WHERE owner_username=? ORDER BY id DESC",
            (rs, i) -> fusionTaskRow(rs),
            ownerUsername
        );
    }

    public List<Map<String, Object>> listRunningFusionTasks(String ownerUsername, int limit) {
        return jdbcTemplate.query(
            "SELECT * FROM fusion_task_record WHERE owner_username=? AND status='RUNNING' ORDER BY updated_at ASC, id ASC LIMIT ?",
            (rs, i) -> fusionTaskRow(rs),
            ownerUsername,
            limit
        );
    }

    public List<String> listOwnersWithRunningCleanTasks(int limit) {
        return jdbcTemplate.query(
            "SELECT DISTINCT owner_username FROM clean_task_record WHERE status='RUNNING' AND owner_username IS NOT NULL AND owner_username<>'' ORDER BY owner_username ASC LIMIT ?",
            (rs, i) -> text(rs.getString("owner_username")),
            limit
        );
    }

    public List<String> listOwnersWithRunningFusionTasks(int limit) {
        return jdbcTemplate.query(
            "SELECT DISTINCT owner_username FROM fusion_task_record WHERE status='RUNNING' AND owner_username IS NOT NULL AND owner_username<>'' ORDER BY owner_username ASC LIMIT ?",
            (rs, i) -> text(rs.getString("owner_username")),
            limit
        );
    }

    public Map<String, Object> getFusionTaskById(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT * FROM fusion_task_record WHERE owner_username=? AND id=?",
            (rs, i) -> fusionTaskRow(rs),
            ownerUsername,
            id
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("融合任务不存在");
        return rows.get(0);
    }

    public Long insertFusionTask(
        String ownerUsername,
        String taskName,
        String targetTable,
        String cleanTaskIdsJson,
        String cleanTaskNamesJson,
        String standardTablesJson,
        String strategy,
        String fusionConfigJson,
        String remark
    ) {
        String now = now();
        return insertAndGetId(
            """
            INSERT INTO fusion_task_record(
              owner_username,task_name,target_table,clean_task_ids_json,clean_task_names_json,standard_tables_json,
              strategy,fusion_config_json,status,fusion_rows,remark,created_at,updated_at
                        ) VALUES(?,?,?,?,?,?,?, ?, 'READY',0,?,?,?)
            """,
            ownerUsername,
            taskName,
            targetTable,
            cleanTaskIdsJson,
            cleanTaskNamesJson,
            standardTablesJson,
            strategy,
            fusionConfigJson,
            remark,
            now,
            now
        );
    }

    public void markFusionTaskRunning(String ownerUsername, Long id) {
        jdbcTemplate.update(
            "UPDATE fusion_task_record SET status='RUNNING', updated_at=? WHERE owner_username=? AND id=?",
            now(), ownerUsername, id
        );
    }

    public void markFusionTaskCompleted(String ownerUsername, Long id, int fusionRows) {
        jdbcTemplate.update(
            "UPDATE fusion_task_record SET status='COMPLETED', fusion_rows=?, updated_at=? WHERE owner_username=? AND id=?",
            fusionRows,
            now(),
            ownerUsername,
            id
        );
    }

    public void markFusionTaskFailed(String ownerUsername, Long id) {
        jdbcTemplate.update(
            "UPDATE fusion_task_record SET status='FAILED', updated_at=? WHERE owner_username=? AND id=?",
            now(), ownerUsername, id
        );
    }

    public int updateFusionTask(
        String ownerUsername,
        Long id,
        String taskName,
        String targetTable,
        String cleanTaskIdsJson,
        String cleanTaskNamesJson,
        String standardTablesJson,
        String strategy,
        String fusionConfigJson,
        String remark
    ) {
        return jdbcTemplate.update(
            """
            UPDATE fusion_task_record
               SET task_name=?,
                   target_table=?,
                   clean_task_ids_json=?,
                   clean_task_names_json=?,
                   standard_tables_json=?,
                   strategy=?,
                 fusion_config_json=?,
                   remark=?,
                   status='READY',
                   fusion_rows=0,
                   updated_at=?
             WHERE owner_username=? AND id=?
            """,
            taskName,
            targetTable,
            cleanTaskIdsJson,
            cleanTaskNamesJson,
            standardTablesJson,
            strategy,
            fusionConfigJson,
            remark,
            now(),
            ownerUsername,
            id
        );
    }

    public int deleteFusionTask(String ownerUsername, Long id) {
        return jdbcTemplate.update("DELETE FROM fusion_task_record WHERE owner_username=? AND id=?", ownerUsername, id);
    }

    public Map<String, Object> findCleanTaskByStandardTable(String ownerUsername, String standardTable) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,task_name,status FROM clean_task_record WHERE owner_username=? AND standard_table=? ORDER BY id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("taskName", rs.getString("task_name"));
                row.put("status", rs.getString("status"));
                return row;
            },
            ownerUsername,
            standardTable
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("未找到对应清洗结果表: " + standardTable);
        }
        if (!"COMPLETED".equalsIgnoreCase(String.valueOf(rows.get(0).get("status")))) {
            throw new IllegalArgumentException("清洗任务未完成: " + rows.get(0).get("taskName"));
        }
        return rows.get(0);
    }

    public List<String> listAllStandardTables() {
        return jdbcTemplate.query("SELECT standard_table FROM clean_task_record", (rs, i) -> text(rs.getString("standard_table")));
    }

    public List<String> listAllFusionTargetTables() {
        return jdbcTemplate.query("SELECT target_table FROM fusion_task_record", (rs, i) -> text(rs.getString("target_table")));
    }

    public int countCleanTaskByStandardTable(String standardTable) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_task_record WHERE standard_table=?",
            Integer.class,
            standardTable
        );
        return count == null ? 0 : count;
    }

    public int countFusionRefByStandardTable(String standardTable) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM fusion_task_record WHERE standard_tables_json LIKE ?",
            Integer.class,
            "%\"" + standardTable + "\"%"
        );
        return count == null ? 0 : count;
    }

    public int countFusionTaskByTargetTable(String targetTable) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM fusion_task_record WHERE target_table=?",
            Integer.class,
            targetTable
        );
        return count == null ? 0 : count;
    }

    private Map<String, Object> cleanTaskRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("taskName", rs.getString("task_name"));
        row.put("cleanObjects", fromJsonToMapList(rs.getString("clean_objects_json")));
        row.put("cleanObjectNames", fromJsonToStringList(rs.getString("clean_object_names_json")));
        row.put("cleanRuleNames", fromJsonToStringList(rs.getString("clean_rule_names_json")));
        row.put("strategy", rs.getString("strategy_code"));
        row.put("strategyName", rs.getString("strategy_name"));
        row.put("standardTable", rs.getString("standard_table"));
        row.put("status", rs.getString("status"));
        row.put("cleanedRows", rs.getInt("cleaned_rows"));
        row.put("remark", nvl(rs.getString("remark")));
        row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
        row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
        return row;
    }

    private Map<String, Object> fusionTaskRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("taskName", rs.getString("task_name"));
        row.put("targetTable", rs.getString("target_table"));
        row.put("cleanTaskIds", fromJsonToLongList(rs.getString("clean_task_ids_json")));
        row.put("cleanTaskNames", fromJsonToStringList(rs.getString("clean_task_names_json")));
        row.put("standardTables", fromJsonToStringList(rs.getString("standard_tables_json")));
        row.put("strategy", rs.getString("strategy"));
        row.put("fusionConfig", fromJsonToMap(rs.getString("fusion_config_json")));
        row.put("status", rs.getString("status"));
        row.put("fusionRows", rs.getInt("fusion_rows"));
        row.put("remark", nvl(rs.getString("remark")));
        row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
        row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
        return row;
    }

    private Map<String, Object> fromJsonToMap(String json) {
        if (isBlank(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> fromJsonToMapList(String json) {
        if (isBlank(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> fromJsonToStringList(String json) {
        if (isBlank(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> fromJsonToLongList(String json) {
        if (isBlank(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    @SuppressWarnings("null")
    private Long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("新增失败");
        return key.longValue();
    }

    private static String now() {
        return DATE_TIME_FORMATTER.format(Instant.now());
    }

    private static String formatDateTime(Timestamp ts) {
        if (ts == null) return "";
        return DATE_TIME_FORMATTER.format(ts.toInstant());
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



