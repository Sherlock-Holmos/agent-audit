package com.audit.data.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class DataProcessService implements IDataProcessService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Pattern SAFE_TABLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern SAFE_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Set<String> READY_STATUSES = Set.of("READY", "COMPLETED", "FAILED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataSourceService dataSourceService;
    private final DashboardService dashboardService;
    private final MeterRegistry meterRegistry;
    private final String stagingSchema;
    private final Counter cleanRunSuccessCounter;
    private final Counter cleanRunFailedCounter;
    private final Counter fusionRunSuccessCounter;
    private final Counter fusionRunFailedCounter;

    public DataProcessService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        DataSourceService dataSourceService,
        DashboardService dashboardService,
        MeterRegistry meterRegistry,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataSourceService = dataSourceService;
        this.dashboardService = dashboardService;
        this.meterRegistry = meterRegistry;
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
        this.cleanRunSuccessCounter = Counter.builder("audit.process.clean.run.success").register(meterRegistry);
        this.cleanRunFailedCounter = Counter.builder("audit.process.clean.run.failed").register(meterRegistry);
        this.fusionRunSuccessCounter = Counter.builder("audit.process.fusion.run.success").register(meterRegistry);
        this.fusionRunFailedCounter = Counter.builder("audit.process.fusion.run.failed").register(meterRegistry);
        ensureStagingSchema();
        ensureCleanStrategyColumns();
        ensureGovernanceTables();
        ensureWorkflowTables();
        ensureAuditActionTable();
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

    public Map<String, Object> getCleanRuleDetail(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,name,category,file_name,content,enabled,remark,updated_at FROM clean_rule_record WHERE owner_username=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("category", rs.getString("category"));
                row.put("fileName", nvl(rs.getString("file_name")));
                row.put("content", nvl(rs.getString("content")));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername,
            id
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("规则不存在");
        return rows.get(0);
    }

    public Map<String, Object> updateCleanRule(String ownerUsername, Long id, Map<String, Object> payload) {
        Map<String, Object> existing = getCleanRuleDetail(ownerUsername, id);
        if ("SYSTEM".equalsIgnoreCase(String.valueOf(existing.get("category")))) {
            throw new IllegalArgumentException("系统规则不允许编辑");
        }

        String name = text(payload.get("name"));
        String fileName = text(payload.get("fileName"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(fileName) || isBlank(content)) {
            throw new IllegalArgumentException("规则名称、文件和内容不能为空");
        }

        int updated = jdbcTemplate.update(
            "UPDATE clean_rule_record SET name=?, file_name=?, content=?, remark=?, updated_at=? WHERE owner_username=? AND id=?",
            name,
            fileName,
            content,
            remark,
            now(),
            ownerUsername,
            id
        );
        if (updated == 0) throw new IllegalArgumentException("规则不存在");
        return getCleanRuleDetail(ownerUsername, id);
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
            "SELECT id,name,code,content,remark,built_in,enabled,updated_at FROM clean_strategy_record WHERE owner_username=? ORDER BY updated_at DESC,id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("code", rs.getString("code"));
                row.put("content", nvl(rs.getString("content")));
                row.put("remark", nvl(rs.getString("remark")));
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
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
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
            "INSERT INTO clean_strategy_record(owner_username,name,code,content,remark,built_in,enabled,created_at,updated_at) VALUES(?,?,?,?,?,0,1,?,?)",
            ownerUsername,
            name,
            code,
            content,
            remark,
            now,
            now
        );

        return Map.of(
            "id", String.valueOf(id),
            "name", name,
            "code", code,
            "content", content,
            "remark", remark,
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

    public Map<String, Object> getCleanStrategyDetail(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,name,code,content,remark,built_in,enabled,updated_at FROM clean_strategy_record WHERE owner_username=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("code", rs.getString("code"));
                row.put("content", nvl(rs.getString("content")));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("builtIn", rs.getBoolean("built_in"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername,
            id
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("策略不存在");
        return rows.get(0);
    }

    public Map<String, Object> updateCleanStrategy(String ownerUsername, Long id, Map<String, Object> payload) {
        Map<String, Object> existing = getCleanStrategyDetail(ownerUsername, id);
        if (Boolean.TRUE.equals(existing.get("builtIn"))) {
            throw new IllegalArgumentException("系统策略不允许编辑");
        }

        String name = text(payload.get("name"));
        String code = text(payload.get("code"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(code)) {
            throw new IllegalArgumentException("策略名称和编码不能为空");
        }

        Integer duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND code=? AND id<>?",
            Integer.class,
            ownerUsername,
            code,
            id
        );
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("策略编码已存在");
        }

        int updated = jdbcTemplate.update(
            "UPDATE clean_strategy_record SET name=?, code=?, content=?, remark=?, updated_at=? WHERE owner_username=? AND id=?",
            name,
            code,
            content,
            remark,
            now(),
            ownerUsername,
            id
        );
        if (updated == 0) throw new IllegalArgumentException("策略不存在");
        return getCleanStrategyDetail(ownerUsername, id);
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
        requireAuthenticated(ownerUsername);
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

        Map<String, Object> created = getCleanTaskById(ownerUsername, id);
        recordAudit(ownerUsername, "CREATE", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of("taskName", taskName));
        invalidateDashboardCache(ownerUsername);
        return created;
    }

    public Map<String, Object> runCleanTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<String, Object> task = getCleanTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("当前任务状态不允许执行");
        }

        String outputTable = sanitizeTableName(String.valueOf(task.get("standardTable")));
        String outputTableRef = stagingTableRef(outputTable);
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
            loadObjectsIntoStandardTable(ownerUsername, id, cleanObjects, outputTableRef);
            applyCleanStrategy(ownerUsername, outputTableRef, strategyCode, ruleNames);

            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + outputTableRef, Integer.class);
            cleanedRows = count == null ? 0 : count;

            jdbcTemplate.update(
                "UPDATE clean_task_record SET status='COMPLETED', cleaned_rows=?, updated_at=? WHERE owner_username=? AND id=?",
                cleanedRows,
                now(),
                ownerUsername,
                id
            );
            cleanRunSuccessCounter.increment();
            persistGovernanceArtifacts(ownerUsername, "CLEAN", id, outputTable, cleanObjects.stream()
                .map(it -> text(it.get("objectName")))
                .filter(it -> !isBlank(it))
                .toList());
            recordAudit(ownerUsername, "RUN", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of("outputTable", outputTable));
        } catch (RuntimeException ex) {
            jdbcTemplate.update(
                "UPDATE clean_task_record SET status='FAILED', updated_at=? WHERE owner_username=? AND id=?",
                now(),
                ownerUsername,
                id
            );
            cleanRunFailedCounter.increment();
            recordAudit(ownerUsername, "RUN", "CLEAN_TASK", String.valueOf(id), "FAILED", Map.of("reason", nvl(ex.getMessage())));
            throw ex;
        } finally {
            sample.stop(Timer.builder("audit.process.clean.run.duration").register(meterRegistry));
        }

        Map<String, Object> completed = getCleanTaskById(ownerUsername, id);
        invalidateDashboardCache(ownerUsername);
        return completed;
    }

    public void deleteCleanTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> task = getCleanTaskById(ownerUsername, id);
        String standardTable = text(task.get("standardTable"));

        // 级联删除依赖该清洗任务的融合任务，并清理融合目标表
        List<Map<String, Object>> fusionTasks = jdbcTemplate.query(
            "SELECT * FROM fusion_task_record WHERE owner_username=?",
            (rs, i) -> fusionTaskRow(rs),
            ownerUsername
        );
        for (Map<String, Object> fusionTask : fusionTasks) {
            List<Long> cleanTaskIds = castLongList(fusionTask.get("cleanTaskIds"));
            Long fusionTaskId = toLong(fusionTask.get("id"));
            if (fusionTaskId != null && cleanTaskIds.contains(id)) {
                deleteFusionTask(ownerUsername, fusionTaskId);
            }
        }

        int affected = jdbcTemplate.update("DELETE FROM clean_task_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (affected == 0) throw new IllegalArgumentException("清洗任务不存在");

        dropStandardTableIfUnused(ownerUsername, standardTable);
        recordAudit(ownerUsername, "DELETE", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of("standardTable", standardTable));
        invalidateDashboardCache(ownerUsername);
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
        requireAuthenticated(ownerUsername);
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

        Map<String, Object> created = getFusionTaskById(ownerUsername, id);
        recordAudit(ownerUsername, "CREATE", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("taskName", taskName));
        invalidateDashboardCache(ownerUsername);
        return created;
    }

    public Map<String, Object> runFusionTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("当前任务状态不允许执行");
        }

        String targetTable = sanitizeTableName(String.valueOf(task.get("targetTable")));
        String targetTableRef = stagingTableRef(targetTable);
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
            fusionRows = mergeStandardTablesToTarget(ownerUsername, id, targetTableRef, standardTables);

            jdbcTemplate.update(
                "UPDATE fusion_task_record SET status='COMPLETED', fusion_rows=?, updated_at=? WHERE owner_username=? AND id=?",
                fusionRows,
                now(),
                ownerUsername,
                id
            );
            fusionRunSuccessCounter.increment();
            persistGovernanceArtifacts(ownerUsername, "FUSION", id, targetTable, standardTables);
            recordAudit(ownerUsername, "RUN", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("targetTable", targetTable));
        } catch (RuntimeException ex) {
            jdbcTemplate.update(
                "UPDATE fusion_task_record SET status='FAILED', updated_at=? WHERE owner_username=? AND id=?",
                now(), ownerUsername, id
            );
            fusionRunFailedCounter.increment();
            recordAudit(ownerUsername, "RUN", "FUSION_TASK", String.valueOf(id), "FAILED", Map.of("reason", nvl(ex.getMessage())));
            throw ex;
        } finally {
            sample.stop(Timer.builder("audit.process.fusion.run.duration").register(meterRegistry));
        }

        Map<String, Object> completed = getFusionTaskById(ownerUsername, id);
        invalidateDashboardCache(ownerUsername);
        return completed;
    }

    public Map<String, Object> previewFusionTask(String ownerUsername, Long id, Integer limit) {
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String targetTable = sanitizeTableName(String.valueOf(task.get("targetTable")));
        String targetTableRef = stagingTableRef(targetTable);
        int safeLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 200);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM " + targetTableRef + " LIMIT " + safeLimit
        );

        List<String> columns = rows.isEmpty()
            ? List.of()
            : new ArrayList<>(rows.get(0).keySet());

        return Map.of(
            "targetTable", targetTable,
            "columns", columns,
            "rows", rows,
            "size", rows.size()
        );
    }

    public void deleteFusionTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String targetTable = text(task.get("targetTable"));

        int affected = jdbcTemplate.update("DELETE FROM fusion_task_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (affected == 0) throw new IllegalArgumentException("融合任务不存在");

        dropFusionTargetTableIfUnused(ownerUsername, targetTable);
        recordAudit(ownerUsername, "DELETE", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("targetTable", targetTable));
        invalidateDashboardCache(ownerUsername);
    }

    public Map<String, Object> cleanupOrphanGeneratedTables(String ownerUsername) {
        requireAuthenticated(ownerUsername);
        Set<String> referencedTables = new LinkedHashSet<>();

        List<String> standardTables = jdbcTemplate.query(
            "SELECT standard_table FROM clean_task_record",
            (rs, i) -> text(rs.getString("standard_table"))
        );
        for (String table : standardTables) {
            if (!isBlank(table)) {
                try {
                    referencedTables.add(sanitizeTableName(table));
                } catch (IllegalArgumentException ignore) {
                    // ignore illegal table names in historical dirty data
                }
            }
        }

        List<String> fusionTables = jdbcTemplate.query(
            "SELECT target_table FROM fusion_task_record",
            (rs, i) -> text(rs.getString("target_table"))
        );
        for (String table : fusionTables) {
            if (!isBlank(table)) {
                try {
                    referencedTables.add(sanitizeTableName(table));
                } catch (IllegalArgumentException ignore) {
                    // ignore illegal table names in historical dirty data
                }
            }
        }

        List<String> allTables = listAllTables();
        List<String> droppedTables = new ArrayList<>();

        for (String table : allTables) {
            if (referencedTables.contains(table)) {
                continue;
            }
            if (isGeneratedTableCandidate(table)) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + stagingTableRef(table));
                droppedTables.add(table);
            }
        }

        return Map.of(
            "owner", ownerUsername,
            "droppedCount", droppedTables.size(),
            "droppedTables", droppedTables,
            "referencedCount", referencedTables.size()
        );
    }

    public Map<String, Object> runWorkflow(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        List<WorkflowNode> nodes = parseWorkflowNodes(payload);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("工作流至少需要一个任务");
        }

        String tenantId = resolveTenantId(ownerUsername);
        String workflowName = text(payload.get("workflowName"));
        if (isBlank(workflowName)) {
            workflowName = "workflow-" + System.currentTimeMillis();
        }

        validateWorkflowNodes(nodes);

        Long workflowId = insertAndGetId(
            "INSERT INTO etl_workflow_record(tenant_id,owner_username,workflow_name,workflow_json,created_at,updated_at) VALUES(?,?,?,?,?,?)",
            tenantId,
            ownerUsername,
            workflowName,
            toJson(payload),
            now(),
            now()
        );

        Long runId = insertAndGetId(
            "INSERT INTO etl_workflow_run_record(tenant_id,owner_username,workflow_id,run_status,start_at,end_at,error_message,created_at,updated_at) VALUES(?,?,?, 'RUNNING', ?, NULL, '', ?, ?)",
            tenantId,
            ownerUsername,
            workflowId,
            now(),
            now(),
            now()
        );

        long start = System.currentTimeMillis();
        List<Map<String, Object>> cleanResults = new ArrayList<>();
        List<Map<String, Object>> fusionResults = new ArrayList<>();
        List<Map<String, Object>> nodeResults = new ArrayList<>();
        Set<String> executed = new HashSet<>();

        try {
            while (executed.size() < nodes.size()) {
                boolean progressed = false;
                for (WorkflowNode node : nodes) {
                    if (executed.contains(node.nodeId)) {
                        continue;
                    }
                    if (!executed.containsAll(node.dependsOn)) {
                        continue;
                    }

                    progressed = true;
                    String startedAt = now();
                    try {
                        Map<String, Object> result = executeWorkflowNode(ownerUsername, node);
                        if ("CLEAN".equals(node.taskType)) {
                            cleanResults.add(result);
                        } else {
                            fusionResults.add(result);
                        }
                        nodeResults.add(Map.of(
                            "nodeId", node.nodeId,
                            "taskType", node.taskType,
                            "taskId", node.taskId,
                            "status", "COMPLETED",
                            "result", result
                        ));
                        jdbcTemplate.update(
                            "INSERT INTO etl_workflow_node_run_record(run_id,node_id,task_type,task_id,status,error_message,started_at,ended_at) VALUES(?,?,?,?, 'COMPLETED','',?,?)",
                            runId,
                            node.nodeId,
                            node.taskType,
                            node.taskId,
                            startedAt,
                            now()
                        );
                        executed.add(node.nodeId);
                    } catch (RuntimeException ex) {
                        String reason = nvl(ex.getMessage());
                        nodeResults.add(Map.of(
                            "nodeId", node.nodeId,
                            "taskType", node.taskType,
                            "taskId", node.taskId,
                            "status", "FAILED",
                            "reason", reason
                        ));
                        jdbcTemplate.update(
                            "INSERT INTO etl_workflow_node_run_record(run_id,node_id,task_type,task_id,status,error_message,started_at,ended_at) VALUES(?,?,?,?, 'FAILED',?,?,?)",
                            runId,
                            node.nodeId,
                            node.taskType,
                            node.taskId,
                            reason,
                            startedAt,
                            now()
                        );
                        jdbcTemplate.update(
                            "UPDATE etl_workflow_run_record SET run_status='FAILED', end_at=?, error_message=?, updated_at=? WHERE id=?",
                            now(),
                            reason,
                            now(),
                            runId
                        );
                        throw ex;
                    }
                }
                if (!progressed) {
                    throw new IllegalArgumentException("工作流存在循环依赖或无可执行节点");
                }
            }
        } catch (RuntimeException ex) {
            recordAudit(ownerUsername, "RUN", "WORKFLOW", String.valueOf(workflowId), "FAILED", Map.of("reason", nvl(ex.getMessage())));
            throw ex;
        }

        long cost = System.currentTimeMillis() - start;
        jdbcTemplate.update(
            "UPDATE etl_workflow_run_record SET run_status='COMPLETED', end_at=?, error_message='', updated_at=? WHERE id=?",
            now(),
            now(),
            runId
        );

        Map<String, Object> result = Map.of(
            "workflowId", workflowId,
            "runId", runId,
            "cleanExecuted", cleanResults.size(),
            "fusionExecuted", fusionResults.size(),
            "costMs", cost,
            "cleanResults", cleanResults,
            "fusionResults", fusionResults,
            "nodeResults", nodeResults
        );
        recordAudit(ownerUsername, "RUN", "WORKFLOW", String.valueOf(workflowId), "SUCCESS", result);
        return result;
    }

    public List<Map<String, Object>> listLineageRecords(String ownerUsername, String taskType, Long taskId) {
                String tenantId = resolveTenantId(ownerUsername);
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

    public List<Map<String, Object>> listQualityReports(String ownerUsername, String taskType, Long taskId) {
                String tenantId = resolveTenantId(ownerUsername);
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

    public List<Map<String, Object>> listSnapshotRecords(String ownerUsername, String taskType, Long taskId) {
                String tenantId = resolveTenantId(ownerUsername);
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

    public List<Map<String, Object>> listAuditRecords(String ownerUsername, Integer limit) {
        String tenantId = resolveTenantId(ownerUsername);
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

    private void invalidateDashboardCache(String ownerUsername) {
        dashboardService.invalidateOwnerCache(ownerUsername);
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
            "INSERT INTO clean_strategy_record(owner_username,name,code,content,remark,built_in,enabled,created_at,updated_at) VALUES(?,?,?,?,?,1,1,?,?)",
            ownerUsername,
            name,
            code,
            "",
            "系统默认策略",
            now,
            now
        );
    }

    private void ensureCleanStrategyColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE clean_strategy_record ADD COLUMN content TEXT");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }

        try {
            jdbcTemplate.execute("ALTER TABLE clean_strategy_record ADD COLUMN remark VARCHAR(512)");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
    }

    private boolean isDuplicateColumnError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("duplicate column") ||
                    normalized.contains("already exists") ||
                    normalized.contains("column already exists")) {
                    return true;
                }
            }
            if (current instanceof SQLException sqlEx) {
                if (sqlEx.getErrorCode() == 1060) {
                    return true;
                }
                String sqlState = sqlEx.getSQLState();
                if ("42S21".equalsIgnoreCase(sqlState)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
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
        String tableRef = stagingTableRef(tableName);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableRef);
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
                        """.formatted(tableRef)
        );
    }

    private void recreateFusionTable(String tableName) {
        String tableRef = stagingTableRef(tableName);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableRef);
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
                        """.formatted(tableRef)
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

    private void applyCleanStrategy(String ownerUsername, String outputTable, String strategyCode, List<String> ruleNames) {
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

        List<RuleAction> actions = loadRuleActions(ownerUsername, ruleNames);
        if (!actions.isEmpty()) {
            applyRuleActionsToRows(outputTable, actions);
        } else if (ruleNames.stream().anyMatch(name -> name.contains("空值"))) {
            // Backward compatibility for historical tasks that only rely on rule names.
            jdbcTemplate.update("UPDATE " + outputTable + " SET normalized_json=REPLACE(normalized_json, ':\"\"', ':\"UNKNOWN\"')");
        }
    }

    private List<RuleAction> loadRuleActions(String ownerUsername, List<String> ruleNames) {
        if (ruleNames == null || ruleNames.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueNames = new LinkedHashSet<>();
        for (String name : ruleNames) {
            if (!isBlank(name)) {
                uniqueNames.add(name);
            }
        }
        if (uniqueNames.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", uniqueNames.stream().map(it -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(ownerUsername);
        args.addAll(uniqueNames);

        String sql = "SELECT name,content FROM clean_rule_record WHERE owner_username=? AND enabled=1 AND name IN (" + placeholders + ") ORDER BY id ASC";
        List<Map<String, Object>> rows = jdbcTemplate.query(
            sql,
            (rs, i) -> Map.of("name", nvl(rs.getString("name")), "content", nvl(rs.getString("content"))),
            args.toArray()
        );

        List<RuleAction> actions = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String ruleName = text(row.get("name"));
            String content = text(row.get("content"));
            actions.addAll(parseRuleActions(content));

            if (ruleName.contains("空值") && actions.stream().noneMatch(action -> "fill_null".equals(action.type))) {
                actions.add(new RuleAction("fill_null", "*", "UNKNOWN", "", ""));
            }
        }
        return actions;
    }

    private List<RuleAction> parseRuleActions(String content) {
        if (isBlank(content)) {
            return List.of();
        }

        String trimmed = content.trim();
        List<RuleAction> actions = new ArrayList<>();

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                if (trimmed.startsWith("{")) {
                    Map<String, Object> obj = objectMapper.readValue(trimmed, new TypeReference<>() {});
                    Object rootActions = obj.get("actions");
                    if (rootActions instanceof List<?> list) {
                        actions.addAll(parseActionList(list));
                    } else {
                        RuleAction single = parseActionMap(obj);
                        if (single != null) actions.add(single);
                    }
                } else {
                    List<Map<String, Object>> list = objectMapper.readValue(trimmed, new TypeReference<>() {});
                    actions.addAll(parseActionList(new ArrayList<>(list)));
                }
            } catch (Exception ignore) {
                // Fall back to line-based parsing below.
            }
        }

        if (!actions.isEmpty()) {
            return actions;
        }

        String[] lines = trimmed.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\\|", -1);
            String type = parts[0].trim().toLowerCase();
            if (type.isEmpty()) {
                continue;
            }

            String field = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : "*";
            String value = parts.length > 2 ? parts[2].trim() : "";
            String from = parts.length > 2 ? parts[2].trim() : "";
            String to = parts.length > 3 ? parts[3].trim() : "";
            actions.add(new RuleAction(type, field, value, from, to));
        }

        if (actions.isEmpty() && (trimmed.contains("空值") || trimmed.contains("fill_null"))) {
            actions.add(new RuleAction("fill_null", "*", "UNKNOWN", "", ""));
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<RuleAction> parseActionList(List<?> list) {
        List<RuleAction> actions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                RuleAction action = parseActionMap((Map<String, Object>) map);
                if (action != null) actions.add(action);
            }
        }
        return actions;
    }

    private RuleAction parseActionMap(Map<String, Object> map) {
        String type = text(map.get("type")).toLowerCase();
        if (isBlank(type)) {
            return null;
        }
        String field = text(map.get("field"));
        if (isBlank(field)) {
            field = "*";
        }
        String value = text(map.get("value"));
        String from = text(map.get("from"));
        String to = text(map.get("to"));
        return new RuleAction(type, field, value, from, to);
    }

    private void applyRuleActionsToRows(String outputTable, List<RuleAction> actions) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, normalized_json FROM " + outputTable + " ORDER BY id ASC",
            (rs, i) -> Map.of("id", rs.getLong("id"), "json", nvl(rs.getString("normalized_json")))
        );

        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            String json = text(row.get("json"));
            if (id == null || isBlank(json)) {
                continue;
            }

            Map<String, Object> obj;
            try {
                obj = objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception ex) {
                continue;
            }

            boolean changed = false;
            for (RuleAction action : actions) {
                changed = applyRuleAction(obj, action) || changed;
            }

            if (changed) {
                jdbcTemplate.update(
                    "UPDATE " + outputTable + " SET normalized_json=? WHERE id=?",
                    toJson(obj),
                    id
                );
            }
        }
    }

    private boolean applyRuleAction(Map<String, Object> obj, RuleAction action) {
        String field = isBlank(action.field) ? "*" : action.field;
        String type = action.type;

        if ("remove_field".equals(type)) {
            if ("*".equals(field)) {
                return false;
            }
            return obj.remove(field) != null;
        }

        if ("*".equals(field)) {
            boolean changed = false;
            for (String key : new ArrayList<>(obj.keySet())) {
                changed = applyRuleToField(obj, key, action) || changed;
            }
            return changed;
        }

        return applyRuleToField(obj, field, action);
    }

    private boolean applyRuleToField(Map<String, Object> obj, String field, RuleAction action) {
        Object current = obj.get(field);
        String currentText = current == null ? "" : String.valueOf(current);

        return switch (action.type) {
            case "fill_null" -> {
                if (current == null || currentText.isBlank()) {
                    obj.put(field, isBlank(action.value) ? "UNKNOWN" : action.value);
                    yield true;
                }
                yield false;
            }
            case "trim" -> {
                if (current == null) yield false;
                String trimmed = currentText.trim();
                if (!trimmed.equals(currentText)) {
                    obj.put(field, trimmed);
                    yield true;
                }
                yield false;
            }
            case "lowercase" -> {
                if (current == null) yield false;
                String lowered = currentText.toLowerCase();
                if (!lowered.equals(currentText)) {
                    obj.put(field, lowered);
                    yield true;
                }
                yield false;
            }
            case "uppercase" -> {
                if (current == null) yield false;
                String uppered = currentText.toUpperCase();
                if (!uppered.equals(currentText)) {
                    obj.put(field, uppered);
                    yield true;
                }
                yield false;
            }
            case "replace" -> {
                if (current == null || isBlank(action.from)) yield false;
                String replaced = currentText.replace(action.from, nvl(action.to));
                if (!replaced.equals(currentText)) {
                    obj.put(field, replaced);
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private record RuleAction(String type, String field, String value, String from, String to) {
    }

    private int mergeStandardTablesToTarget(String ownerUsername, Long fusionTaskId, String targetTableRef, List<String> standardTables) {
        int total = 0;
        for (String table : standardTables) {
            String safeStandardTable = sanitizeTableName(table);
            String sourceTableRef = stagingTableRef(safeStandardTable);
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
                                """.formatted(targetTableRef, sourceTableRef),
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

    private void dropStandardTableIfUnused(String ownerUsername, String standardTable) {
        if (isBlank(standardTable)) return;

        String safeTable;
        try {
            safeTable = sanitizeTableName(standardTable);
        } catch (IllegalArgumentException ex) {
            return;
        }

        Integer cleanTaskRef = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_task_record WHERE standard_table=?",
            Integer.class,
            safeTable
        );
        if (cleanTaskRef != null && cleanTaskRef > 0) return;

        Integer fusionRef = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM fusion_task_record WHERE standard_tables_json LIKE ?",
            Integer.class,
            "%\"" + safeTable + "\"%"
        );
        if (fusionRef != null && fusionRef > 0) return;

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + stagingTableRef(safeTable));
    }

    private void dropFusionTargetTableIfUnused(String ownerUsername, String targetTable) {
        if (isBlank(targetTable)) return;

        String safeTable;
        try {
            safeTable = sanitizeTableName(targetTable);
        } catch (IllegalArgumentException ex) {
            return;
        }

        Integer fusionRef = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM fusion_task_record WHERE target_table=?",
            Integer.class,
            safeTable
        );
        if (fusionRef != null && fusionRef > 0) return;

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + stagingTableRef(safeTable));
    }

    private List<String> listAllTables() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema=? ORDER BY table_name",
            stagingSchema
        );
        List<String> tables = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("table_name");
            if (value == null) {
                value = row.get("TABLE_NAME");
            }
            if (value != null) {
                tables.add(String.valueOf(value));
            }
        }
        return tables;
    }

    private boolean isGeneratedTableCandidate(String tableName) {
        String normalized = text(tableName).toLowerCase();
        return normalized.startsWith("clean_std_")
            || normalized.startsWith("fusion_")
            || normalized.startsWith("tmp_fusion_")
            || normalized.startsWith("std_")
            || normalized.startsWith("fuse_");
    }

    private void ensureStagingSchema() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + stagingSchema);
    }

    private void ensureGovernanceTables() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_field_lineage (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            tenant_id VARCHAR(128),
              owner_username VARCHAR(128) NOT NULL,
              task_type VARCHAR(32) NOT NULL,
              task_id BIGINT NOT NULL,
              source_table VARCHAR(255) NOT NULL,
              source_field VARCHAR(255) NOT NULL,
              target_table VARCHAR(255) NOT NULL,
              target_field VARCHAR(255) NOT NULL,
              created_at DATETIME NOT NULL,
              INDEX idx_lineage_owner_task (owner_username, task_type, task_id)
            )
            """
        );

        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_quality_report (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            tenant_id VARCHAR(128),
              owner_username VARCHAR(128) NOT NULL,
              task_type VARCHAR(32) NOT NULL,
              task_id BIGINT NOT NULL,
              table_name VARCHAR(255) NOT NULL,
              total_rows INT NOT NULL,
              unknown_rows INT NOT NULL,
              duplicate_rows INT NOT NULL,
              quality_score INT NOT NULL,
              created_at DATETIME NOT NULL,
              INDEX idx_quality_owner_task (owner_username, task_type, task_id)
            )
            """
        );

        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_table_snapshot (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            tenant_id VARCHAR(128),
              owner_username VARCHAR(128) NOT NULL,
              task_type VARCHAR(32) NOT NULL,
              task_id BIGINT NOT NULL,
              table_name VARCHAR(255) NOT NULL,
              snapshot_version INT NOT NULL,
              row_count INT NOT NULL,
              schema_json LONGTEXT,
              created_at DATETIME NOT NULL,
              INDEX idx_snapshot_owner_task (owner_username, task_type, task_id)
            )
            """
        );

                ensureColumnExists("ALTER TABLE etl_field_lineage ADD COLUMN tenant_id VARCHAR(128)");
                ensureColumnExists("ALTER TABLE etl_quality_report ADD COLUMN tenant_id VARCHAR(128)");
                ensureColumnExists("ALTER TABLE etl_table_snapshot ADD COLUMN tenant_id VARCHAR(128)");
        }

        private void ensureWorkflowTables() {
                jdbcTemplate.execute(
                        """
                        CREATE TABLE IF NOT EXISTS etl_workflow_record (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            tenant_id VARCHAR(128) NOT NULL,
                            owner_username VARCHAR(128) NOT NULL,
                            workflow_name VARCHAR(255) NOT NULL,
                            workflow_json LONGTEXT,
                            created_at DATETIME NOT NULL,
                            updated_at DATETIME NOT NULL,
                            INDEX idx_workflow_owner (tenant_id, owner_username)
                        )
                        """
                );

                jdbcTemplate.execute(
                        """
                        CREATE TABLE IF NOT EXISTS etl_workflow_run_record (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            tenant_id VARCHAR(128) NOT NULL,
                            owner_username VARCHAR(128) NOT NULL,
                            workflow_id BIGINT NOT NULL,
                            run_status VARCHAR(32) NOT NULL,
                            start_at DATETIME,
                            end_at DATETIME,
                            error_message VARCHAR(1024),
                            created_at DATETIME NOT NULL,
                            updated_at DATETIME NOT NULL,
                            INDEX idx_workflow_run_owner (tenant_id, owner_username, workflow_id)
                        )
                        """
                );

                jdbcTemplate.execute(
                        """
                        CREATE TABLE IF NOT EXISTS etl_workflow_node_run_record (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            run_id BIGINT NOT NULL,
                            node_id VARCHAR(128) NOT NULL,
                            task_type VARCHAR(32) NOT NULL,
                            task_id BIGINT NOT NULL,
                            status VARCHAR(32) NOT NULL,
                            error_message VARCHAR(1024),
                            started_at DATETIME,
                            ended_at DATETIME,
                            INDEX idx_node_run_run (run_id)
                        )
                        """
                );
    }

    private void ensureAuditActionTable() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS audit_action_record (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            tenant_id VARCHAR(128),
              actor_username VARCHAR(128) NOT NULL,
              action_type VARCHAR(32) NOT NULL,
              resource_type VARCHAR(64) NOT NULL,
              resource_id VARCHAR(64) NOT NULL,
              result_status VARCHAR(16) NOT NULL,
              detail_json LONGTEXT,
              created_at DATETIME NOT NULL,
              INDEX idx_audit_actor_created (actor_username, created_at)
            )
            """
        );
        ensureColumnExists("ALTER TABLE audit_action_record ADD COLUMN tenant_id VARCHAR(128)");
    }

    private void persistGovernanceArtifacts(String ownerUsername, String taskType, Long taskId, String targetTable, List<String> sourceTables) {
        String tenantId = resolveTenantId(ownerUsername);
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

    private void recordAudit(String ownerUsername, String actionType, String resourceType, String resourceId, String resultStatus, Map<String, Object> detail) {
        String tenantId = resolveTenantId(ownerUsername);
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

    private void requireAuthenticated(String ownerUsername) {
        if (isBlank(ownerUsername) || "anonymous".equalsIgnoreCase(ownerUsername)) {
            throw new IllegalArgumentException("未认证用户不允许执行写操作");
        }
    }

    private void ensureColumnExists(String alterSql) {
        try {
            jdbcTemplate.execute(alterSql);
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
    }

    private List<WorkflowNode> parseWorkflowNodes(Map<String, Object> payload) {
        List<Map<String, Object>> nodeMaps = castMapList(payload.get("nodes"));
        List<WorkflowNode> nodes = new ArrayList<>();
        if (!nodeMaps.isEmpty()) {
            for (Map<String, Object> node : nodeMaps) {
                String nodeId = text(node.get("nodeId"));
                if (isBlank(nodeId)) {
                    nodeId = "node-" + (nodes.size() + 1);
                }
                String taskType = text(node.get("taskType")).toUpperCase();
                Long taskId = toLong(node.get("taskId"));
                List<String> dependsOn = castStringList(node.get("dependsOn"));
                nodes.add(new WorkflowNode(nodeId, taskType, taskId, dependsOn));
            }
            return nodes;
        }

        List<Long> cleanTaskIds = castLongList(payload.get("cleanTaskIds"));
        List<Long> fusionTaskIds = castLongList(payload.get("fusionTaskIds"));
        for (Long id : cleanTaskIds) {
            nodes.add(new WorkflowNode("clean-" + id, "CLEAN", id, List.of()));
        }
        List<String> cleanNodeIds = nodes.stream().map(WorkflowNode::nodeId).toList();
        for (Long id : fusionTaskIds) {
            nodes.add(new WorkflowNode("fusion-" + id, "FUSION", id, cleanNodeIds));
        }
        return nodes;
    }

    private void validateWorkflowNodes(List<WorkflowNode> nodes) {
        Set<String> ids = new HashSet<>();
        for (WorkflowNode node : nodes) {
            if (!ids.add(node.nodeId)) {
                throw new IllegalArgumentException("工作流节点ID重复: " + node.nodeId);
            }
            if (!Set.of("CLEAN", "FUSION").contains(node.taskType)) {
                throw new IllegalArgumentException("工作流任务类型仅支持 CLEAN/FUSION");
            }
            if (node.taskId == null || node.taskId <= 0) {
                throw new IllegalArgumentException("工作流节点任务ID不合法");
            }
        }
        for (WorkflowNode node : nodes) {
            for (String dep : node.dependsOn) {
                if (!ids.contains(dep)) {
                    throw new IllegalArgumentException("工作流依赖节点不存在: " + dep);
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> edges = new HashMap<>();
        for (WorkflowNode node : nodes) {
            indegree.put(node.nodeId, node.dependsOn.size());
            edges.putIfAbsent(node.nodeId, new ArrayList<>());
        }
        for (WorkflowNode node : nodes) {
            for (String dep : node.dependsOn) {
                edges.computeIfAbsent(dep, it -> new ArrayList<>()).add(node.nodeId);
            }
        }
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String next : edges.getOrDefault(current, List.of())) {
                int v = indegree.get(next) - 1;
                indegree.put(next, v);
                if (v == 0) {
                    queue.offer(next);
                }
            }
        }
        if (visited != nodes.size()) {
            throw new IllegalArgumentException("工作流存在循环依赖");
        }
    }

    private Map<String, Object> executeWorkflowNode(String ownerUsername, WorkflowNode node) {
        return switch (node.taskType) {
            case "CLEAN" -> runCleanTask(ownerUsername, node.taskId);
            case "FUSION" -> runFusionTask(ownerUsername, node.taskId);
            default -> throw new IllegalArgumentException("不支持的节点任务类型: " + node.taskType);
        };
    }

    private String resolveTenantId(String ownerUsername) {
        String normalized = text(ownerUsername);
        int pos = normalized.indexOf(':');
        if (pos > 0) {
            return normalized.substring(0, pos);
        }
        return "default";
    }

    private record WorkflowNode(String nodeId, String taskType, Long taskId, List<String> dependsOn) {
    }

    private String stagingTableRef(String tableName) {
        return stagingSchema + "." + sanitizeTableName(tableName);
    }

    private String sanitizeSchemaName(String schemaName) {
        String normalized = text(schemaName);
        if (!SAFE_SCHEMA_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("schema 名不合法: " + schemaName);
        }
        return normalized;
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
