package com.audit.data.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class DataProcessService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Pattern SAFE_TABLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Set<String> READY_STATUSES = Set.of("READY", "COMPLETED", "FAILED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataSourceService dataSourceService;

    public DataProcessService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, DataSourceService dataSourceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataSourceService = dataSourceService;
    }

    public List<Map<String, Object>> listCleanRules(String ownerUsername) {
        ensureDefaultCleanConfig(ownerUsername);
        return jdbcTemplate.query(
            "SELECT id,name,category,file_name,enabled,remark,updated_at FROM clean_rule_record WHERE owner_username=? ORDER BY updated_at DESC,id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("category", rs.getString("category"));
                row.put("fileName", nvl(rs.getString("file_name")));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername
        );
    }

    public Map<String, Object> uploadCleanRule(String ownerUsername, Map<String, Object> payload) {
        String name = text(payload.get("name"));
        String fileName = text(payload.get("fileName"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(fileName) || isBlank(content)) {
            throw new IllegalArgumentException("规则名称、文件和内容不能为空");
        }

        String now = now();
        Long id = insertAndGetId(
            "INSERT INTO clean_rule_record(owner_username,name,category,file_name,content,enabled,remark,created_at,updated_at) VALUES(?,?, 'USER',?,?,1,?,?,?)",
            ownerUsername, name, fileName, content, remark, now, now
        );

        Map<String, Object> row = new HashMap<>();
        row.put("id", String.valueOf(id));
        row.put("name", name);
        row.put("category", "USER");
        row.put("fileName", fileName);
        row.put("enabled", true);
        row.put("remark", remark);
        row.put("updatedAt", now);
        return row;
    }

    public Map<String, Object> toggleCleanRule(String ownerUsername, Long id, boolean enabled) {
        int updated = jdbcTemplate.update(
            "UPDATE clean_rule_record SET enabled=?, updated_at=? WHERE owner_username=? AND id=?",
            enabled ? 1 : 0, now(), ownerUsername, id
        );
        if (updated == 0) throw new IllegalArgumentException("规则不存在");
        return Map.of("id", String.valueOf(id), "enabled", enabled);
    }

    public void deleteCleanRule(String ownerUsername, Long id) {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_rule_record WHERE owner_username=? AND id=? AND category='SYSTEM'",
            Integer.class,
            ownerUsername,
            id
        );
        if (cnt != null && cnt > 0) throw new IllegalArgumentException("系统规则不允许删除");

        int deleted = jdbcTemplate.update("DELETE FROM clean_rule_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (deleted == 0) throw new IllegalArgumentException("规则不存在");
    }

    public List<Map<String, Object>> listCleanStrategies(String ownerUsername) {
        ensureDefaultCleanConfig(ownerUsername);
        return jdbcTemplate.query(
            "SELECT id,name,code,built_in,enabled,updated_at FROM clean_strategy_record WHERE owner_username=? ORDER BY updated_at DESC,id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("code", rs.getString("code"));
                row.put("builtIn", rs.getBoolean("built_in"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername
        );
    }

    public Map<String, Object> createCleanStrategy(String ownerUsername, Map<String, Object> payload) {
        String name = text(payload.get("name"));
        String code = text(payload.get("code"));
        if (isBlank(name) || isBlank(code)) throw new IllegalArgumentException("策略名称和编码不能为空");

        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND code=?",
            Integer.class,
            ownerUsername,
            code
        );
        if (exists != null && exists > 0) throw new IllegalArgumentException("策略编码已存在");

        String now = now();
        Long id = insertAndGetId(
            "INSERT INTO clean_strategy_record(owner_username,name,code,built_in,enabled,created_at,updated_at) VALUES(?,?,?,0,1,?,?)",
            ownerUsername,
            name,
            code,
            now,
            now
        );

        return Map.of(
            "id", String.valueOf(id),
            "name", name,
            "code", code,
            "builtIn", false,
            "enabled", true,
            "updatedAt", now
        );
    }

    public Map<String, Object> toggleCleanStrategy(String ownerUsername, Long id, boolean enabled) {
        int updated = jdbcTemplate.update(
            "UPDATE clean_strategy_record SET enabled=?, updated_at=? WHERE owner_username=? AND id=?",
            enabled ? 1 : 0, now(), ownerUsername, id
        );
        if (updated == 0) throw new IllegalArgumentException("策略不存在");
        return Map.of("id", String.valueOf(id), "enabled", enabled);
    }

    public void deleteCleanStrategy(String ownerUsername, Long id) {
        Integer builtIn = jdbcTemplate.queryForObject(
            "SELECT built_in FROM clean_strategy_record WHERE owner_username=? AND id=?",
            Integer.class,
            ownerUsername,
            id
        );
        if (builtIn == null) throw new IllegalArgumentException("策略不存在");
        if (builtIn == 1) throw new IllegalArgumentException("系统策略不允许删除");

        jdbcTemplate.update("DELETE FROM clean_strategy_record WHERE owner_username=? AND id=?", ownerUsername, id);
    }

    public List<Map<String, Object>> listCleanTasks(String ownerUsername, String keyword, String sourceId, String status) {
        String sql = "SELECT * FROM clean_task_record WHERE owner_username=? ORDER BY id DESC";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, i) -> cleanTaskRow(rs), ownerUsername);
        return rows.stream()
            .filter(item -> isBlank(keyword) || contains(item.get("taskName"), keyword) || listContains((List<?>) item.get("cleanObjectNames"), keyword))
            .filter(item -> isBlank(sourceId) || objectHasSource((List<Map<String, Object>>) item.get("cleanObjects"), sourceId))
            .filter(item -> isBlank(status) || status.equalsIgnoreCase(String.valueOf(item.get("status"))))
            .toList();
    }

    public Map<String, Object> createCleanTask(String ownerUsername, Map<String, Object> payload) {
        String taskName = text(payload.get("taskName"));
        String strategyCode = text(payload.get("strategy"));
        String standardTable = text(payload.get("standardTable"));
        String remark = text(payload.get("remark"));
        List<Map<String, Object>> cleanObjects = castMapList(payload.get("cleanObjects"));
        List<String> cleanRuleNames = castStringList(payload.get("cleanRuleNames"));

        if (isBlank(taskName) || isBlank(strategyCode) || cleanObjects.isEmpty()) {
            throw new IllegalArgumentException("清洗任务必填项缺失");
        }

        ensureDefaultCleanConfig(ownerUsername);
        Map<String, Object> strategy = getEnabledStrategy(ownerUsername, strategyCode);
        if (strategy.isEmpty()) throw new IllegalArgumentException("清洗策略不存在或已停用");

        for (Map<String, Object> object : cleanObjects) {
            Long sourceIdVal = toLong(object.get("sourceId"));
            String objectName = text(object.get("objectName"));
            if (sourceIdVal == null || isBlank(objectName)) throw new IllegalArgumentException("清洗对象信息不完整");
            List<Map<String, Object>> objects = dataSourceService.listSourceObjects(ownerUsername, sourceIdVal);
            boolean valid = objects.stream().anyMatch(it -> objectName.equals(String.valueOf(it.get("objectName"))));
            if (!valid) throw new IllegalArgumentException("存在无效清洗对象，请重新选择");
        }

        List<String> objectNames = cleanObjects.stream()
            .map(obj -> text(obj.get("sourceName")) + " / " + text(obj.get("objectName")))
            .toList();

        String now = now();
        String outputTable = isBlank(standardTable) ? "clean_std_" + System.currentTimeMillis() : standardTable;

        Long id = insertAndGetId(
            """
            INSERT INTO clean_task_record(
              owner_username,task_name,clean_objects_json,clean_object_names_json,clean_rule_names_json,
              strategy_code,strategy_name,standard_table,status,cleaned_rows,remark,created_at,updated_at
            ) VALUES(?,?,?,?,?,?,?,?, 'READY',0,?,?,?)
            """,
            ownerUsername,
            taskName,
            toJson(cleanObjects),
            toJson(objectNames),
            toJson(cleanRuleNames),
            strategyCode,
            text(strategy.get("name")),
            outputTable,
            remark,
            now,
            now
        );

        return getCleanTaskById(ownerUsername, id);
    }

    public Map<String, Object> runCleanTask(String ownerUsername, Long id) {
        Map<String, Object> task = getCleanTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("当前任务状态不允许执行");
        }

        String outputTable = sanitizeTableName(String.valueOf(task.get("standardTable")));
        List<Map<String, Object>> cleanObjects = castMapList(task.get("cleanObjects"));
        String strategyCode = text(task.get("strategy"));
        List<String> ruleNames = castStringList(task.get("cleanRuleNames"));

        jdbcTemplate.update(
            "UPDATE clean_task_record SET status='RUNNING', updated_at=? WHERE owner_username=? AND id=?",
            now(), ownerUsername, id
        );

        int cleanedRows;
        try {
            recreateStandardTable(outputTable);
            loadObjectsIntoStandardTable(ownerUsername, id, cleanObjects, outputTable);
            applyCleanStrategy(outputTable, strategyCode, ruleNames);

            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + outputTable, Integer.class);
            cleanedRows = count == null ? 0 : count;

            jdbcTemplate.update(
                "UPDATE clean_task_record SET status='COMPLETED', cleaned_rows=?, updated_at=? WHERE owner_username=? AND id=?",
                cleanedRows,
                now(),
                ownerUsername,
                id
            );
        } catch (RuntimeException ex) {
            jdbcTemplate.update(
                "UPDATE clean_task_record SET status='FAILED', updated_at=? WHERE owner_username=? AND id=?",
                now(),
                ownerUsername,
                id
            );
            throw ex;
        }

        return getCleanTaskById(ownerUsername, id);
    }

    public void deleteCleanTask(String ownerUsername, Long id) {
        int affected = jdbcTemplate.update("DELETE FROM clean_task_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (affected == 0) throw new IllegalArgumentException("清洗任务不存在");
    }

    public List<Map<String, Object>> listFusionTasks(String ownerUsername, String keyword, String status) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT * FROM fusion_task_record WHERE owner_username=? ORDER BY id DESC",
            (rs, i) -> fusionTaskRow(rs),
            ownerUsername
        );
        return rows.stream()
            .filter(item -> isBlank(keyword) || contains(item.get("taskName"), keyword) || contains(item.get("targetTable"), keyword))
            .filter(item -> isBlank(status) || status.equalsIgnoreCase(String.valueOf(item.get("status"))))
            .toList();
    }

    public Map<String, Object> createFusionTask(String ownerUsername, Map<String, Object> payload) {
        String taskName = text(payload.get("taskName"));
        String targetTable = text(payload.get("targetTable"));
        String strategy = text(payload.get("strategy"));
        String remark = text(payload.get("remark"));
        List<Long> cleanTaskIds = castLongList(payload.get("cleanTaskIds"));

        if (isBlank(taskName) || isBlank(targetTable) || isBlank(strategy) || cleanTaskIds.isEmpty()) {
            throw new IllegalArgumentException("融合任务必填项缺失");
        }

        List<String> cleanTaskNames = new ArrayList<>();
        List<String> standardTables = new ArrayList<>();
        for (Long cleanTaskId : cleanTaskIds) {
            Map<String, Object> cleanTask = getCleanTaskById(ownerUsername, cleanTaskId);
            if (!"COMPLETED".equalsIgnoreCase(String.valueOf(cleanTask.get("status")))) {
                throw new IllegalArgumentException("仅可选择已完成的清洗任务");
            }
            cleanTaskNames.add(String.valueOf(cleanTask.get("taskName")));
            standardTables.add(String.valueOf(cleanTask.get("standardTable")));
        }

        String now = now();
        Long id = insertAndGetId(
            """
            INSERT INTO fusion_task_record(
              owner_username,task_name,target_table,clean_task_ids_json,clean_task_names_json,standard_tables_json,
              strategy,status,fusion_rows,remark,created_at,updated_at
            ) VALUES(?,?,?,?,?,?,?, 'READY',0,?,?,?)
            """,
            ownerUsername,
            taskName,
            targetTable,
            toJson(cleanTaskIds),
            toJson(cleanTaskNames),
            toJson(standardTables),
            strategy,
            remark,
            now,
            now
        );

        return getFusionTaskById(ownerUsername, id);
    }

    public Map<String, Object> runFusionTask(String ownerUsername, Long id) {
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("当前任务状态不允许执行");
        }

        String targetTable = sanitizeTableName(String.valueOf(task.get("targetTable")));
        List<String> standardTables = castStringList(task.get("standardTables"));
        if (standardTables.isEmpty()) {
            throw new IllegalArgumentException("缺少可融合的标准表");
        }

        jdbcTemplate.update(
            "UPDATE fusion_task_record SET status='RUNNING', updated_at=? WHERE owner_username=? AND id=?",
            now(), ownerUsername, id
        );

        int fusionRows;
        try {
            recreateFusionTable(targetTable);
            fusionRows = mergeStandardTablesToTarget(ownerUsername, id, targetTable, standardTables);

            jdbcTemplate.update(
                "UPDATE fusion_task_record SET status='COMPLETED', fusion_rows=?, updated_at=? WHERE owner_username=? AND id=?",
                fusionRows,
                now(),
                ownerUsername,
                id
            );
        } catch (RuntimeException ex) {
            jdbcTemplate.update(
                "UPDATE fusion_task_record SET status='FAILED', updated_at=? WHERE owner_username=? AND id=?",
                now(), ownerUsername, id
            );
            throw ex;
        }

        return getFusionTaskById(ownerUsername, id);
    }

    public void deleteFusionTask(String ownerUsername, Long id) {
        int affected = jdbcTemplate.update("DELETE FROM fusion_task_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (affected == 0) throw new IllegalArgumentException("融合任务不存在");
    }

    private void ensureDefaultCleanConfig(String ownerUsername) {
        ensureSystemRule(ownerUsername, "空值填充规则", "fill_null_with_default");
        ensureSystemRule(ownerUsername, "字段标准化规则", "normalize_fields");
        cleanupDuplicateSystemRules(ownerUsername);

        ensureSystemStrategy(ownerUsername, "去重+空值补齐", "DEDUP_AND_FILL");
        ensureSystemStrategy(ownerUsername, "字段标准化", "STANDARDIZE");
        ensureSystemStrategy(ownerUsername, "异常值剔除", "OUTLIER_REMOVE");
        cleanupDuplicateSystemStrategies(ownerUsername);
    }

    private void ensureSystemRule(String ownerUsername, String name, String content) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_rule_record WHERE owner_username=? AND category='SYSTEM' AND name=? AND content=?",
            Integer.class,
            ownerUsername,
            name,
            content
        );
        if (count != null && count > 0) {
            return;
        }

        String now = now();
        jdbcTemplate.update(
            "INSERT INTO clean_rule_record(owner_username,name,category,file_name,content,enabled,remark,created_at,updated_at) VALUES(?, ?, 'SYSTEM', '-', ?, 1, '系统默认规则', ?, ?)",
            ownerUsername,
            name,
            content,
            now,
            now
        );
    }

    private void cleanupDuplicateSystemRules(String ownerUsername) {
        List<Long> duplicateIds = jdbcTemplate.query(
            """
            SELECT r.id
              FROM clean_rule_record r
              JOIN (
                    SELECT owner_username, name, content, MIN(id) AS keep_id, COUNT(1) AS cnt
                      FROM clean_rule_record
                     WHERE owner_username=? AND category='SYSTEM'
                     GROUP BY owner_username, name, content
                    HAVING COUNT(1) > 1
                   ) dup
                ON dup.owner_username = r.owner_username
               AND dup.name = r.name
               AND dup.content = r.content
             WHERE r.id <> dup.keep_id
            """,
            (rs, i) -> rs.getLong("id"),
            ownerUsername
        );
        if (!duplicateIds.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "DELETE FROM clean_rule_record WHERE id=? AND owner_username=?",
                duplicateIds,
                duplicateIds.size(),
                (ps, id) -> {
                    ps.setLong(1, id);
                    ps.setString(2, ownerUsername);
                }
            );
        }
    }

    private void ensureSystemStrategy(String ownerUsername, String name, String code) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND built_in=1 AND code=?",
            Integer.class,
            ownerUsername,
            code
        );
        if (count != null && count > 0) {
            return;
        }

        String now = now();
        jdbcTemplate.update(
            "INSERT INTO clean_strategy_record(owner_username,name,code,built_in,enabled,created_at,updated_at) VALUES(?,?,?,1,1,?,?)",
            ownerUsername,
            name,
            code,
            now,
            now
        );
    }

    private void cleanupDuplicateSystemStrategies(String ownerUsername) {
        List<Long> duplicateIds = jdbcTemplate.query(
            """
            SELECT s.id
              FROM clean_strategy_record s
              JOIN (
                    SELECT owner_username, code, MIN(id) AS keep_id, COUNT(1) AS cnt
                      FROM clean_strategy_record
                     WHERE owner_username=? AND built_in=1
                     GROUP BY owner_username, code
                    HAVING COUNT(1) > 1
                   ) dup
                ON dup.owner_username = s.owner_username
               AND dup.code = s.code
             WHERE s.id <> dup.keep_id
            """,
            (rs, i) -> rs.getLong("id"),
            ownerUsername
        );
        if (!duplicateIds.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "DELETE FROM clean_strategy_record WHERE id=? AND owner_username=?",
                duplicateIds,
                duplicateIds.size(),
                (ps, id) -> {
                    ps.setLong(1, id);
                    ps.setString(2, ownerUsername);
                }
            );
        }
    }

    private Map<String, Object> getEnabledStrategy(String ownerUsername, String code) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,name,code FROM clean_strategy_record WHERE owner_username=? AND code=? AND enabled=1",
            (rs, i) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name"), "code", rs.getString("code")),
            ownerUsername,
            code
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> getCleanTaskById(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT * FROM clean_task_record WHERE owner_username=? AND id=?",
            (rs, i) -> cleanTaskRow(rs),
            ownerUsername,
            id
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("清洗任务不存在");
        return rows.get(0);
    }

    private Map<String, Object> getFusionTaskById(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT * FROM fusion_task_record WHERE owner_username=? AND id=?",
            (rs, i) -> fusionTaskRow(rs),
            ownerUsername,
            id
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("融合任务不存在");
        return rows.get(0);
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
        row.put("status", rs.getString("status"));
        row.put("fusionRows", rs.getInt("fusion_rows"));
        row.put("remark", nvl(rs.getString("remark")));
        row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
        row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
        return row;
    }

    private Long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("新增失败");
        return key.longValue();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON序列化失败");
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private List<Long> castLongList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(DataProcessService::toLong).filter(Objects::nonNull).toList();
    }

    private static boolean contains(Object value, String keyword) {
        return String.valueOf(value).toLowerCase().contains(keyword.toLowerCase());
    }

    private static boolean listContains(List<?> values, String keyword) {
        return values.stream().map(String::valueOf).anyMatch(v -> v.toLowerCase().contains(keyword.toLowerCase()));
    }

    private static boolean objectHasSource(List<Map<String, Object>> objects, String sourceId) {
        return objects.stream().anyMatch(it -> String.valueOf(it.get("sourceId")).equals(sourceId));
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

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String now() {
        return DATE_TIME_FORMATTER.format(Instant.now());
    }

    private static String formatDateTime(Timestamp ts) {
        if (ts == null) return "";
        return DATE_TIME_FORMATTER.format(ts.toInstant());
    }

    private void recreateStandardTable(String tableName) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
        jdbcTemplate.execute(
            """
            CREATE TABLE %s (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              task_id BIGINT NOT NULL,
              source_id BIGINT NOT NULL,
              object_name VARCHAR(255) NOT NULL,
              row_no INT NOT NULL,
              raw_json LONGTEXT NOT NULL,
              normalized_json LONGTEXT NOT NULL,
              created_at DATETIME NOT NULL
            )
            """.formatted(tableName)
        );
    }

    private void recreateFusionTable(String tableName) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
        jdbcTemplate.execute(
            """
            CREATE TABLE %s (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              fusion_task_id BIGINT NOT NULL,
              clean_task_id BIGINT NOT NULL,
              source_id BIGINT NOT NULL,
              object_name VARCHAR(255) NOT NULL,
              row_no INT NOT NULL,
              raw_json LONGTEXT NOT NULL,
              normalized_json LONGTEXT NOT NULL,
              source_standard_table VARCHAR(255) NOT NULL,
              created_at DATETIME NOT NULL
            )
            """.formatted(tableName)
        );
    }

    private void loadObjectsIntoStandardTable(String ownerUsername, Long taskId, List<Map<String, Object>> cleanObjects, String outputTable) {
        for (Map<String, Object> object : cleanObjects) {
            Long sourceId = toLong(object.get("sourceId"));
            String objectName = text(object.get("objectName"));
            if (sourceId == null || isBlank(objectName)) {
                throw new IllegalArgumentException("清洗对象信息不完整");
            }
            Map<String, Object> source = getSourceById(ownerUsername, sourceId);
            String sourceType = text(source.get("type")).toUpperCase();

            List<String> rows = switch (sourceType) {
                case "DATABASE" -> readDatabaseRows(objectName);
                case "FILE" -> readFileRows(text(source.get("filePath")), text(source.get("fileName")));
                default -> throw new IllegalArgumentException("不支持的数据源类型: " + sourceType);
            };

            int rowNo = 1;
            for (String row : rows) {
                jdbcTemplate.update(
                    "INSERT INTO " + outputTable + "(task_id,source_id,object_name,row_no,raw_json,normalized_json,created_at) VALUES(?,?,?,?,?,?,?)",
                    taskId,
                    sourceId,
                    objectName,
                    rowNo++,
                    row,
                    row,
                    now()
                );
            }
        }
    }

    private List<String> readDatabaseRows(String objectName) {
        String tableName = sanitizeTableName(objectName);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("SELECT * FROM " + tableName + " LIMIT 10000");
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> record : records) {
            rows.add(toJson(record));
        }
        return rows;
    }

    private List<String> readFileRows(String filePath, String fileName) {
        if (isBlank(filePath)) {
            throw new IllegalArgumentException("文件数据源缺少文件路径");
        }
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        String lower = nvl(fileName).toLowerCase();
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            return readTextStructuredRows(path, lower.endsWith(".txt") ? '|' : ',');
        }
        if (lower.endsWith(".json")) {
            return readJsonRows(path);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            throw new IllegalArgumentException("当前版本暂不支持解析 Excel 清洗，请先转为 csv/txt/json");
        }
        throw new IllegalArgumentException("不支持的文件类型");
    }

    private List<String> readTextStructuredRows(Path path, char delimiter) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return List.of();
            String[] headers = splitLine(headerLine, delimiter);
            List<String> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null && rows.size() < 10000) {
                String[] values = splitLine(line, delimiter);
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String key = headers[i].trim();
                    if (key.isEmpty()) key = "col_" + (i + 1);
                    row.put(key, i < values.length ? values[i].trim() : "");
                }
                rows.add(toJson(row));
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取文件失败: " + ex.getMessage());
        }
    }

    private List<String> readJsonRows(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.startsWith("[")) {
                List<Map<String, Object>> list = objectMapper.readValue(content, new TypeReference<>() {});
                List<String> rows = new ArrayList<>();
                for (Map<String, Object> item : list) {
                    rows.add(toJson(item));
                }
                return rows;
            }

            List<String> rows = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null && rows.size() < 10000) {
                    String trim = line.trim();
                    if (!trim.isEmpty()) rows.add(trim);
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException("解析 JSON 文件失败: " + ex.getMessage());
        }
    }

    private void applyCleanStrategy(String outputTable, String strategyCode, List<String> ruleNames) {
        String normalized = strategyCode.toUpperCase();

        if (normalized.contains("DEDUP")) {
            jdbcTemplate.update(
                "DELETE t1 FROM " + outputTable + " t1 JOIN " + outputTable + " t2 ON t1.id > t2.id AND t1.normalized_json = t2.normalized_json"
            );
        }

        if (normalized.contains("STANDARD")) {
            jdbcTemplate.update("UPDATE " + outputTable + " SET normalized_json=LOWER(normalized_json)");
        }

        if (normalized.contains("OUTLIER")) {
            jdbcTemplate.update("DELETE FROM " + outputTable + " WHERE CHAR_LENGTH(normalized_json) > 8000");
        }

        if (ruleNames.stream().anyMatch(name -> name.contains("空值"))) {
            jdbcTemplate.update(
                "UPDATE " + outputTable + " SET normalized_json=REPLACE(normalized_json, ':\"\"', ':\"UNKNOWN\"')"
            );
        }
    }

    private int mergeStandardTablesToTarget(String ownerUsername, Long fusionTaskId, String targetTable, List<String> standardTables) {
        int total = 0;
        for (String table : standardTables) {
            String safeStandardTable = sanitizeTableName(table);
            Map<String, Object> cleanTask = findCleanTaskByStandardTable(ownerUsername, safeStandardTable);
            Long cleanTaskId = toLong(cleanTask.get("id"));
            if (cleanTaskId == null) {
                throw new IllegalArgumentException("清洗任务缺失: " + safeStandardTable);
            }

            int inserted = jdbcTemplate.update(
                """
                INSERT INTO %s(fusion_task_id,clean_task_id,source_id,object_name,row_no,raw_json,normalized_json,source_standard_table,created_at)
                SELECT ?, ?, source_id, object_name, row_no, raw_json, normalized_json, ?, ?
                  FROM %s
                """.formatted(targetTable, safeStandardTable),
                fusionTaskId,
                cleanTaskId,
                safeStandardTable,
                now()
            );
            total += inserted;
        }
        return total;
    }

    private Map<String, Object> findCleanTaskByStandardTable(String ownerUsername, String standardTable) {
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

    private Map<String, Object> getSourceById(String ownerUsername, Long sourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,type,file_name,file_path FROM data_source_record WHERE owner_username=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("type", rs.getString("type"));
                row.put("fileName", nvl(rs.getString("file_name")));
                row.put("filePath", nvl(rs.getString("file_path")));
                return row;
            },
            ownerUsername,
            sourceId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        return rows.get(0);
    }

    private String sanitizeTableName(String tableName) {
        String normalized = text(tableName);
        if (!SAFE_TABLE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("表名不合法: " + tableName);
        }
        return normalized;
    }

    private String[] splitLine(String line, char delimiter) {
        return line.split(Pattern.quote(String.valueOf(delimiter)), -1);
    }
}
