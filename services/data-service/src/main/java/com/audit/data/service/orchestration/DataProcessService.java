package com.audit.data.service.orchestration;

import com.audit.data.repository.DataProcessTaskRepository;
import com.audit.data.service.DashboardService;
import com.audit.data.service.DataSourceService;
import com.audit.data.service.api.IDataProcessService;
import com.audit.data.service.domain.CleanConfigService;
import com.audit.data.service.domain.CleanRuleEngineService;
import com.audit.data.service.domain.GovernanceAuditService;
import com.audit.data.service.domain.WorkflowDefinitionService;
import com.audit.data.service.infrastructure.StagingTableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 数据处理编排服务：协调清洗、融合、工作流执行及治理审计落库。
 */
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
    private final DataProcessTaskRepository dataProcessTaskRepository;
    private final CleanConfigService cleanConfigService;
    private final StagingTableService stagingTableService;
    private final GovernanceAuditService governanceAuditService;
    private final CleanRuleEngineService cleanRuleEngineService;
    private final WorkflowDefinitionService workflowDefinitionService;
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
        DataProcessTaskRepository dataProcessTaskRepository,
        CleanConfigService cleanConfigService,
        StagingTableService stagingTableService,
        GovernanceAuditService governanceAuditService,
        CleanRuleEngineService cleanRuleEngineService,
        WorkflowDefinitionService workflowDefinitionService,
        MeterRegistry meterRegistry,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataSourceService = dataSourceService;
        this.dashboardService = dashboardService;
        this.dataProcessTaskRepository = dataProcessTaskRepository;
        this.cleanConfigService = cleanConfigService;
        this.stagingTableService = stagingTableService;
        this.governanceAuditService = governanceAuditService;
        this.cleanRuleEngineService = cleanRuleEngineService;
        this.workflowDefinitionService = workflowDefinitionService;
        this.meterRegistry = meterRegistry;
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
        this.cleanRunSuccessCounter = Counter.builder("audit.process.clean.run.success").register(meterRegistry);
        this.cleanRunFailedCounter = Counter.builder("audit.process.clean.run.failed").register(meterRegistry);
        this.fusionRunSuccessCounter = Counter.builder("audit.process.fusion.run.success").register(meterRegistry);
        this.fusionRunFailedCounter = Counter.builder("audit.process.fusion.run.failed").register(meterRegistry);
    }

    public List<Map<String, Object>> listCleanRules(String ownerUsername) {
        return cleanConfigService.listCleanRules(ownerUsername);
    }

    public Map<String, Object> uploadCleanRule(String ownerUsername, Map<String, Object> payload) {
        return cleanConfigService.uploadCleanRule(ownerUsername, payload);
    }

    public Map<String, Object> toggleCleanRule(String ownerUsername, Long id, boolean enabled) {
        return cleanConfigService.toggleCleanRule(ownerUsername, id, enabled);
    }

    public Map<String, Object> getCleanRuleDetail(String ownerUsername, Long id) {
        return cleanConfigService.getCleanRuleDetail(ownerUsername, id);
    }

    public Map<String, Object> updateCleanRule(String ownerUsername, Long id, Map<String, Object> payload) {
        return cleanConfigService.updateCleanRule(ownerUsername, id, payload);
    }

    public void deleteCleanRule(String ownerUsername, Long id) {
        cleanConfigService.deleteCleanRule(ownerUsername, id);
    }

    public List<Map<String, Object>> listCleanStrategies(String ownerUsername) {
        return cleanConfigService.listCleanStrategies(ownerUsername);
    }

    public Map<String, Object> createCleanStrategy(String ownerUsername, Map<String, Object> payload) {
        return cleanConfigService.createCleanStrategy(ownerUsername, payload);
    }

    public Map<String, Object> toggleCleanStrategy(String ownerUsername, Long id, boolean enabled) {
        return cleanConfigService.toggleCleanStrategy(ownerUsername, id, enabled);
    }

    public Map<String, Object> getCleanStrategyDetail(String ownerUsername, Long id) {
        return cleanConfigService.getCleanStrategyDetail(ownerUsername, id);
    }

    public Map<String, Object> updateCleanStrategy(String ownerUsername, Long id, Map<String, Object> payload) {
        return cleanConfigService.updateCleanStrategy(ownerUsername, id, payload);
    }

    public void deleteCleanStrategy(String ownerUsername, Long id) {
        cleanConfigService.deleteCleanStrategy(ownerUsername, id);
    }

    public List<Map<String, Object>> listCleanTasks(String ownerUsername, String keyword, String sourceId, String status) {
        List<Map<String, Object>> rows = dataProcessTaskRepository.listCleanTasks(ownerUsername);
        return rows.stream()
            .filter(item -> isBlank(keyword) || contains(item.get("taskName"), keyword) || listContains((List<?>) item.get("cleanObjectNames"), keyword))
            .filter(item -> isBlank(sourceId) || objectHasSource(asMapList(item.get("cleanObjects")), sourceId))
            .filter(item -> isBlank(status) || status.equalsIgnoreCase(String.valueOf(item.get("status"))))
            .toList();
    }

    @Transactional
    public Map<String, Object> createCleanTask(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        String taskName = text(payload.get("taskName"));
        String strategyCode = text(payload.get("strategy"));
        String standardTable = text(payload.get("standardTable"));
        String remark = text(payload.get("remark"));
        List<Map<String, Object>> cleanObjects = castMapList(payload.get("cleanObjects"));
        List<String> cleanRuleNames = castStringList(payload.get("cleanRuleNames"));

        if (isBlank(taskName) || isBlank(strategyCode) || cleanObjects.isEmpty()) {
            throw new IllegalArgumentException("娓呮礂浠诲姟蹇呭～椤圭己澶");
        }

        cleanConfigService.ensureDefaultCleanConfig(ownerUsername);
        Map<String, Object> strategy = cleanConfigService.getEnabledStrategy(ownerUsername, strategyCode);
        if (strategy.isEmpty()) throw new IllegalArgumentException("娓呮礂绛栫暐涓嶅瓨鍦ㄦ垨宸插仠鐢");

        for (Map<String, Object> object : cleanObjects) {
            Long sourceIdVal = toLong(object.get("sourceId"));
            String objectName = text(object.get("objectName"));
            if (sourceIdVal == null || isBlank(objectName)) throw new IllegalArgumentException("娓呮礂瀵硅薄淇℃伅涓嶅畬鏁");
            List<Map<String, Object>> objects = dataSourceService.listSourceObjects(ownerUsername, sourceIdVal);
            boolean valid = objects.stream().anyMatch(it -> objectName.equals(String.valueOf(it.get("objectName"))));
            if (!valid) throw new IllegalArgumentException("瀛樺湪鏃犳晥娓呮礂瀵硅薄锛岃閲嶆柊閫夋嫨");
        }

        List<String> objectNames = cleanObjects.stream()
            .map(obj -> text(obj.get("sourceName")) + " / " + text(obj.get("objectName")))
            .toList();

        String outputTable = isBlank(standardTable) ? "clean_std_" + System.currentTimeMillis() : standardTable;

        Long id = dataProcessTaskRepository.insertCleanTask(
            ownerUsername,
            taskName,
            toJson(cleanObjects),
            toJson(objectNames),
            toJson(cleanRuleNames),
            strategyCode,
            text(strategy.get("name")),
            outputTable,
            remark
        );

        Map<String, Object> created = getCleanTaskById(ownerUsername, id);
        recordAudit(ownerUsername, "CREATE", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of("taskName", taskName));
        invalidateDashboardCache(ownerUsername);
        return created;
    }

    @Transactional
    public Map<String, Object> runCleanTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<String, Object> task = getCleanTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("褰撳墠浠诲姟鐘舵€佷笉鍏佽鎵ц");
        }

        String outputTable = sanitizeTableName(String.valueOf(task.get("standardTable")));
        String outputTableRef = stagingTableRef(outputTable);
        List<Map<String, Object>> cleanObjects = castMapList(task.get("cleanObjects"));
        String strategyCode = text(task.get("strategy"));
        List<String> ruleNames = castStringList(task.get("cleanRuleNames"));

        dataProcessTaskRepository.markCleanTaskRunning(ownerUsername, id);

        int cleanedRows;
        try {
            stagingTableService.recreateStandardTable(outputTable);
            stagingTableService.loadObjectsIntoStandardTable(ownerUsername, id, cleanObjects, outputTable);
            cleanRuleEngineService.applyCleanStrategy(ownerUsername, outputTableRef, strategyCode, ruleNames);

            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + outputTableRef, Integer.class);
            cleanedRows = count == null ? 0 : count;

            dataProcessTaskRepository.markCleanTaskCompleted(ownerUsername, id, cleanedRows);
            cleanRunSuccessCounter.increment();
            persistGovernanceArtifacts(ownerUsername, "CLEAN", id, outputTable, cleanObjects.stream()
                .map(it -> text(it.get("objectName")))
                .filter(it -> !isBlank(it))
                .toList());
            recordAudit(ownerUsername, "RUN", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of("outputTable", outputTable));
        } catch (RuntimeException ex) {
            dataProcessTaskRepository.markCleanTaskFailed(ownerUsername, id);
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

    @Transactional
    public void deleteCleanTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> task = getCleanTaskById(ownerUsername, id);
        String standardTable = text(task.get("standardTable"));

        // 级联删除依赖该清洗任务的融合任务，并清理融合目标表。
        List<Map<String, Object>> fusionTasks = dataProcessTaskRepository.listFusionTasks(ownerUsername);
        for (Map<String, Object> fusionTask : fusionTasks) {
            List<Long> cleanTaskIds = castLongList(fusionTask.get("cleanTaskIds"));
            Long fusionTaskId = toLong(fusionTask.get("id"));
            if (fusionTaskId != null && cleanTaskIds.contains(id)) {
                deleteFusionTask(ownerUsername, fusionTaskId);
            }
        }

        int affected = dataProcessTaskRepository.deleteCleanTask(ownerUsername, id);
        if (affected == 0) throw new IllegalArgumentException("娓呮礂浠诲姟涓嶅瓨鍦");

        stagingTableService.dropStandardTableIfUnused(standardTable);
        recordAudit(ownerUsername, "DELETE", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of("standardTable", standardTable));
        invalidateDashboardCache(ownerUsername);
    }

    public List<Map<String, Object>> listFusionTasks(String ownerUsername, String keyword, String status) {
        List<Map<String, Object>> rows = dataProcessTaskRepository.listFusionTasks(ownerUsername);
        return rows.stream()
            .filter(item -> isBlank(keyword) || contains(item.get("taskName"), keyword) || contains(item.get("targetTable"), keyword))
            .filter(item -> isBlank(status) || status.equalsIgnoreCase(String.valueOf(item.get("status"))))
            .toList();
    }

    @Transactional
    public Map<String, Object> createFusionTask(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        String taskName = text(payload.get("taskName"));
        String targetTable = text(payload.get("targetTable"));
        String strategy = text(payload.get("strategy"));
        String remark = text(payload.get("remark"));
        List<Long> cleanTaskIds = castLongList(payload.get("cleanTaskIds"));

        if (isBlank(taskName) || isBlank(targetTable) || isBlank(strategy) || cleanTaskIds.isEmpty()) {
            throw new IllegalArgumentException("铻嶅悎浠诲姟蹇呭～椤圭己澶");
        }

        List<String> cleanTaskNames = new ArrayList<>();
        List<String> standardTables = new ArrayList<>();
        for (Long cleanTaskId : cleanTaskIds) {
            Map<String, Object> cleanTask = getCleanTaskById(ownerUsername, cleanTaskId);
            if (!"COMPLETED".equalsIgnoreCase(String.valueOf(cleanTask.get("status")))) {
                throw new IllegalArgumentException("浠呭彲閫夋嫨宸插畬鎴愮殑娓呮礂浠诲姟");
            }
            cleanTaskNames.add(String.valueOf(cleanTask.get("taskName")));
            standardTables.add(String.valueOf(cleanTask.get("standardTable")));
        }

        Long id = dataProcessTaskRepository.insertFusionTask(
            ownerUsername,
            taskName,
            targetTable,
            toJson(cleanTaskIds),
            toJson(cleanTaskNames),
            toJson(standardTables),
            strategy,
            remark
        );

        Map<String, Object> created = getFusionTaskById(ownerUsername, id);
        recordAudit(ownerUsername, "CREATE", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("taskName", taskName));
        invalidateDashboardCache(ownerUsername);
        return created;
    }

    @Transactional
    public Map<String, Object> runFusionTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("褰撳墠浠诲姟鐘舵€佷笉鍏佽鎵ц");
        }

        String targetTable = sanitizeTableName(String.valueOf(task.get("targetTable")));
        List<String> standardTables = castStringList(task.get("standardTables"));
        if (standardTables.isEmpty()) {
            throw new IllegalArgumentException("缂哄皯鍙瀺鍚堢殑鏍囧噯琛");
        }

        dataProcessTaskRepository.markFusionTaskRunning(ownerUsername, id);

        int fusionRows;
        try {
            stagingTableService.recreateFusionTable(targetTable);
            fusionRows = stagingTableService.mergeStandardTablesToTarget(ownerUsername, id, targetTable, standardTables);

            dataProcessTaskRepository.markFusionTaskCompleted(ownerUsername, id, fusionRows);
            fusionRunSuccessCounter.increment();
            persistGovernanceArtifacts(ownerUsername, "FUSION", id, targetTable, standardTables);
            recordAudit(ownerUsername, "RUN", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("targetTable", targetTable));
        } catch (RuntimeException ex) {
            dataProcessTaskRepository.markFusionTaskFailed(ownerUsername, id);
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

    @Transactional
    public void deleteFusionTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String targetTable = text(task.get("targetTable"));

        int affected = dataProcessTaskRepository.deleteFusionTask(ownerUsername, id);
        if (affected == 0) throw new IllegalArgumentException("铻嶅悎浠诲姟涓嶅瓨鍦");

        stagingTableService.dropFusionTargetTableIfUnused(targetTable);
        recordAudit(ownerUsername, "DELETE", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("targetTable", targetTable));
        invalidateDashboardCache(ownerUsername);
    }

    public Map<String, Object> cleanupOrphanGeneratedTables(String ownerUsername) {
        requireAuthenticated(ownerUsername);
        Set<String> referencedTables = new LinkedHashSet<>();

        List<String> standardTables = dataProcessTaskRepository.listAllStandardTables();
        for (String table : standardTables) {
            if (!isBlank(table)) {
                try {
                    referencedTables.add(sanitizeTableName(table));
                } catch (IllegalArgumentException ignore) {
                    // ignore illegal table names in historical dirty data
                }
            }
        }

        List<String> fusionTables = dataProcessTaskRepository.listAllFusionTargetTables();
        for (String table : fusionTables) {
            if (!isBlank(table)) {
                try {
                    referencedTables.add(sanitizeTableName(table));
                } catch (IllegalArgumentException ignore) {
                    // ignore illegal table names in historical dirty data
                }
            }
        }

        List<String> allTables = stagingTableService.listAllTables();
        List<String> droppedTables = new ArrayList<>();

        for (String table : allTables) {
            if (referencedTables.contains(table)) {
                continue;
            }
            if (stagingTableService.isGeneratedTableCandidate(table)) {
                stagingTableService.dropTableIfExists(table);
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

    @Transactional
    public Map<String, Object> runWorkflow(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        List<WorkflowDefinitionService.WorkflowNode> nodes = workflowDefinitionService.parseWorkflowNodes(payload);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("宸ヤ綔娴佽嚦灏戦渶瑕佷竴涓换鍔");
        }

        String tenantId = resolveTenantId(ownerUsername);
        String workflowName = text(payload.get("workflowName"));
        if (isBlank(workflowName)) {
            workflowName = "workflow-" + System.currentTimeMillis();
        }

        workflowDefinitionService.validateWorkflowNodes(nodes);

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
                for (WorkflowDefinitionService.WorkflowNode node : nodes) {
                    if (executed.contains(node.nodeId())) {
                        continue;
                    }
                    if (!executed.containsAll(node.dependsOn())) {
                        continue;
                    }

                    progressed = true;
                    String startedAt = now();
                    try {
                        Map<String, Object> result = executeWorkflowNode(ownerUsername, node);
                        if ("CLEAN".equals(node.taskType())) {
                            cleanResults.add(result);
                        } else {
                            fusionResults.add(result);
                        }
                        nodeResults.add(Map.of(
                            "nodeId", node.nodeId(),
                            "taskType", node.taskType(),
                            "taskId", node.taskId(),
                            "status", "COMPLETED",
                            "result", result
                        ));
                        jdbcTemplate.update(
                            "INSERT INTO etl_workflow_node_run_record(run_id,node_id,task_type,task_id,status,error_message,started_at,ended_at) VALUES(?,?,?,?, 'COMPLETED','',?,?)",
                            runId,
                            node.nodeId(),
                            node.taskType(),
                            node.taskId(),
                            startedAt,
                            now()
                        );
                        executed.add(node.nodeId());
                    } catch (RuntimeException ex) {
                        String reason = nvl(ex.getMessage());
                        nodeResults.add(Map.of(
                            "nodeId", node.nodeId(),
                            "taskType", node.taskType(),
                            "taskId", node.taskId(),
                            "status", "FAILED",
                            "reason", reason
                        ));
                        jdbcTemplate.update(
                            "INSERT INTO etl_workflow_node_run_record(run_id,node_id,task_type,task_id,status,error_message,started_at,ended_at) VALUES(?,?,?,?, 'FAILED',?,?,?)",
                            runId,
                            node.nodeId(),
                            node.taskType(),
                            node.taskId(),
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
                    throw new IllegalArgumentException("宸ヤ綔娴佸瓨鍦ㄥ惊鐜緷璧栨垨鏃犲彲鎵ц鑺傜偣");
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
        return governanceAuditService.listLineageRecords(ownerUsername, tenantId, taskType, taskId);
    }

    public List<Map<String, Object>> listQualityReports(String ownerUsername, String taskType, Long taskId) {
        String tenantId = resolveTenantId(ownerUsername);
        return governanceAuditService.listQualityReports(ownerUsername, tenantId, taskType, taskId);
    }

    public List<Map<String, Object>> listSnapshotRecords(String ownerUsername, String taskType, Long taskId) {
        String tenantId = resolveTenantId(ownerUsername);
        return governanceAuditService.listSnapshotRecords(ownerUsername, tenantId, taskType, taskId);
    }

    public List<Map<String, Object>> listAuditRecords(String ownerUsername, Integer limit) {
        String tenantId = resolveTenantId(ownerUsername);
        return governanceAuditService.listAuditRecords(ownerUsername, tenantId, limit);
    }

    private void invalidateDashboardCache(String ownerUsername) {
        dashboardService.invalidateOwnerCache(ownerUsername);
    }

    private Map<String, Object> getCleanTaskById(String ownerUsername, Long id) {
        return dataProcessTaskRepository.getCleanTaskById(ownerUsername, id);
    }

    private Map<String, Object> getFusionTaskById(String ownerUsername, Long id) {
        return dataProcessTaskRepository.getFusionTaskById(ownerUsername, id);
    }

    @SuppressWarnings("null")
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
        if (key == null) throw new IllegalStateException("鏂板澶辫触");
        return key.longValue();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON搴忓垪鍖栧け璐");
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

    private List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private static List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
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

    private void persistGovernanceArtifacts(String ownerUsername, String taskType, Long taskId, String targetTable, List<String> sourceTables) {
        String tenantId = resolveTenantId(ownerUsername);
        governanceAuditService.persistGovernanceArtifacts(tenantId, ownerUsername, taskType, taskId, targetTable, sourceTables);
    }

    private void recordAudit(String ownerUsername, String actionType, String resourceType, String resourceId, String resultStatus, Map<String, Object> detail) {
        String tenantId = resolveTenantId(ownerUsername);
        governanceAuditService.recordAudit(tenantId, ownerUsername, actionType, resourceType, resourceId, resultStatus, detail);
    }

    private void requireAuthenticated(String ownerUsername) {
        if (isBlank(ownerUsername) || "anonymous".equalsIgnoreCase(ownerUsername)) {
            throw new IllegalArgumentException("鏈璇佺敤鎴蜂笉鍏佽鎵ц鍐欐搷浣");
        }
    }


    private Map<String, Object> executeWorkflowNode(String ownerUsername, WorkflowDefinitionService.WorkflowNode node) {
        return switch (node.taskType()) {
            case "CLEAN" -> runCleanTask(ownerUsername, node.taskId());
            case "FUSION" -> runFusionTask(ownerUsername, node.taskId());
            default -> throw new IllegalArgumentException("涓嶆敮鎸佺殑鑺傜偣浠诲姟绫诲瀷: " + node.taskType());
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

    private String stagingTableRef(String tableName) {
        return stagingSchema + "." + sanitizeTableName(tableName);
    }

    private String sanitizeSchemaName(String schemaName) {
        String normalized = text(schemaName);
        if (!SAFE_SCHEMA_PATTERN.matcher(normalized).matches()) {
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

}

