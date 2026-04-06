package com.audit.data.service.orchestration;

import com.audit.data.repository.DataProcessTaskRepository;
import com.audit.data.service.DashboardService;
import com.audit.data.service.DataSourceService;
import com.audit.data.service.api.IDataProcessService;
import com.audit.data.service.domain.CleanConfigService;
import com.audit.data.service.domain.GovernanceAuditService;
import com.audit.data.service.domain.WorkflowDefinitionService;
import com.audit.data.service.infrastructure.NifiOrchestrationService;
import com.audit.data.service.infrastructure.StagingTableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 数据处理编排服务：协调清洗、融合、工作流执行及治理审计落库。
 */
public class DataProcessService implements IDataProcessService {

    private static final Logger log = LoggerFactory.getLogger(DataProcessService.class);

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
    private final WorkflowDefinitionService workflowDefinitionService;
    private final NifiOrchestrationService nifiOrchestrationService;
    private final MeterRegistry meterRegistry;
    private final String stagingSchema;
    private final long nifiRunningTimeoutSeconds;
    private final boolean nifiAutoReconcileEnabled;
    private final int nifiAutoReconcileOwnerLimit;
    private final int nifiAutoReconcileTaskLimit;
    private final String nifiNativeJdbcUrl;
    private final String nifiNativeJdbcUsername;
    private final String nifiNativeJdbcPassword;
    private final Counter cleanRunFailedCounter;
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
        WorkflowDefinitionService workflowDefinitionService,
        NifiOrchestrationService nifiOrchestrationService,
        MeterRegistry meterRegistry,
        @Value("${app.nifi.running-timeout-seconds:1800}") long nifiRunningTimeoutSeconds,
        @Value("${app.nifi.auto-reconcile.enabled:true}") boolean nifiAutoReconcileEnabled,
        @Value("${app.nifi.auto-reconcile.owner-limit:200}") int nifiAutoReconcileOwnerLimit,
        @Value("${app.nifi.auto-reconcile.task-limit:100}") int nifiAutoReconcileTaskLimit,
        @Value("${app.nifi.native.jdbc-url:jdbc:mysql://mysql:3306/agent_audit?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}") String nifiNativeJdbcUrl,
        @Value("${app.nifi.native.jdbc-username:root}") String nifiNativeJdbcUsername,
        @Value("${app.nifi.native.jdbc-password:password}") String nifiNativeJdbcPassword,
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
        this.workflowDefinitionService = workflowDefinitionService;
        this.nifiOrchestrationService = nifiOrchestrationService;
        this.meterRegistry = meterRegistry;
        this.nifiRunningTimeoutSeconds = Math.max(60L, nifiRunningTimeoutSeconds);
        this.nifiAutoReconcileEnabled = nifiAutoReconcileEnabled;
        this.nifiAutoReconcileOwnerLimit = Math.max(20, nifiAutoReconcileOwnerLimit);
        this.nifiAutoReconcileTaskLimit = Math.max(10, nifiAutoReconcileTaskLimit);
        this.nifiNativeJdbcUrl = text(nifiNativeJdbcUrl);
        this.nifiNativeJdbcUsername = text(nifiNativeJdbcUsername);
        this.nifiNativeJdbcPassword = text(nifiNativeJdbcPassword);
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
        this.cleanRunFailedCounter = Counter.builder("audit.process.clean.run.failed").register(meterRegistry);
        this.fusionRunFailedCounter = Counter.builder("audit.process.fusion.run.failed").register(meterRegistry);
    }

    @PostConstruct
    public void startupEtlSelfCheck() {
        try {
            Map<String, Object> status = nifiOrchestrationService.getStatus();
            boolean nifiEnabled = Boolean.TRUE.equals(status.get("enabled"));
            boolean nifiReachable = Boolean.TRUE.equals(status.get("reachable"));
            String statusMessage = text(status.get("message"));

            if (!nifiEnabled) {
                log.warn("ETL engine is NIFI but NiFi integration is disabled. Set APP_NIFI_ENABLED=true to dispatch ETL tasks to NiFi.");
                return;
            }

            int cleanTemplateCount = countEnabledTemplate("CLEAN");
            int fusionTemplateCount = countEnabledTemplate("FUSION");

            if (!nifiReachable) {
                log.warn("NiFi is not reachable at startup. statusMessage={} cleanTemplateCount={} fusionTemplateCount={}",
                    nvl(statusMessage), cleanTemplateCount, fusionTemplateCount);
                return;
            }

            if (cleanTemplateCount <= 0 || fusionTemplateCount <= 0) {
                log.warn("NiFi ETL self-check warning: missing enabled template. cleanTemplateCount={} fusionTemplateCount={}. Please configure /api/data/control-plane/nifi/templates.",
                    cleanTemplateCount, fusionTemplateCount);
                return;
            }

            log.info("NiFi ETL self-check passed. cleanTemplateCount={} fusionTemplateCount={}", cleanTemplateCount, fusionTemplateCount);
        } catch (RuntimeException ex) {
            log.warn("NiFi ETL self-check failed: {}", nvl(ex.getMessage()));
        }
    }

    private int countEnabledTemplate(String flowType) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM nifi_flow_template_record WHERE flow_type=? AND enabled=1",
            Integer.class,
            text(flowType).toUpperCase()
        );
        return count == null ? 0 : count;
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

    public List<Map<String, Object>> listFusionKeySynonyms(String ownerUsername) {
        return cleanConfigService.listFusionKeySynonyms(ownerUsername);
    }

    public Map<String, Object> createFusionKeySynonym(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> created = cleanConfigService.createFusionKeySynonym(ownerUsername, payload);
        recordAudit(
            ownerUsername,
            "CREATE",
            "FUSION_KEY_SYNONYM",
            String.valueOf(created.get("id")),
            "SUCCESS",
            Map.of("canonicalKey", String.valueOf(created.get("canonicalKey")))
        );
        return created;
    }

    public Map<String, Object> getFusionKeySynonymDetail(String ownerUsername, Long id) {
        return cleanConfigService.getFusionKeySynonymDetail(ownerUsername, id);
    }

    public Map<String, Object> updateFusionKeySynonym(String ownerUsername, Long id, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> updated = cleanConfigService.updateFusionKeySynonym(ownerUsername, id, payload);
        recordAudit(
            ownerUsername,
            "UPDATE",
            "FUSION_KEY_SYNONYM",
            String.valueOf(id),
            "SUCCESS",
            Map.of("canonicalKey", String.valueOf(updated.get("canonicalKey")))
        );
        return updated;
    }

    public Map<String, Object> toggleFusionKeySynonym(String ownerUsername, Long id, boolean enabled) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> toggled = cleanConfigService.toggleFusionKeySynonym(ownerUsername, id, enabled);
        recordAudit(
            ownerUsername,
            enabled ? "ENABLE" : "DISABLE",
            "FUSION_KEY_SYNONYM",
            String.valueOf(id),
            "SUCCESS",
            Map.of("enabled", enabled)
        );
        return toggled;
    }

    public void deleteFusionKeySynonym(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        cleanConfigService.deleteFusionKeySynonym(ownerUsername, id);
        recordAudit(ownerUsername, "DELETE", "FUSION_KEY_SYNONYM", String.valueOf(id), "SUCCESS", Map.of());
    }

    public List<Map<String, Object>> listFusionKeySynonymHistory(String ownerUsername, Long id, Integer limit) {
        return cleanConfigService.listFusionKeySynonymHistory(ownerUsername, id, limit);
    }

    public List<Map<String, Object>> listFusionKeySynonymHistoryByCanonicalKey(String ownerUsername, String canonicalKey, Integer limit) {
        return cleanConfigService.listFusionKeySynonymHistoryByCanonicalKey(ownerUsername, canonicalKey, limit);
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
            throw new IllegalArgumentException("清洗任务必填项缺失");
        }

        cleanConfigService.ensureDefaultCleanConfig(ownerUsername);
        Map<String, Object> strategy = cleanConfigService.getEnabledStrategy(ownerUsername, strategyCode);
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
    public Map<String, Object> updateCleanTask(String ownerUsername, Long id, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> existing = getCleanTaskById(ownerUsername, id);
        String currentStatus = text(existing.get("status")).toUpperCase();
        if ("RUNNING".equals(currentStatus) || "COMPLETED".equals(currentStatus)) {
            throw new IllegalArgumentException("仅待执行或失败任务允许编辑");
        }

        String taskName = text(payload.get("taskName"));
        String strategyCode = text(payload.get("strategy"));
        String standardTable = text(payload.get("standardTable"));
        String remark = text(payload.get("remark"));
        List<Map<String, Object>> cleanObjects = castMapList(payload.get("cleanObjects"));
        List<String> cleanRuleNames = castStringList(payload.get("cleanRuleNames"));

        if (isBlank(taskName) || isBlank(strategyCode) || cleanObjects.isEmpty()) {
            throw new IllegalArgumentException("清洗任务必填项缺失");
        }

        cleanConfigService.ensureDefaultCleanConfig(ownerUsername);
        Map<String, Object> strategy = cleanConfigService.getEnabledStrategy(ownerUsername, strategyCode);
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

        String outputTable = isBlank(standardTable)
            ? text(existing.get("standardTable"))
            : standardTable;

        int affected = dataProcessTaskRepository.updateCleanTask(
            ownerUsername,
            id,
            taskName,
            toJson(cleanObjects),
            toJson(objectNames),
            toJson(cleanRuleNames),
            strategyCode,
            text(strategy.get("name")),
            outputTable,
            remark
        );
        if (affected == 0) {
            throw new IllegalArgumentException("清洗任务不存在");
        }

        Map<String, Object> updated = getCleanTaskById(ownerUsername, id);
        recordAudit(ownerUsername, "UPDATE", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of("taskName", taskName));
        invalidateDashboardCache(ownerUsername);
        return updated;
    }

    @Transactional
    public Map<String, Object> runCleanTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> task = getCleanTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("当前任务状态不允许执行");
        }

        String outputTable = sanitizeTableName(String.valueOf(task.get("standardTable")));
        List<Map<String, Object>> cleanObjects = castMapList(task.get("cleanObjects"));
        String strategyCode = text(task.get("strategy"));
        List<String> ruleNames = castStringList(task.get("cleanRuleNames"));

        dataProcessTaskRepository.markCleanTaskRunning(ownerUsername, id);

        try {
            Map<String, Object> nifiResult = dispatchCleanTaskToNifi(ownerUsername, id, task, outputTable, strategyCode, cleanObjects, ruleNames);
            recordAudit(ownerUsername, "RUN", "CLEAN_TASK", String.valueOf(id), "SUCCESS", Map.of(
                "outputTable", outputTable,
                "executionMode", "NIFI",
                "nifiDispatch", nifiResult
            ));

            Map<String, Object> running = new LinkedHashMap<>(getCleanTaskById(ownerUsername, id));
            running.put("message", "NiFi 清洗流程已下发，正在执行中");
            running.put("dataReady", false);
            running.put("executionMode", "NIFI");
            invalidateDashboardCache(ownerUsername);
            return running;
        } catch (RuntimeException ex) {
            dataProcessTaskRepository.markCleanTaskFailed(ownerUsername, id);
            cleanRunFailedCounter.increment();
            recordAudit(ownerUsername, "RUN", "CLEAN_TASK", String.valueOf(id), "FAILED", Map.of("reason", nvl(ex.getMessage())));
            throw ex;
        }
    }

    public Map<String, Object> previewCleanTask(String ownerUsername, Long id, Integer limit) {
        Map<String, Object> task = getCleanTaskById(ownerUsername, id);
        ensureCleanArtifactsForCompletedTask(ownerUsername, task);
        String standardTable = sanitizeTableName(String.valueOf(task.get("standardTable")));
        String standardTableRef = stagingTableRef(standardTable);
        int safeLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 200);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,task_id,source_id,object_name,row_no,raw_json,normalized_json,created_at FROM " +
                    standardTableRef + " WHERE task_id=? ORDER BY row_no ASC LIMIT " + safeLimit,
                id
            );

            Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + standardTableRef + " WHERE task_id=?",
                Integer.class,
                id
            );

            return Map.of(
                "task", task,
                "standardTable", standardTable,
                "rows", rows,
                "size", rows.size(),
                "totalRows", total == null ? 0 : total,
                "previewLimit", safeLimit,
                "dataReady", true,
                "executionMode", "NIFI"
            );
        } catch (DataAccessException ex) {
            if (isMissingTableException(ex)) {
                return Map.of(
                    "task", task,
                    "standardTable", standardTable,
                    "rows", List.of(),
                    "size", 0,
                    "totalRows", 0,
                    "previewLimit", safeLimit,
                    "dataReady", false,
                    "executionMode", "NIFI",
                    "message", "NiFi 清洗流程已下发，目标标准表尚未落地，请稍后重试预览"
                );
            }
            throw ex;
        }
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
        if (affected == 0) throw new IllegalArgumentException("清洗任务不存在");

        try {
            stagingTableService.dropTableIfExists(buildRawTableName(standardTable));
        } catch (IllegalArgumentException ignore) {
            // Ignore historical dirty table names during cleanup.
        }
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
        Map<String, Object> fusionConfig = castMap(payload.get("fusionConfig"));

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

        Long id = dataProcessTaskRepository.insertFusionTask(
            ownerUsername,
            taskName,
            targetTable,
            toJson(cleanTaskIds),
            toJson(cleanTaskNames),
            toJson(standardTables),
            strategy,
            toJson(fusionConfig),
            remark
        );

        Map<String, Object> created = getFusionTaskById(ownerUsername, id);
        recordAudit(ownerUsername, "CREATE", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("taskName", taskName));
        invalidateDashboardCache(ownerUsername);
        return created;
    }

    @Transactional
    public Map<String, Object> updateFusionTask(String ownerUsername, Long id, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> existing = getFusionTaskById(ownerUsername, id);
        String currentStatus = text(existing.get("status")).toUpperCase();
        if ("RUNNING".equals(currentStatus) || "COMPLETED".equals(currentStatus)) {
            throw new IllegalArgumentException("仅待执行或失败任务允许编辑");
        }

        String taskName = text(payload.get("taskName"));
        String targetTable = text(payload.get("targetTable"));
        String strategy = text(payload.get("strategy"));
        String remark = text(payload.get("remark"));
        List<Long> cleanTaskIds = castLongList(payload.get("cleanTaskIds"));
        Map<String, Object> fusionConfig = castMap(payload.get("fusionConfig"));

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

        int affected = dataProcessTaskRepository.updateFusionTask(
            ownerUsername,
            id,
            taskName,
            targetTable,
            toJson(cleanTaskIds),
            toJson(cleanTaskNames),
            toJson(standardTables),
            strategy,
            toJson(fusionConfig),
            remark
        );
        if (affected == 0) {
            throw new IllegalArgumentException("融合任务不存在");
        }

        Map<String, Object> updated = getFusionTaskById(ownerUsername, id);
        recordAudit(ownerUsername, "UPDATE", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of("taskName", taskName));
        invalidateDashboardCache(ownerUsername);
        return updated;
    }

    @Transactional
    public Map<String, Object> runFusionTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String currentStatus = String.valueOf(task.get("status"));
        if (!READY_STATUSES.contains(currentStatus.toUpperCase())) {
            throw new IllegalArgumentException("当前任务状态不允许执行");
        }

        String targetTable = sanitizeTableName(String.valueOf(task.get("targetTable")));
        List<String> standardTables = castStringList(task.get("standardTables"));
        String strategy = text(task.get("strategy"));
        Map<String, Object> fusionConfig = castMap(task.get("fusionConfig"));
        if (standardTables.isEmpty()) {
            throw new IllegalArgumentException("缺少可融合的标准表");
        }

        dataProcessTaskRepository.markFusionTaskRunning(ownerUsername, id);

        try {
            Map<String, Object> nifiResult = dispatchFusionTaskToNifi(ownerUsername, id, task, targetTable, standardTables, strategy, fusionConfig);
            recordAudit(ownerUsername, "RUN", "FUSION_TASK", String.valueOf(id), "SUCCESS", Map.of(
                "targetTable", targetTable,
                "executionMode", "NIFI",
                "nifiDispatch", nifiResult
            ));

            Map<String, Object> running = new LinkedHashMap<>(getFusionTaskById(ownerUsername, id));
            running.put("message", "NiFi 融合流程已下发，正在执行中");
            running.put("dataReady", false);
            running.put("executionMode", "NIFI");
            invalidateDashboardCache(ownerUsername);
            return running;
        } catch (RuntimeException ex) {
            dataProcessTaskRepository.markFusionTaskFailed(ownerUsername, id);
            fusionRunFailedCounter.increment();
            recordAudit(ownerUsername, "RUN", "FUSION_TASK", String.valueOf(id), "FAILED", Map.of("reason", nvl(ex.getMessage())));
            throw ex;
        }
    }

    public Map<String, Object> previewFusionTask(String ownerUsername, Long id, Integer limit) {
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        ensureFusionArtifactsForCompletedTask(ownerUsername, task);
        String targetTable = sanitizeTableName(String.valueOf(task.get("targetTable")));
        String targetTableRef = stagingTableRef(targetTable);
        int safeLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 200);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + targetTableRef + " WHERE fusion_task_id=? ORDER BY row_no ASC LIMIT " + safeLimit,
                id
            );

            List<String> columns = rows.isEmpty()
                ? List.of()
                : new ArrayList<>(rows.get(0).keySet());

            return Map.of(
                "targetTable", targetTable,
                "columns", columns,
                "rows", rows,
                "size", rows.size(),
                "dataReady", true,
                "executionMode", "NIFI"
            );
        } catch (DataAccessException ex) {
            if (isMissingTableException(ex)) {
                return Map.of(
                    "targetTable", targetTable,
                    "columns", List.of(),
                    "rows", List.of(),
                    "size", 0,
                    "dataReady", false,
                    "executionMode", "NIFI",
                    "message", "NiFi 融合流程已下发，目标融合表尚未落地，请稍后重试预览"
                );
            }
            throw ex;
        }
    }

    @Transactional
    public void deleteFusionTask(String ownerUsername, Long id) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> task = getFusionTaskById(ownerUsername, id);
        String targetTable = text(task.get("targetTable"));

        int affected = dataProcessTaskRepository.deleteFusionTask(ownerUsername, id);
        if (affected == 0) throw new IllegalArgumentException("融合任务不存在");

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
            throw new IllegalArgumentException("工作流至少需要一个任务");
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

    public Map<String, Object> getNifiStatus(String ownerUsername) {
        return nifiOrchestrationService.getStatus();
    }

    public List<Map<String, Object>> listNifiFlowTemplates(String ownerUsername) {
        return jdbcTemplate.query(
            """
            SELECT id, flow_type, process_group_id, parameter_schema_json, version_no, enabled, remark, created_at, updated_at
              FROM nifi_flow_template_record
             WHERE owner_username=?
             ORDER BY updated_at DESC, id DESC
            """,
            (rs, i) -> Map.of(
                "id", rs.getLong("id"),
                "flowType", rs.getString("flow_type"),
                "processGroupId", rs.getString("process_group_id"),
                "parameterSchema", castMap(parseJson(rs.getString("parameter_schema_json"))),
                "versionNo", rs.getInt("version_no"),
                "enabled", rs.getBoolean("enabled"),
                "remark", nvl(rs.getString("remark")),
                "createdAt", formatDateTime(rs.getTimestamp("created_at")),
                "updatedAt", formatDateTime(rs.getTimestamp("updated_at"))
            ),
            ownerUsername
        );
    }

    @Transactional
    public Map<String, Object> saveNifiFlowTemplate(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        String flowType = text(payload.get("flowType")).toUpperCase();
        String processGroupId = text(payload.get("processGroupId"));
        Map<String, Object> parameterSchema = castMap(payload.get("parameterSchema"));
        boolean enabled = !Boolean.FALSE.equals(payload.get("enabled"));
        String remark = text(payload.get("remark"));

        if (isBlank(flowType)) {
            throw new IllegalArgumentException("flowType 不能为空");
        }
        if (isBlank(processGroupId)) {
            throw new IllegalArgumentException("processGroupId 不能为空");
        }

        String tenantId = resolveTenantId(ownerUsername);
        String now = now();

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id, version_no FROM nifi_flow_template_record WHERE owner_username=? AND flow_type=? LIMIT 1",
            ownerUsername,
            flowType
        );

        int versionNo = 1;
        Long templateId;
        if (existing.isEmpty()) {
            templateId = insertAndGetId(
                "INSERT INTO nifi_flow_template_record(tenant_id,owner_username,flow_type,process_group_id,parameter_schema_json,version_no,enabled,remark,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                tenantId,
                ownerUsername,
                flowType,
                processGroupId,
                toJson(parameterSchema),
                versionNo,
                enabled ? 1 : 0,
                remark,
                now,
                now
            );
        } else {
            Map<String, Object> row = existing.get(0);
            templateId = toLong(row.get("id"));
            Number oldVersion = (Number) row.get("version_no");
            versionNo = (oldVersion == null ? 0 : oldVersion.intValue()) + 1;
            jdbcTemplate.update(
                "UPDATE nifi_flow_template_record SET process_group_id=?, parameter_schema_json=?, version_no=?, enabled=?, remark=?, updated_at=? WHERE id=? AND owner_username=?",
                processGroupId,
                toJson(parameterSchema),
                versionNo,
                enabled ? 1 : 0,
                remark,
                now,
                templateId,
                ownerUsername
            );
        }

        recordAudit(
            ownerUsername,
            "UPSERT",
            "NIFI_TEMPLATE",
            String.valueOf(templateId),
            "SUCCESS",
            Map.of("flowType", flowType, "versionNo", versionNo, "enabled", enabled)
        );

        return Map.of(
            "id", templateId,
            "flowType", flowType,
            "processGroupId", processGroupId,
            "parameterSchema", parameterSchema,
            "versionNo", versionNo,
            "enabled", enabled,
            "remark", remark,
            "updatedAt", now
        );
    }

    @Transactional
    public Map<String, Object> bootstrapNifiEtlTemplates(String ownerUsername) {
        requireAuthenticated(ownerUsername);

        Map<String, Object> status = nifiOrchestrationService.getStatus();
        if (!Boolean.TRUE.equals(status.get("enabled"))) {
            throw new IllegalStateException("NiFi integration is disabled, cannot bootstrap templates");
        }

        Map<String, Object> cleanBlueprint = buildDefaultNifiBlueprint("CLEAN", ownerUsername);
        Map<String, Object> fusionBlueprint = buildDefaultNifiBlueprint("FUSION", ownerUsername);

        Map<String, Object> cleanProvision = nifiOrchestrationService.provisionFlowBlueprint(cleanBlueprint);
        Map<String, Object> fusionProvision = nifiOrchestrationService.provisionFlowBlueprint(fusionBlueprint);

        String cleanPgId = text(cleanProvision.get("processGroupId"));
        String fusionPgId = text(fusionProvision.get("processGroupId"));

        Map<String, Object> cleanTemplate = saveNifiFlowTemplate(ownerUsername, Map.of(
            "flowType", "CLEAN",
            "processGroupId", cleanPgId,
            "parameterSchema", Map.of("requiredKeys", List.of("ownerUsername", "taskId")),
            "enabled", true,
            "remark", "Auto bootstrap by system"
        ));

        Map<String, Object> fusionTemplate = saveNifiFlowTemplate(ownerUsername, Map.of(
            "flowType", "FUSION",
            "processGroupId", fusionPgId,
            "parameterSchema", Map.of("requiredKeys", List.of("ownerUsername", "taskId")),
            "enabled", true,
            "remark", "Auto bootstrap by system"
        ));

        return Map.of(
            "status", "BOOTSTRAPPED",
            "clean", cleanTemplate,
            "fusion", fusionTemplate,
            "provision", Map.of(
                "clean", cleanProvision,
                "fusion", fusionProvision
            )
        );
    }

    @Transactional
    public Map<String, Object> provisionNifiFlowBlueprint(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        String preset = text(safePayload.get("preset")).toUpperCase();
        String groupName = text(safePayload.get("groupName"));
        String flowType = text(safePayload.get("flowType")).toUpperCase();

        Map<String, Object> blueprint = new LinkedHashMap<>(safePayload);
        if (("CLEAN".equals(preset) || "FUSION".equals(preset)) && isEmptyBlueprint(blueprint)) {
            mergeBlueprintDefaults(blueprint, buildDefaultNifiBlueprint(preset, ownerUsername));
        } else if (("CLEAN".equals(flowType) || "FUSION".equals(flowType)) && isEmptyBlueprint(blueprint)) {
            mergeBlueprintDefaults(blueprint, buildDefaultNifiBlueprint(flowType, ownerUsername));
        }

        if (groupName.isBlank()) {
            if ("CLEAN".equals(flowType) || "FUSION".equals(flowType)) {
                groupName = "AUDIT_" + flowType;
            } else if ("CLEAN".equals(preset) || "FUSION".equals(preset)) {
                groupName = "AUDIT_" + preset;
            } else {
                groupName = "AUDIT_FLOW_" + System.currentTimeMillis();
            }
        }

        blueprint.put("groupName", groupName);
        Map<String, Object> result = nifiOrchestrationService.provisionFlowBlueprint(blueprint);
        recordAudit(ownerUsername, "CREATE", "NIFI_FLOW_BLUEPRINT", groupName, "SUCCESS", result);
        return result;
    }

    private Map<String, Object> buildDefaultNifiBlueprint(String flowType, String ownerUsername) {
        String normalizedFlowType = text(flowType).toUpperCase();
        String groupName = "AUDIT_" + normalizedFlowType;
        if ("CLEAN".equals(normalizedFlowType)) {
            return buildCleanNativeBlueprint(groupName, ownerUsername);
        }
        if ("FUSION".equals(normalizedFlowType)) {
            return buildFusionNativeBlueprint(groupName, ownerUsername);
        }

        List<Map<String, Object>> processors = new ArrayList<>();
        processors.add(new LinkedHashMap<>(Map.of(
            "name", normalizedFlowType + " Native Trigger",
            "type", "org.apache.nifi.processors.standard.GenerateFlowFile",
            "x", 0.0,
            "y", 0.0,
            "properties", Map.of(
                "Data Format", "Text",
                "Batch Size", "1",
                "Custom Text", normalizedFlowType + " poll",
                "Unique FlowFiles", "true"
            ),
            "schedulingStrategy", "TIMER_DRIVEN",
            "schedulingPeriod", "5 sec",
            "autoTerminatedRelationships", List.of("success")
        )));

        List<Map<String, Object>> connections = List.of();

        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("flowType", normalizedFlowType);
        blueprint.put("groupName", groupName);
        blueprint.put("controllerServices", List.of());
        blueprint.put("processors", processors);
        blueprint.put("connections", connections);
        blueprint.put("startAfterCreate", true);
        return blueprint;
    }

    private Map<String, Object> buildCleanNativeBlueprint(String groupName, String ownerUsername) {
        List<Map<String, Object>> processors = new ArrayList<>();
        processors.add(new LinkedHashMap<>(Map.of(
            "name", "CLEAN Native Poll Trigger",
            "type", "org.apache.nifi.processors.standard.GenerateFlowFile",
            "x", 0.0,
            "y", 0.0,
            "properties", Map.of(
                "Data Format", "Text",
                "Batch Size", "1",
                "Custom Text", "CLEAN native poll",
                "Unique FlowFiles", "true"
            ),
            "schedulingStrategy", "TIMER_DRIVEN",
            "schedulingPeriod", "5 sec"
        )));

        Map<String, Object> scriptProperties = new LinkedHashMap<>();
        scriptProperties.put("Script Engine", "Groovy");
        scriptProperties.put("Script Body", buildCleanNativeGroovyScript());
        scriptProperties.put("db.url", nifiNativeJdbcUrl);
        scriptProperties.put("db.user", nifiNativeJdbcUsername);
        scriptProperties.put("db.password", nifiNativeJdbcPassword);
        scriptProperties.put("db.stagingSchema", stagingSchema);
        scriptProperties.put("owner.username", isBlank(ownerUsername) ? "system" : ownerUsername);

        processors.add(new LinkedHashMap<>(Map.of(
            "name", "CLEAN Native ExecuteScript",
            "type", "org.apache.nifi.processors.script.ExecuteScript",
            "x", 360.0,
            "y", 0.0,
            "properties", scriptProperties,
            "autoTerminatedRelationships", List.of("success", "failure")
        )));

        List<Map<String, Object>> connections = List.of(
            new LinkedHashMap<>(Map.of(
                "name", "CLEAN Native Trigger -> Script",
                "source", "CLEAN Native Poll Trigger",
                "destination", "CLEAN Native ExecuteScript",
                "selectedRelationships", List.of("success")
            ))
        );

        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("flowType", "CLEAN");
        blueprint.put("groupName", groupName);
        blueprint.put("controllerServices", List.of());
        blueprint.put("processors", processors);
        blueprint.put("connections", connections);
        blueprint.put("startAfterCreate", true);
        return blueprint;
    }

    private Map<String, Object> buildFusionNativeBlueprint(String groupName, String ownerUsername) {
        List<Map<String, Object>> processors = new ArrayList<>();
        processors.add(new LinkedHashMap<>(Map.of(
            "name", "FUSION Native Poll Trigger",
            "type", "org.apache.nifi.processors.standard.GenerateFlowFile",
            "x", 0.0,
            "y", 0.0,
            "properties", Map.of(
                "Data Format", "Text",
                "Batch Size", "1",
                "Custom Text", "FUSION native poll",
                "Unique FlowFiles", "true"
            ),
            "schedulingStrategy", "TIMER_DRIVEN",
            "schedulingPeriod", "5 sec"
        )));

        Map<String, Object> scriptProperties = new LinkedHashMap<>();
        scriptProperties.put("Script Engine", "Groovy");
        scriptProperties.put("Script Body", buildFusionNativeGroovyScript());
        scriptProperties.put("db.url", nifiNativeJdbcUrl);
        scriptProperties.put("db.user", nifiNativeJdbcUsername);
        scriptProperties.put("db.password", nifiNativeJdbcPassword);
        scriptProperties.put("db.stagingSchema", stagingSchema);
        scriptProperties.put("owner.username", isBlank(ownerUsername) ? "system" : ownerUsername);

        processors.add(new LinkedHashMap<>(Map.of(
            "name", "FUSION Native ExecuteScript",
            "type", "org.apache.nifi.processors.script.ExecuteScript",
            "x", 360.0,
            "y", 0.0,
            "properties", scriptProperties,
            "autoTerminatedRelationships", List.of("success", "failure")
        )));

        List<Map<String, Object>> connections = List.of(
            new LinkedHashMap<>(Map.of(
                "name", "FUSION Native Trigger -> Script",
                "source", "FUSION Native Poll Trigger",
                "destination", "FUSION Native ExecuteScript",
                "selectedRelationships", List.of("success")
            ))
        );

        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("flowType", "FUSION");
        blueprint.put("groupName", groupName);
        blueprint.put("controllerServices", List.of());
        blueprint.put("processors", processors);
        blueprint.put("connections", connections);
        blueprint.put("startAfterCreate", true);
        return blueprint;
    }

    private String buildFusionNativeGroovyScript() {
        return """
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

def ff = session.get()
if (ff == null) {
    return
}

def owner = context.getProperty('owner.username').evaluateAttributeExpressions(ff).value
def jdbcUrl = context.getProperty('db.url').evaluateAttributeExpressions(ff).value
def jdbcUser = context.getProperty('db.user').evaluateAttributeExpressions(ff).value
def jdbcPassword = context.getProperty('db.password').evaluateAttributeExpressions(ff).value
def stagingSchema = context.getProperty('db.stagingSchema').evaluateAttributeExpressions(ff).value

def slurper = new JsonSlurper()
Long taskId = null
def conn = null
try {
    Class.forName('com.mysql.cj.jdbc.Driver')
    conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)
    conn.autoCommit = false

    def canonical = { v ->
        if (v == null) return ''
        return String.valueOf(v).trim().toLowerCase().replace('_', '').replace(' ', '')
    }

    def normalizeMatchValue = { v ->
        if (v == null) return ''
        def s = String.valueOf(v).trim()
        if (!s) return ''
        return s.toLowerCase()
    }

    def parseInstantValue = { Object value ->
        if (value == null) return null
        def raw = String.valueOf(value).trim()
        if (!raw) return null
        try { return Instant.parse(raw) } catch (ignored1) {}
        try { return LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant() } catch (ignored2) {}
        try { return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')).atZone(ZoneId.systemDefault()).toInstant() } catch (ignored3) {}
        try {
            long n = Long.parseLong(raw)
            if (raw.length() >= 13) {
                return Instant.ofEpochMilli(n)
            }
            return Instant.ofEpochSecond(n)
        } catch (ignored4) {}
        return null
    }

    def findFieldValue = { Map row, String keyName ->
        if (row == null || keyName == null) return null
        if (row.containsKey(keyName)) return row[keyName]
        def target = canonical(keyName)
        if (!target) return null
        def found = row.find { e -> canonical(e.key) == target || canonical(e.key).endsWith(target) }
        return found == null ? null : found.value
    }

    def taskSql = 'SELECT id,target_table,standard_tables_json,fusion_config_json,strategy FROM fusion_task_record WHERE owner_username=? AND status=\'RUNNING\' ORDER BY updated_at ASC,id ASC LIMIT 1 FOR UPDATE'
    def ps = conn.prepareStatement(taskSql)
    ps.setString(1, owner)
    def rs = ps.executeQuery()
    if (!rs.next()) {
        conn.commit()
        session.transfer(ff, REL_SUCCESS)
        return
    }

    taskId = rs.getLong('id')
    def targetTable = String.valueOf(rs.getString('target_table'))
    def tables = slurper.parseText(String.valueOf(rs.getString('standard_tables_json')))
    def strategy = String.valueOf(rs.getString('strategy') ?: 'KEY_ALIGN').trim().toUpperCase()
    if (!strategy) strategy = 'KEY_ALIGN'

    def fusionConfigText = rs.getString('fusion_config_json')
    def fusionConfig = [:]
    if (fusionConfigText != null && fusionConfigText.trim()) {
        fusionConfig = slurper.parseText(fusionConfigText)
    }

    boolean fillMissingSourceRows = Boolean.TRUE.equals(fusionConfig.get('fillMissingSourceRows')) || 'true'.equalsIgnoreCase(String.valueOf(fusionConfig.get('fillMissingSourceRows') ?: ''))
    boolean loosePrimaryFallback = Boolean.TRUE.equals(fusionConfig.get('loosePrimaryFallback')) || 'true'.equalsIgnoreCase(String.valueOf(fusionConfig.get('loosePrimaryFallback') ?: ''))

    def keyExpr = String.valueOf(fusionConfig.get('keyField') ?: '')
    if ('RULE_MATCH'.equals(strategy) && fusionConfig.get('matchFields') instanceof List) {
        def fs = (fusionConfig.get('matchFields') as List).collect { String.valueOf(it).trim() }.findAll { it }
        if (!fs.isEmpty()) {
            keyExpr = fs.join('+')
        }
    }
    def keyParts = keyExpr.split(/[+,|]/).collect { it.trim() }.findAll { it }.toList()
    if (keyParts.isEmpty()) {
        keyParts = ['单位ID']
    }

    def timeField = String.valueOf(fusionConfig.get('timeField') ?: '')
    if ('TIME_WINDOW'.equals(strategy) && !timeField.trim().isEmpty()) {
        keyParts = [timeField.trim()]
    }
    int windowMinutes = 60
    try {
        def w = fusionConfig.get('windowMinutes')
        if (w != null) {
            windowMinutes = Math.max(1, Integer.parseInt(String.valueOf(w)))
        }
    } catch (ignored5) {
    }
    long windowSeconds = windowMinutes * 60L
    String primaryKeyField = keyParts.isEmpty() ? '单位ID' : keyParts.get(0)

    if (!(targetTable ==~ /[A-Za-z0-9_]+/)) {
        throw new RuntimeException('invalid target table: ' + targetTable)
    }

    conn.createStatement().execute('DROP TABLE IF EXISTS ' + stagingSchema + '.' + targetTable)
    conn.createStatement().execute('''
        CREATE TABLE ''' + stagingSchema + '.' + targetTable + ''' (
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
        )''')

    def buckets = new LinkedHashMap<String, Map>()
    tables.each { table ->
        def t = String.valueOf(table)
        if (!(t ==~ /[A-Za-z0-9_]+/)) {
            return
        }
        def rows = conn.createStatement().executeQuery('SELECT source_id,object_name,row_no,raw_json,normalized_json FROM ' + stagingSchema + '.' + t + ' ORDER BY row_no ASC')
        while (rows.next()) {
            def normalized = slurper.parseText(String.valueOf(rows.getString('normalized_json')))

            def keyValues = keyParts.collect { keyName ->
                def v = findFieldValue(normalized as Map, keyName)
                return v == null ? '' : String.valueOf(v).trim()
            }

            String matchKey = ''
            if ('APPEND'.equals(strategy)) {
                matchKey = '__row__' + t + '#' + rows.getLong('source_id') + '#' + rows.getInt('row_no')
            } else if ('TIME_WINDOW'.equals(strategy)) {
                def tField = !timeField.trim().isEmpty() ? timeField : keyParts[0]
                def tVal = findFieldValue(normalized as Map, tField)
                def instant = parseInstantValue(tVal)
                def bizField = String.valueOf(fusionConfig.get('businessKey') ?: '')
                if (!bizField.trim().isEmpty()) {
                    bizField = bizField.trim()
                } else {
                    bizField = keyParts.find { !it.equalsIgnoreCase(tField) } ?: ''
                }
                def bizVal = bizField ? findFieldValue(normalized as Map, bizField) : null
                if (instant != null) {
                    long bucketNo = Math.floorDiv(instant.getEpochSecond(), windowSeconds)
                    def bizNorm = normalizeMatchValue(bizVal)
                    matchKey = '__time__' + (bizNorm ? bizNorm + '|' : '') + bucketNo
                }
            } else {
                def normalizedParts = keyValues.collect { normalizeMatchValue(it) }
                if (normalizedParts.every { it }) {
                    matchKey = '__key__' + normalizedParts.join('|')
                } else if (loosePrimaryFallback && normalizedParts.size() > 1 && normalizedParts[0]) {
                    matchKey = '__fallback__' + normalizedParts[0]
                }
            }

            if (!matchKey) {
                matchKey = '__row__' + t + '#' + rows.getLong('source_id') + '#' + rows.getInt('row_no')
            }

            def bucket = buckets.computeIfAbsent(matchKey) {
                [
                    entries: new LinkedHashMap<String, List>(),
                    key: keyValues.join('|'),
                    strategy: strategy,
                    keyParts: keyParts,
                    primaryKey: primaryKeyField,
                    fallback: matchKey.startsWith('__fallback__')
                ]
            }
            def arr = bucket.entries.computeIfAbsent(t) { [] }
            arr << [
                table: t,
                sourceId: rows.getLong('source_id'),
                objectName: String.valueOf(rows.getString('object_name')),
                rowNo: rows.getInt('row_no'),
                raw: slurper.parseText(String.valueOf(rows.getString('raw_json'))),
                normalized: normalized
            ]
        }
    }

    def ins = conn.prepareStatement('INSERT INTO ' + stagingSchema + '.' + targetTable + '(fusion_task_id,clean_task_id,source_id,object_name,row_no,raw_json,normalized_json,source_standard_table,created_at) VALUES(?,?,?,?,?,?,?,?,NOW())')
    int outRows = 0
    int rowNo = 1
    buckets.values().each { bucket ->
        def byTable = bucket.entries as Map<String, List>
        int maxLen = byTable.values().collect { it.size() }.max() ?: 0
        for (int i = 0; i < maxLen; i++) {
            def merged = [:]
            def raw = []
            def src = new LinkedHashSet<String>()
            def parts = (bucket.key ?: '').split('\\|', -1)
            keyParts.eachWithIndex { kp, idx ->
                def part = parts.size() > idx ? parts[idx] : ''
                if (part) merged[kp] = part
            }
            byTable.each { table, entries ->
                if (i >= entries.size()) return
                def e = entries[i]
                e.normalized.each { k, v ->
                    if (!keyParts.any { canonical(it) == canonical(k) }) {
                        merged[table + '__' + String.valueOf(k)] = v
                    }
                }
                raw << [table: e.table, sourceId: e.sourceId, objectName: e.objectName, rowNo: e.rowNo, raw: e.raw]
                src << table
            }

            if (fillMissingSourceRows) {
                tables.each { tb ->
                    def tableName = String.valueOf(tb)
                    if (!src.contains(tableName)) {
                        raw << [
                            table: tableName,
                            sourceId: 0L,
                            objectName: 'MISSING_PLACEHOLDER',
                            rowNo: null,
                            raw: [_missing: true, _reason: 'NO_MATCHED_SOURCE_ROW', _sourceTable: tableName]
                        ]
                        src << tableName
                    }
                }
            }

            ins.setLong(1, taskId)
            ins.setLong(2, 0L)
            ins.setLong(3, 0L)
            ins.setString(4, 'MERGED')
            ins.setInt(5, rowNo++)
            ins.setString(6, JsonOutput.toJson(raw))
            ins.setString(7, JsonOutput.toJson(merged))
            ins.setString(8, (src as List).join(','))
            ins.addBatch()
            outRows++
        }
    }
    ins.executeBatch()

    def up = conn.prepareStatement('UPDATE fusion_task_record SET status=\'COMPLETED\', fusion_rows=?, updated_at=NOW() WHERE id=?')
    up.setInt(1, outRows)
    up.setLong(2, taskId)
    up.executeUpdate()
    conn.commit()
    session.transfer(ff, REL_SUCCESS)
} catch (Exception ex) {
    if (conn != null) {
        try { conn.rollback() } catch (ignored) {}
        if (taskId != null) {
            try {
                def fail = conn.prepareStatement('UPDATE fusion_task_record SET status=\'FAILED\', updated_at=NOW() WHERE id=?')
                fail.setLong(1, taskId)
                fail.executeUpdate()
                conn.commit()
            } catch (ignored2) {}
        }
    }
    log.error('FUSION native script failed: ' + ex.getMessage(), ex)
    session.transfer(ff, REL_FAILURE)
} finally {
    if (conn != null) {
        try { conn.close() } catch (ignored3) {}
    }
}
""";
    }

    private String buildCleanNativeGroovyScript() {
        return """
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.FileInputStream
import java.util.regex.Pattern
import java.sql.DriverManager
import org.apache.poi.ss.usermodel.WorkbookFactory

def ff = session.get()
if (ff == null) {
    return
}

def owner = context.getProperty('owner.username').evaluateAttributeExpressions(ff).value
def jdbcUrl = context.getProperty('db.url').evaluateAttributeExpressions(ff).value
def jdbcUser = context.getProperty('db.user').evaluateAttributeExpressions(ff).value
def jdbcPassword = context.getProperty('db.password').evaluateAttributeExpressions(ff).value
def stagingSchema = context.getProperty('db.stagingSchema').evaluateAttributeExpressions(ff).value
def slurper = new JsonSlurper()

def normalizeValue = { v ->
    if (v == null) return 'UNKNOWN'
    def s = String.valueOf(v).trim()
    return s ? s : 'UNKNOWN'
}

def parseActionMap = { Map map ->
    def t = String.valueOf(map.get('type') ?: '').trim().toLowerCase()
    if (!t) return null
    def f = String.valueOf(map.get('field') ?: '*').trim()
    if (!f) f = '*'
    return [
        type: t,
        field: f,
        value: String.valueOf(map.get('value') ?: ''),
        from: String.valueOf(map.get('from') ?: ''),
        to: String.valueOf(map.get('to') ?: '')
    ]
}

def parseRuleActions = { String content ->
    if (content == null || content.trim().isEmpty()) return []
    def trimmed = content.trim()
    def actions = []

    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
        try {
            def parsed = slurper.parseText(trimmed)
            if (parsed instanceof Map) {
                def fromActions = parsed.actions
                if (fromActions instanceof List) {
                    fromActions.each { it ->
                        if (it instanceof Map) {
                            def a = parseActionMap(it as Map)
                            if (a != null) actions << a
                        }
                    }
                } else {
                    def a = parseActionMap(parsed as Map)
                    if (a != null) actions << a
                }
            } else if (parsed instanceof List) {
                parsed.each { it ->
                    if (it instanceof Map) {
                        def a = parseActionMap(it as Map)
                        if (a != null) actions << a
                    }
                }
            }
        } catch (ignored) {
        }
    }

    if (!actions.isEmpty()) return actions

    trimmed.split(/\r?\n/).each { rawLine ->
        def line = String.valueOf(rawLine).trim()
        if (!line || line.startsWith('#')) return
        def parts = line.split('\\|', -1)
        def type = parts.length > 0 ? parts[0].trim().toLowerCase() : ''
        if (!type) return
        def field = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : '*'
        def value = parts.length > 2 ? parts[2].trim() : ''
        def from = parts.length > 2 ? parts[2].trim() : ''
        def to = parts.length > 3 ? parts[3].trim() : ''
        actions << [type: type, field: field, value: value, from: from, to: to]
    }
    return actions
}

def applyActionToField = { Map obj, String field, Map action ->
    def current = obj.get(field)
    def currentText = current == null ? '' : String.valueOf(current)
    switch (String.valueOf(action.type)) {
        case 'fill_null':
            if (current == null || currentText.trim().isEmpty()) {
                obj.put(field, (action.value == null || String.valueOf(action.value).trim().isEmpty()) ? 'UNKNOWN' : String.valueOf(action.value))
                return true
            }
            return false
        case 'trim':
            if (current == null) return false
            def trimmed = currentText.trim()
            if (trimmed != currentText) {
                obj.put(field, trimmed)
                return true
            }
            return false
        case 'lowercase':
            if (current == null) return false
            def lowered = currentText.toLowerCase()
            if (lowered != currentText) {
                obj.put(field, lowered)
                return true
            }
            return false
        case 'uppercase':
            if (current == null) return false
            def uppered = currentText.toUpperCase()
            if (uppered != currentText) {
                obj.put(field, uppered)
                return true
            }
            return false
        case 'replace':
            def from = String.valueOf(action.from ?: '')
            if (current == null || from.isEmpty()) return false
            def replaced = currentText.replace(from, String.valueOf(action.to ?: ''))
            if (replaced != currentText) {
                obj.put(field, replaced)
                return true
            }
            return false
    }
    return false
}

def applyRuleAction = { Map obj, Map action ->
    def field = String.valueOf(action.field ?: '*').trim()
    if (!field) field = '*'
    def type = String.valueOf(action.type ?: '')

    if (type == 'remove_field') {
        if (field == '*') return false
        return obj.remove(field) != null
    }

    if (field == '*') {
        boolean changed = false
        new ArrayList(obj.keySet()).each { key ->
            changed = applyActionToField(obj, String.valueOf(key), action) || changed
        }
        return changed
    }
    return applyActionToField(obj, field, action)
}

def readFileRows = { String path, String objectName ->
    def lowerName = (objectName ?: path).toLowerCase()
    def rows = []
    if (lowerName.endsWith('.xlsx') || lowerName.endsWith('.xls')) {
        def fis = new FileInputStream(path)
        def wb = WorkbookFactory.create(fis)
        try {
            def sheet = wb.getSheetAt(0)
            def headerRow = sheet.getRow(sheet.getFirstRowNum())
            def headers = []
            headerRow.cellIterator().each { c -> headers << String.valueOf(c.toString()).trim() }
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                def row = sheet.getRow(i)
                if (row == null) continue
                def map = [:]
                boolean any = false
                for (int j = 0; j < headers.size(); j++) {
                    def cell = row.getCell(j)
                    def value = cell == null ? '' : String.valueOf(cell.toString())
                    map[headers[j]] = normalizeValue(value)
                    if (String.valueOf(value).trim()) any = true
                }
                if (any) rows << map
            }
        } finally {
            wb.close(); fis.close()
        }
        return rows
    }

    if (lowerName.endsWith('.csv') || lowerName.endsWith('.txt') || lowerName.endsWith('.pipe')) {
        def file = new File(path)
        if (!file.exists()) {
            return rows
        }
        def lines = file.readLines('UTF-8')
        if (lines.isEmpty()) {
            return rows
        }
        def first = String.valueOf(lines[0])
        def delimiter = lowerName.endsWith('.pipe') ? '|' : (first.contains('|') ? '|' : ',')
        def headers = first.split(Pattern.quote(delimiter), -1).collect { String.valueOf(it).trim() }
        for (int i = 1; i < lines.size(); i++) {
            def line = String.valueOf(lines[i])
            if (!line.trim()) continue
            def values = line.split(Pattern.quote(delimiter), -1)
            def map = [:]
            boolean any = false
            for (int j = 0; j < headers.size(); j++) {
                def v = j < values.length ? String.valueOf(values[j]) : ''
                map[headers[j]] = normalizeValue(v)
                if (v.trim()) any = true
            }
            if (any) rows << map
        }
        return rows
    }

    def file = new File(path)
    if (!file.exists()) {
        return rows
    }

    if (lowerName.endsWith('.json')) {
        def parsed = slurper.parse(file)
        if (parsed instanceof List) {
            parsed.each { obj ->
                if (obj instanceof Map) {
                    def map = [:]
                    obj.each { k, v -> map[String.valueOf(k)] = normalizeValue(v) }
                    rows << map
                }
            }
        } else if (parsed instanceof Map) {
            def map = [:]
            parsed.each { k, v -> map[String.valueOf(k)] = normalizeValue(v) }
            rows << map
        }
        return rows
    }

    file.eachLine('UTF-8') { line ->
        if (!line?.trim()) return
        if (lowerName.endsWith('.jsonl')) {
            def obj = slurper.parseText(line)
            def map = [:]
            obj.each { k, v -> map[String.valueOf(k)] = normalizeValue(v) }
            rows << map
        }
    }
    return rows
}

Long taskId = null
def conn = null
try {
    Class.forName('com.mysql.cj.jdbc.Driver')
    conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)
    conn.autoCommit = false

    def ps = conn.prepareStatement('SELECT id,standard_table,clean_objects_json,strategy_code,clean_rule_names_json FROM clean_task_record WHERE owner_username=? AND status=\'RUNNING\' ORDER BY updated_at ASC,id ASC LIMIT 1 FOR UPDATE')
    ps.setString(1, owner)
    def rs = ps.executeQuery()
    if (!rs.next()) {
        conn.commit()
        session.transfer(ff, REL_SUCCESS)
        return
    }
    taskId = rs.getLong('id')
    def standardTable = String.valueOf(rs.getString('standard_table'))
    def rawTable = standardTable.replaceFirst('^clean_std_', 'clean_raw_')
    def cleanObjects = slurper.parseText(String.valueOf(rs.getString('clean_objects_json')))
    def strategyCode = String.valueOf(rs.getString('strategy_code') ?: '').toUpperCase()
    def cleanRuleNamesRaw = String.valueOf(rs.getString('clean_rule_names_json') ?: '[]')
    def cleanRuleNames = []
    try {
        def parsedNames = slurper.parseText(cleanRuleNamesRaw)
        if (parsedNames instanceof List) {
            parsedNames.each { n ->
                def rn = String.valueOf(n ?: '').trim()
                if (rn) cleanRuleNames << rn
            }
        }
    } catch (ignored) {
    }

    def actions = []
    if (!cleanRuleNames.isEmpty()) {
        cleanRuleNames.each { rn ->
            def rp = conn.prepareStatement('SELECT content FROM clean_rule_record WHERE owner_username=? AND enabled=1 AND name=? ORDER BY id ASC LIMIT 1')
            rp.setString(1, owner)
            rp.setString(2, String.valueOf(rn))
            def rr = rp.executeQuery()
            if (rr.next()) {
                actions.addAll(parseRuleActions(String.valueOf(rr.getString('content') ?: '')))
            }
        }
    }
    if (cleanRuleNames.any { String.valueOf(it).contains('空值') } && !actions.any { String.valueOf(it.type) == 'fill_null' }) {
        actions << [type: 'fill_null', field: '*', value: 'UNKNOWN', from: '', to: '']
    }

    boolean enableStandard = strategyCode.contains('STANDARD')
    boolean enableOutlier = strategyCode.contains('OUTLIER')
    boolean enableDedup = strategyCode.contains('DEDUP')
    def dedupSet = new LinkedHashSet<String>()

    if (!(standardTable ==~ /[A-Za-z0-9_]+/) || !(rawTable ==~ /[A-Za-z0-9_]+/)) {
        throw new RuntimeException('invalid table name')
    }

    conn.createStatement().execute('DROP TABLE IF EXISTS ' + stagingSchema + '.' + rawTable)
    conn.createStatement().execute('''
        CREATE TABLE ''' + stagingSchema + '.' + rawTable + ''' (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            task_id BIGINT NOT NULL,
            source_id BIGINT NOT NULL,
            object_name VARCHAR(255) NOT NULL,
            row_no INT NOT NULL,
            raw_json LONGTEXT NOT NULL,
            created_at DATETIME NOT NULL
        )''')

    conn.createStatement().execute('DROP TABLE IF EXISTS ' + stagingSchema + '.' + standardTable)
    conn.createStatement().execute('''
        CREATE TABLE ''' + stagingSchema + '.' + standardTable + ''' (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            task_id BIGINT NOT NULL,
            source_id BIGINT NOT NULL,
            object_name VARCHAR(255) NOT NULL,
            row_no INT NOT NULL,
            raw_json LONGTEXT NOT NULL,
            normalized_json LONGTEXT NOT NULL,
            created_at DATETIME NOT NULL
        )''')

    def insRaw = conn.prepareStatement('INSERT INTO ' + stagingSchema + '.' + rawTable + '(task_id,source_id,object_name,row_no,raw_json,created_at) VALUES(?,?,?,?,?,NOW())')
    def insStd = conn.prepareStatement('INSERT INTO ' + stagingSchema + '.' + standardTable + '(task_id,source_id,object_name,row_no,raw_json,normalized_json,created_at) VALUES(?,?,?,?,?,?,NOW())')

    int total = 0
    cleanObjects.each { obj ->
        def sourceId = Long.valueOf(String.valueOf(obj.sourceId))
        def objectName = String.valueOf(obj.objectName)
        def srcPs = conn.prepareStatement('SELECT file_path,file_name,type FROM data_source_record WHERE id=? AND owner_username=? LIMIT 1')
        srcPs.setLong(1, sourceId)
        srcPs.setString(2, owner)
        def srcRs = srcPs.executeQuery()
        if (!srcRs.next()) {
            return
        }
        def sourceType = String.valueOf(srcRs.getString('type'))
        if (!'FILE'.equalsIgnoreCase(sourceType)) {
            throw new RuntimeException('CLEAN native only supports FILE source now')
        }
        def filePath = String.valueOf(srcRs.getString('file_path')).replace('/app/uploads', '/data/uploads')
        def fileName = String.valueOf(srcRs.getString('file_name'))
        def effectiveName = objectName ?: fileName
        def rows = readFileRows(filePath, effectiveName)
        int rn = 1
        rows.each { r ->
            def raw = JsonOutput.toJson(r)

            // Raw layer keeps original ingested row.
            insRaw.setLong(1, taskId)
            insRaw.setLong(2, sourceId)
            insRaw.setString(3, effectiveName)
            insRaw.setInt(4, rn)
            insRaw.setString(5, raw)
            insRaw.addBatch()

            def normalizedMap = [:]
            r.each { k, v -> normalizedMap[String.valueOf(k)] = normalizeValue(v) }

            if (enableStandard) {
                normalizedMap.keySet().toList().each { k ->
                    def val = normalizedMap[k]
                    normalizedMap[k] = val == null ? 'UNKNOWN' : String.valueOf(val).toLowerCase()
                }
            }

            actions.each { action ->
                applyRuleAction(normalizedMap, action as Map)
            }

            def normalized = JsonOutput.toJson(normalizedMap)

            if (enableOutlier && normalized.length() > 8000) {
                rn++
                return
            }
            if (enableDedup && dedupSet.contains(normalized)) {
                rn++
                return
            }
            if (enableDedup) {
                dedupSet.add(normalized)
            }

            insStd.setLong(1, taskId)
            insStd.setLong(2, sourceId)
            insStd.setString(3, effectiveName)
            insStd.setInt(4, rn)
            insStd.setString(5, raw)
            insStd.setString(6, normalized)
            insStd.addBatch()

            rn++
            total++
        }
    }

    insRaw.executeBatch()
    insStd.executeBatch()

    def up = conn.prepareStatement('UPDATE clean_task_record SET status=\'COMPLETED\', cleaned_rows=?, updated_at=NOW() WHERE id=?')
    up.setInt(1, total)
    up.setLong(2, taskId)
    up.executeUpdate()
    conn.commit()
    session.transfer(ff, REL_SUCCESS)
} catch (Exception ex) {
    if (conn != null) {
        try { conn.rollback() } catch (ignored) {}
        if (taskId != null) {
            try {
                def fail = conn.prepareStatement('UPDATE clean_task_record SET status=\'FAILED\', updated_at=NOW() WHERE id=?')
                fail.setLong(1, taskId)
                fail.executeUpdate()
                conn.commit()
            } catch (ignored2) {}
        }
    }
    log.error('CLEAN native script failed: ' + ex.getMessage(), ex)
    session.transfer(ff, REL_FAILURE)
} finally {
    if (conn != null) {
        try { conn.close() } catch (ignored3) {}
    }
}
""";
    }

    private void mergeBlueprintDefaults(Map<String, Object> target, Map<String, Object> defaults) {
        target.putIfAbsent("flowType", defaults.get("flowType"));
        target.putIfAbsent("groupName", defaults.get("groupName"));
        target.putIfAbsent("parameterContext", defaults.get("parameterContext"));
        target.putIfAbsent("controllerServices", defaults.get("controllerServices"));
        target.putIfAbsent("startAfterCreate", defaults.get("startAfterCreate"));

        if (castMapList(target.get("processors")).isEmpty()) {
            target.put("processors", defaults.get("processors"));
        }
        if (castMapList(target.get("connections")).isEmpty()) {
            target.put("connections", defaults.get("connections"));
        }
    }

    private boolean isEmptyBlueprint(Map<String, Object> blueprint) {
        return castMapList(blueprint.get("processors")).isEmpty()
            && castMapList(blueprint.get("connections")).isEmpty()
            && castMapList(blueprint.get("controllerServices")).isEmpty();
    }

    @Scheduled(
        initialDelayString = "${app.nifi.auto-reconcile.initial-delay-ms:30000}",
        fixedDelayString = "${app.nifi.auto-reconcile.fixed-delay-ms:60000}"
    )
    public void autoReconcileNifiRunningTasks() {
        if (!nifiAutoReconcileEnabled) {
            return;
        }

        Set<String> owners = new LinkedHashSet<>();
        owners.addAll(dataProcessTaskRepository.listOwnersWithRunningCleanTasks(nifiAutoReconcileOwnerLimit));
        owners.addAll(dataProcessTaskRepository.listOwnersWithRunningFusionTasks(nifiAutoReconcileOwnerLimit));
        if (owners.isEmpty()) {
            return;
        }

        for (String owner : owners) {
            if (isBlank(owner) || "anonymous".equalsIgnoreCase(owner)) {
                continue;
            }
            try {
                Map<String, Object> result = reconcileNifiRunningTasksInternal(owner, nifiAutoReconcileTaskLimit, "AUTO", "system");
                log.info("NiFi auto reconcile finished. owner={} result={}", owner, result);
            } catch (RuntimeException ex) {
                log.warn("NiFi auto reconcile failed. owner={} error={}", owner, nvl(ex.getMessage()));
            }
        }
    }

    @Transactional
    public Map<String, Object> reconcileNifiRunningTasks(String ownerUsername, Integer limit) {
        return reconcileNifiRunningTasksInternal(ownerUsername, limit, "MANUAL", ownerUsername);
    }

    @Transactional
    public Map<String, Object> reconcileNifiTask(String ownerUsername, String taskType, Long taskId) {
        requireAuthenticated(ownerUsername);
        String normalizedTaskType = text(taskType).toUpperCase();
        if (!"CLEAN".equals(normalizedTaskType) && !"FUSION".equals(normalizedTaskType)) {
            throw new IllegalArgumentException("taskType 仅支持 CLEAN 或 FUSION");
        }
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("taskId 必须大于0");
        }

        Map<String, Object> result;
        if ("CLEAN".equals(normalizedTaskType)) {
            result = reconcileSingleCleanTask(ownerUsername, getCleanTaskById(ownerUsername, taskId));
        } else {
            result = reconcileSingleFusionTask(ownerUsername, getFusionTaskById(ownerUsername, taskId));
        }

        recordNifiReconcile(ownerUsername, "MANUAL", ownerUsername, "SINGLE", normalizedTaskType, taskId, result);
        invalidateDashboardCache(ownerUsername);
        return result;
    }

    @Transactional
    public Map<String, Object> repairCompletedTaskArtifacts(String ownerUsername, String taskType, Integer limit) {
        requireAuthenticated(ownerUsername);
        int safeLimit = (limit == null || limit <= 0) ? 200 : Math.min(limit, 2000);
        String normalizedTaskType = text(taskType).toUpperCase();
        boolean includeClean = isBlank(normalizedTaskType) || "CLEAN".equals(normalizedTaskType);
        boolean includeFusion = isBlank(normalizedTaskType) || "FUSION".equals(normalizedTaskType);
        if (!includeClean && !includeFusion) {
            throw new IllegalArgumentException("taskType 仅支持 CLEAN 或 FUSION");
        }

        int scannedClean = 0;
        int scannedFusion = 0;
        int repairedClean = 0;
        int repairedFusion = 0;

        if (includeClean) {
            List<Map<String, Object>> cleanTasks = dataProcessTaskRepository.listCleanTasks(ownerUsername);
            for (Map<String, Object> task : cleanTasks) {
                if (!"COMPLETED".equalsIgnoreCase(text(task.get("status")))) {
                    continue;
                }
                scannedClean++;
                if (ensureCleanArtifactsForCompletedTask(ownerUsername, task)) {
                    repairedClean++;
                }
                if ((scannedClean + scannedFusion) >= safeLimit) {
                    break;
                }
            }
        }

        if (includeFusion && (scannedClean + scannedFusion) < safeLimit) {
            List<Map<String, Object>> fusionTasks = dataProcessTaskRepository.listFusionTasks(ownerUsername);
            for (Map<String, Object> task : fusionTasks) {
                if (!"COMPLETED".equalsIgnoreCase(text(task.get("status")))) {
                    continue;
                }
                scannedFusion++;
                if (ensureFusionArtifactsForCompletedTask(ownerUsername, task)) {
                    repairedFusion++;
                }
                if ((scannedClean + scannedFusion) >= safeLimit) {
                    break;
                }
            }
        }

        invalidateDashboardCache(ownerUsername);
        return Map.of(
            "owner", ownerUsername,
            "taskType", normalizedTaskType,
            "scanLimit", safeLimit,
            "scannedClean", scannedClean,
            "scannedFusion", scannedFusion,
            "repairedClean", repairedClean,
            "repairedFusion", repairedFusion,
            "repairedTotal", repairedClean + repairedFusion
        );
    }

    public List<Map<String, Object>> listNifiReconcileRecords(String ownerUsername, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 100 : Math.min(limit, 500);
        return jdbcTemplate.query(
            """
            SELECT id, trigger_type, trigger_user, reconcile_mode, task_type, task_id, result_json, created_at
              FROM nifi_task_reconcile_record
             WHERE owner_username=?
             ORDER BY id DESC
             LIMIT ?
            """,
            (rs, i) -> Map.of(
                "id", rs.getLong("id"),
                "triggerType", nvl(rs.getString("trigger_type")),
                "triggerUser", nvl(rs.getString("trigger_user")),
                "reconcileMode", nvl(rs.getString("reconcile_mode")),
                "taskType", nvl(rs.getString("task_type")),
                "taskId", rs.getObject("task_id") == null ? 0L : rs.getLong("task_id"),
                "result", castMap(parseJson(rs.getString("result_json"))),
                "createdAt", formatDateTime(rs.getTimestamp("created_at"))
            ),
            ownerUsername,
            safeLimit
        );
    }

    private Map<String, Object> reconcileNifiRunningTasksInternal(String ownerUsername, Integer limit, String triggerType, String triggerUser) {
        requireAuthenticated(ownerUsername);
        int safeLimit = (limit == null || limit <= 0) ? 100 : Math.min(limit, 500);
        int completedClean = 0;
        int completedFusion = 0;
        int failedTimeout = 0;
        int stillRunning = 0;

        List<Map<String, Object>> runningCleanTasks = dataProcessTaskRepository.listRunningCleanTasks(ownerUsername, safeLimit);
        for (Map<String, Object> cleanTask : runningCleanTasks) {
            Map<String, Object> single = reconcileSingleCleanTask(ownerUsername, cleanTask);
            String outcome = text(single.get("outcome"));
            if ("COMPLETED".equals(outcome)) {
                completedClean++;
            } else if ("FAILED_TIMEOUT".equals(outcome)) {
                failedTimeout++;
            } else {
                stillRunning++;
            }
        }

        List<Map<String, Object>> runningFusionTasks = dataProcessTaskRepository.listRunningFusionTasks(ownerUsername, safeLimit);
        for (Map<String, Object> fusionTask : runningFusionTasks) {
            Map<String, Object> single = reconcileSingleFusionTask(ownerUsername, fusionTask);
            String outcome = text(single.get("outcome"));
            if ("COMPLETED".equals(outcome)) {
                completedFusion++;
            } else if ("FAILED_TIMEOUT".equals(outcome)) {
                failedTimeout++;
            } else {
                stillRunning++;
            }
        }

        invalidateDashboardCache(ownerUsername);
        Map<String, Object> result = Map.of(
            "owner", ownerUsername,
            "engine", "NIFI",
            "scanLimit", safeLimit,
            "completedClean", completedClean,
            "completedFusion", completedFusion,
            "failedTimeout", failedTimeout,
            "stillRunning", stillRunning,
            "runningTimeoutSeconds", nifiRunningTimeoutSeconds
        );
        recordNifiReconcile(ownerUsername, triggerType, triggerUser, "BATCH", "", 0L, result);
        return result;
    }

    private Map<String, Object> reconcileSingleCleanTask(String ownerUsername, Map<String, Object> cleanTask) {
        Long taskId = toLong(cleanTask.get("id"));
        String status = text(cleanTask.get("status")).toUpperCase();
        if (taskId == null) {
            return Map.of("taskType", "CLEAN", "taskId", 0L, "outcome", "SKIPPED", "reason", "INVALID_TASK");
        }
        if ("COMPLETED".equals(status)) {
            ensureCleanArtifactsForCompletedTask(ownerUsername, cleanTask);
            return Map.of("taskType", "CLEAN", "taskId", taskId, "outcome", "COMPLETED", "reason", "ALREADY_COMPLETED");
        }
        if (!"RUNNING".equals(status)) {
            return Map.of("taskType", "CLEAN", "taskId", taskId, "outcome", "SKIPPED", "reason", "STATUS_" + status);
        }

        String standardTable = sanitizeTableName(text(cleanTask.get("standardTable")));
        Integer landedRows = queryCleanRows(standardTable, taskId);
        if (landedRows != null && landedRows > 0) {
            dataProcessTaskRepository.markCleanTaskCompleted(ownerUsername, taskId, landedRows);
            persistCleanLayersAndGovernance(ownerUsername, taskId, standardTable, castMapList(cleanTask.get("cleanObjects")));
            return Map.of("taskType", "CLEAN", "taskId", taskId, "outcome", "COMPLETED", "landedRows", landedRows, "table", standardTable);
        }

        if (isTaskRunningTimeout(cleanTask)) {
            dataProcessTaskRepository.markCleanTaskFailed(ownerUsername, taskId);
            recordAudit(ownerUsername, "RECONCILE", "CLEAN_TASK", String.valueOf(taskId), "FAILED", Map.of("reason", "NIFI_RUNNING_TIMEOUT"));
            return Map.of("taskType", "CLEAN", "taskId", taskId, "outcome", "FAILED_TIMEOUT", "table", standardTable);
        }

        return Map.of("taskType", "CLEAN", "taskId", taskId, "outcome", "STILL_RUNNING", "table", standardTable);
    }

    private Map<String, Object> reconcileSingleFusionTask(String ownerUsername, Map<String, Object> fusionTask) {
        Long taskId = toLong(fusionTask.get("id"));
        String status = text(fusionTask.get("status")).toUpperCase();
        if (taskId == null) {
            return Map.of("taskType", "FUSION", "taskId", 0L, "outcome", "SKIPPED", "reason", "INVALID_TASK");
        }
        if ("COMPLETED".equals(status)) {
            ensureFusionArtifactsForCompletedTask(ownerUsername, fusionTask);
            return Map.of("taskType", "FUSION", "taskId", taskId, "outcome", "COMPLETED", "reason", "ALREADY_COMPLETED");
        }
        if (!"RUNNING".equals(status)) {
            return Map.of("taskType", "FUSION", "taskId", taskId, "outcome", "SKIPPED", "reason", "STATUS_" + status);
        }

        String targetTable = sanitizeTableName(text(fusionTask.get("targetTable")));
        Integer landedRows = queryFusionRows(targetTable, taskId);
        if (landedRows != null && landedRows > 0) {
            dataProcessTaskRepository.markFusionTaskCompleted(ownerUsername, taskId, landedRows);
            String strategy = text(fusionTask.get("strategy"));
            List<String> sourceTables = castStringList(fusionTask.get("standardTables"));
            stagingTableService.persistFusionResultToGold(ownerUsername, taskId, targetTable, strategy);
            persistGovernanceArtifacts(ownerUsername, "FUSION", taskId, targetTable, sourceTables);
            return Map.of("taskType", "FUSION", "taskId", taskId, "outcome", "COMPLETED", "landedRows", landedRows, "table", targetTable);
        }

        if (isTaskRunningTimeout(fusionTask)) {
            dataProcessTaskRepository.markFusionTaskFailed(ownerUsername, taskId);
            recordAudit(ownerUsername, "RECONCILE", "FUSION_TASK", String.valueOf(taskId), "FAILED", Map.of("reason", "NIFI_RUNNING_TIMEOUT"));
            return Map.of("taskType", "FUSION", "taskId", taskId, "outcome", "FAILED_TIMEOUT", "table", targetTable);
        }

        return Map.of("taskType", "FUSION", "taskId", taskId, "outcome", "STILL_RUNNING", "table", targetTable);
    }

    private void recordNifiReconcile(String ownerUsername, String triggerType, String triggerUser, String mode, String taskType, Long taskId, Map<String, Object> result) {
        String tenantId = resolveTenantId(ownerUsername);
        jdbcTemplate.update(
            "INSERT INTO nifi_task_reconcile_record(tenant_id,owner_username,trigger_type,trigger_user,reconcile_mode,task_type,task_id,result_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
            tenantId,
            ownerUsername,
            text(triggerType).toUpperCase(),
            isBlank(triggerUser) ? ownerUsername : triggerUser,
            text(mode).toUpperCase(),
            isBlank(taskType) ? null : text(taskType).toUpperCase(),
            taskId != null && taskId > 0 ? taskId : null,
            toJson(result),
            now()
        );
    }

    @Transactional
    public Map<String, Object> triggerNifiFlow(String ownerUsername, Map<String, Object> payload) {
        requireAuthenticated(ownerUsername);
        String flowType = text(payload.get("flowType")).toUpperCase();
        String processGroupId = text(payload.get("processGroupId"));
        Map<String, Object> parameters = castMap(payload.get("parameters"));
        Map<String, Object> template = findEnabledNifiTemplate(ownerUsername, flowType);
        if (isBlank(processGroupId)) {
            processGroupId = text(template.get("processGroupId"));
        }
        if (isBlank(processGroupId)) {
            processGroupId = nifiOrchestrationService.autoDiscoverProcessGroupId(flowType);
        }

        int processorCount = nifiOrchestrationService.getProcessGroupProcessorCount(processGroupId);
        boolean requireFusionNative = "FUSION".equals(flowType);
        boolean requireCleanNative = "CLEAN".equals(flowType);
        boolean fusionNativeReady = !requireFusionNative || nifiOrchestrationService.isFusionNativeProcessGroup(processGroupId);
        boolean cleanNativeReady = !requireCleanNative || nifiOrchestrationService.isCleanNativeProcessGroup(processGroupId);
        if (processorCount <= 0 || !fusionNativeReady || !cleanNativeReady) {
            log.warn("NiFi {} template points to empty processGroupId={}, trying auto bootstrap self-heal", flowType, processGroupId);
            try {
                Map<String, Object> bootstrap = bootstrapNifiEtlTemplates(ownerUsername);
                Map<String, Object> healedTemplate = "CLEAN".equals(flowType)
                    ? castMap(bootstrap.get("clean"))
                    : castMap(bootstrap.get("fusion"));
                String healedProcessGroupId = text(healedTemplate.get("processGroupId"));
                if (!isBlank(healedProcessGroupId) && !healedProcessGroupId.equals(processGroupId)) {
                    int healedProcessorCount = nifiOrchestrationService.getProcessGroupProcessorCount(healedProcessGroupId);
                    boolean healedNativeReady = !requireFusionNative || nifiOrchestrationService.isFusionNativeProcessGroup(healedProcessGroupId);
                    boolean healedCleanNativeReady = !requireCleanNative || nifiOrchestrationService.isCleanNativeProcessGroup(healedProcessGroupId);
                    if (healedProcessorCount > 0 && healedNativeReady && healedCleanNativeReady) {
                        processGroupId = healedProcessGroupId;
                        template = findEnabledNifiTemplate(ownerUsername, flowType);
                        processorCount = healedProcessorCount;
                        fusionNativeReady = healedNativeReady;
                        cleanNativeReady = healedCleanNativeReady;
                        log.info("NiFi {} self-heal succeeded. switched processGroupId={} processorCount={}", flowType, processGroupId, processorCount);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("NiFi {} self-heal failed for processGroupId={}: {}", flowType, processGroupId, ex.getMessage());
            }
        }
        if (processorCount <= 0 || !fusionNativeReady || !cleanNativeReady) {
            throw new IllegalStateException(
                "NiFi " + flowType + " 流程组未配置处理器，系统已尝试自动修复但仍失败。请先在 NiFi 中配置处理链后再执行任务。processGroupId=" + processGroupId
            );
        }

        boolean skipCallbackCheck = requireFusionNative || requireCleanNative;
        Map<String, Object> callbackHealth = Map.of("skip", skipCallbackCheck);
        int invalidCallbacksAfter = 0;
        if (!skipCallbackCheck) {
            callbackHealth = nifiOrchestrationService.ensureCallbackProcessorsReady(processGroupId);
            invalidCallbacksAfter = callbackHealth.get("invalidCallbacksAfter") instanceof Number n ? n.intValue() : 0;
            if (invalidCallbacksAfter > 0) {
                log.warn("NiFi {} processGroupId={} has invalid callback processors after self-heal: {}", flowType, processGroupId, callbackHealth);
                try {
                    Map<String, Object> bootstrap = bootstrapNifiEtlTemplates(ownerUsername);
                    Map<String, Object> healedTemplate = "CLEAN".equals(flowType)
                        ? castMap(bootstrap.get("clean"))
                        : castMap(bootstrap.get("fusion"));
                    String healedProcessGroupId = text(healedTemplate.get("processGroupId"));
                    if (!isBlank(healedProcessGroupId) && !healedProcessGroupId.equals(processGroupId)) {
                        processGroupId = healedProcessGroupId;
                        template = findEnabledNifiTemplate(ownerUsername, flowType);
                        callbackHealth = nifiOrchestrationService.ensureCallbackProcessorsReady(processGroupId);
                        invalidCallbacksAfter = callbackHealth.get("invalidCallbacksAfter") instanceof Number n ? n.intValue() : 0;
                        log.info("NiFi {} callback self-heal switched processGroupId={} callbackHealth={}", flowType, processGroupId, callbackHealth);
                    }
                } catch (RuntimeException ex) {
                    log.warn("NiFi {} callback self-heal bootstrap failed for processGroupId={}: {}", flowType, processGroupId, ex.getMessage());
                }
            }
        }
        if (invalidCallbacksAfter > 0) {
            throw new IllegalStateException(
                "NiFi " + flowType + " 回调处理器无效，系统已尝试自动修复但仍失败。processGroupId=" + processGroupId + ", callbackHealth=" + callbackHealth
            );
        }
        validateFlowParameters(flowType, parameters, template);

        String tenantId = resolveTenantId(ownerUsername);
        String now = now();
        Map<String, Object> result;

        Map<String, Object> requestPayload = Map.of(
            "flowType", flowType,
            "processGroupId", processGroupId,
            "parameters", parameters,
            "templateVersion", template.getOrDefault("versionNo", 0)
        );

        try {
            result = nifiOrchestrationService.triggerFlow(flowType, processGroupId, parameters);
            insertAndGetId(
                "INSERT INTO nifi_flow_run_record(tenant_id,owner_username,flow_type,process_group_id,dispatch_status,external_run_id,request_json,response_json,error_message,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                tenantId,
                ownerUsername,
                text(result.get("flowType")),
                text(result.get("processGroupId")),
                text(result.get("dispatchStatus")),
                text(result.get("externalRunId")),
                toJson(requestPayload),
                toJson(result),
                "",
                now,
                now
            );
            recordAudit(
                ownerUsername,
                "RUN",
                "NIFI_FLOW",
                text(result.get("externalRunId")),
                "SUCCESS",
                Map.of(
                    "flowType", text(result.get("flowType")),
                    "processGroupId", text(result.get("processGroupId")),
                    "dispatchStatus", text(result.get("dispatchStatus")),
                    "templateVersion", template.getOrDefault("versionNo", 0)
                )
            );
            return Map.of(
                "flowType", text(result.get("flowType")),
                "processGroupId", text(result.get("processGroupId")),
                "dispatchStatus", text(result.get("dispatchStatus")),
                "templateVersion", template.getOrDefault("versionNo", 0),
                "externalRunId", text(result.get("externalRunId")),
                "httpStatus", result.getOrDefault("httpStatus", 0),
                "submittedAt", result.getOrDefault("submittedAt", now)
            );
        } catch (RuntimeException ex) {
            String safeFlowType = isBlank(flowType) ? "INGEST" : flowType.toUpperCase();
            String safeProcessGroupId = processGroupId;
            if (isBlank(safeProcessGroupId)) {
                safeProcessGroupId = "UNKNOWN";
            }
            insertAndGetId(
                "INSERT INTO nifi_flow_run_record(tenant_id,owner_username,flow_type,process_group_id,dispatch_status,external_run_id,request_json,response_json,error_message,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                tenantId,
                ownerUsername,
                safeFlowType,
                safeProcessGroupId,
                "FAILED",
                "",
                toJson(requestPayload),
                "{}",
                nvl(ex.getMessage()),
                now,
                now
            );
            recordAudit(
                ownerUsername,
                "RUN",
                "NIFI_FLOW",
                safeProcessGroupId,
                "FAILED",
                Map.of("reason", nvl(ex.getMessage()), "flowType", safeFlowType)
            );
            return Map.of(
                "flowType", safeFlowType,
                "processGroupId", safeProcessGroupId,
                "dispatchStatus", "FAILED",
                "templateVersion", template.getOrDefault("versionNo", 0),
                "errorMessage", nvl(ex.getMessage()),
                "submittedAt", now
            );
        }
    }

    public List<Map<String, Object>> listNifiFlowRuns(String ownerUsername, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 50 : Math.min(limit, 500);
        return jdbcTemplate.query(
            """
            SELECT id, flow_type, process_group_id, dispatch_status, external_run_id, request_json, response_json, error_message, created_at, updated_at
              FROM nifi_flow_run_record
             WHERE owner_username=?
             ORDER BY id DESC
             LIMIT ?
            """,
            (rs, i) -> Map.of(
                "id", rs.getLong("id"),
                "flowType", rs.getString("flow_type"),
                "processGroupId", rs.getString("process_group_id"),
                "dispatchStatus", rs.getString("dispatch_status"),
                "externalRunId", nvl(rs.getString("external_run_id")),
                "request", castMap(parseJson(rs.getString("request_json"))),
                "response", castMap(parseJson(rs.getString("response_json"))),
                "errorMessage", nvl(rs.getString("error_message")),
                "createdAt", formatDateTime(rs.getTimestamp("created_at")),
                "updatedAt", formatDateTime(rs.getTimestamp("updated_at"))
            ),
            ownerUsername,
            safeLimit
        );
    }

    public Map<String, Object> getLayerStats(String ownerUsername, String taskType, Long taskId) {
        String normalizedTaskType = text(taskType).toUpperCase();
        if ("CLEAN".equals(normalizedTaskType) && taskId != null && taskId > 0) {
            try {
                Map<String, Object> task = getCleanTaskById(ownerUsername, taskId);
                ensureCleanArtifactsForCompletedTask(ownerUsername, task);
            } catch (RuntimeException ex) {
                log.warn("ensure clean artifacts before stats failed. owner={} taskId={} error={}", ownerUsername, taskId, nvl(ex.getMessage()));
            }
        }
        if ("FUSION".equals(normalizedTaskType) && taskId != null && taskId > 0) {
            try {
                Map<String, Object> task = getFusionTaskById(ownerUsername, taskId);
                ensureFusionArtifactsForCompletedTask(ownerUsername, task);
            } catch (RuntimeException ex) {
                log.warn("ensure fusion artifacts before stats failed. owner={} taskId={} error={}", ownerUsername, taskId, nvl(ex.getMessage()));
            }
        }
        boolean filterByTaskType = !isBlank(normalizedTaskType);
        boolean filterByTaskId = taskId != null && taskId > 0;
        long filterTaskId = taskId == null ? 0L : taskId;

        List<Map<String, Object>> bronzeRows = jdbcTemplate.queryForList(
            "SELECT source_task_type AS taskType, source_task_id AS taskId, COUNT(1) AS rowCount FROM bronze_ingest_record WHERE owner_username=? GROUP BY source_task_type, source_task_id",
            ownerUsername
        );
        List<Map<String, Object>> silverRows = jdbcTemplate.queryForList(
            "SELECT source_task_type AS taskType, source_task_id AS taskId, COUNT(1) AS rowCount FROM silver_standard_record WHERE owner_username=? GROUP BY source_task_type, source_task_id",
            ownerUsername
        );
        List<Map<String, Object>> goldRows = jdbcTemplate.queryForList(
            "SELECT 'FUSION' AS taskType, fusion_task_id AS taskId, COUNT(1) AS rowCount FROM gold_fusion_wide_record WHERE owner_username=? GROUP BY fusion_task_id",
            ownerUsername
        );

        Map<String, Map<String, Object>> merged = new java.util.LinkedHashMap<>();
        mergeLayerRows(merged, bronzeRows, "bronzeRows");
        mergeLayerRows(merged, silverRows, "silverRows");
        mergeLayerRows(merged, goldRows, "goldRows");

        List<Map<String, Object>> details = merged.values().stream()
            .filter(it -> !filterByTaskType || normalizedTaskType.equals(text(it.get("taskType"))))
            .filter(it -> !filterByTaskId || filterTaskId == (toLong(it.get("taskId")) == null ? 0L : toLong(it.get("taskId"))))
            .toList();

        int bronzeTotal = details.stream().mapToInt(it -> ((Number) it.getOrDefault("bronzeRows", 0)).intValue()).sum();
        int silverTotal = details.stream().mapToInt(it -> ((Number) it.getOrDefault("silverRows", 0)).intValue()).sum();
        int goldTotal = details.stream().mapToInt(it -> ((Number) it.getOrDefault("goldRows", 0)).intValue()).sum();

        return Map.of(
            "owner", ownerUsername,
            "taskType", normalizedTaskType,
            "taskId", taskId == null ? 0L : taskId,
            "summary", Map.of(
                "bronzeRows", bronzeTotal,
                "silverRows", silverTotal,
                "goldRows", goldTotal,
                "taskCount", details.size()
            ),
            "details", details
        );
    }

    private void mergeLayerRows(Map<String, Map<String, Object>> merged, List<Map<String, Object>> rows, String fieldName) {
        for (Map<String, Object> row : rows) {
            String taskType = text(row.get("taskType")).toUpperCase();
            Long taskId = toLong(row.get("taskId"));
            if (taskId == null || isBlank(taskType)) {
                continue;
            }
            String key = taskType + "#" + taskId;
            Map<String, Object> base = merged.computeIfAbsent(key, k -> {
                Map<String, Object> val = new java.util.LinkedHashMap<>();
                val.put("taskType", taskType);
                val.put("taskId", taskId);
                val.put("bronzeRows", 0);
                val.put("silverRows", 0);
                val.put("goldRows", 0);
                return val;
            });
            Number rowCount = (Number) row.get("rowCount");
            base.put(fieldName, rowCount == null ? 0 : rowCount.intValue());
        }
    }

    private Map<String, Object> findEnabledNifiTemplate(String ownerUsername, String flowType) {
        String normalizedFlowType = isBlank(flowType) ? "INGEST" : flowType.toUpperCase();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, process_group_id, parameter_schema_json, version_no FROM nifi_flow_template_record WHERE owner_username=? AND flow_type=? AND enabled=1 LIMIT 1",
            ownerUsername,
            normalizedFlowType
        );
        if (rows.isEmpty()) {
            return Map.of(
                "flowType", normalizedFlowType,
                "processGroupId", "",
                "parameterSchema", Map.of(),
                "versionNo", 0
            );
        }
        Map<String, Object> row = rows.get(0);
        Number versionNo = (Number) row.get("version_no");
        return Map.of(
            "flowType", normalizedFlowType,
            "processGroupId", text(row.get("process_group_id")),
            "parameterSchema", castMap(parseJson(String.valueOf(row.get("parameter_schema_json")))),
            "versionNo", versionNo == null ? 0 : versionNo.intValue()
        );
    }

    private void validateFlowParameters(String flowType, Map<String, Object> parameters, Map<String, Object> template) {
        Map<String, Object> parameterSchema = castMap(template.get("parameterSchema"));
        List<String> requiredKeys = castStringList(parameterSchema.get("requiredKeys"));
        List<String> missing = new ArrayList<>();
        for (String key : requiredKeys) {
            String normalizedKey = text(key);
            if (isBlank(normalizedKey)) {
                continue;
            }
            Object value = parameters.get(normalizedKey);
            if (value == null || isBlank(String.valueOf(value))) {
                missing.add(normalizedKey);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("NiFi flow " + flowType + " 缺少必填参数: " + String.join(",", missing));
        }
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

    private Object parseJson(String raw) {
        if (isBlank(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String formatDateTime(Timestamp ts) {
        return ts == null ? "" : DATE_TIME_FORMATTER.format(ts.toInstant());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
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

    private boolean isMissingTableException(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String msg = nvl(cursor.getMessage()).toLowerCase();
            if (msg.contains("doesn't exist") || msg.contains("does not exist") || msg.contains("not exist")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
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

    private Integer queryCleanRows(String tableName, Long taskId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + stagingTableRef(tableName) + " WHERE task_id=?",
                Integer.class,
                taskId
            );
        } catch (DataAccessException ex) {
            if (isMissingTableException(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private Integer queryFusionRows(String tableName, Long taskId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + stagingTableRef(tableName) + " WHERE fusion_task_id=?",
                Integer.class,
                taskId
            );
        } catch (DataAccessException ex) {
            if (isMissingTableException(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private boolean ensureCleanArtifactsForCompletedTask(String ownerUsername, Map<String, Object> cleanTask) {
        if (cleanTask == null || cleanTask.isEmpty()) {
            return false;
        }
        Long taskId = toLong(cleanTask.get("id"));
        String status = text(cleanTask.get("status")).toUpperCase();
        if (taskId == null || !"COMPLETED".equals(status)) {
            return false;
        }

        String standardTable = sanitizeTableName(text(cleanTask.get("standardTable")));
        Integer landedRows = queryCleanRows(standardTable, taskId);
        if (landedRows == null || landedRows <= 0) {
            return false;
        }

        int bronzeRows = countRows(
            "SELECT COUNT(1) FROM bronze_ingest_record WHERE owner_username=? AND source_task_type='CLEAN' AND source_task_id=?",
            ownerUsername,
            taskId
        );
        int silverRows = countRows(
            "SELECT COUNT(1) FROM silver_standard_record WHERE owner_username=? AND source_task_type='CLEAN' AND source_task_id=?",
            ownerUsername,
            taskId
        );
        int qualityRows = countRows(
            "SELECT COUNT(1) FROM etl_quality_report WHERE owner_username=? AND task_type='CLEAN' AND task_id=? AND table_name=?",
            ownerUsername,
            taskId,
            standardTable
        );
        int snapshotRows = countRows(
            "SELECT COUNT(1) FROM etl_table_snapshot WHERE owner_username=? AND task_type='CLEAN' AND task_id=? AND table_name=?",
            ownerUsername,
            taskId,
            standardTable
        );

        if (bronzeRows == 0 && silverRows == 0 && qualityRows == 0 && snapshotRows == 0) {
            persistCleanLayersAndGovernance(ownerUsername, taskId, standardTable, castMapList(cleanTask.get("cleanObjects")));
            recordAudit(ownerUsername, "REPAIR", "CLEAN_TASK", String.valueOf(taskId), "SUCCESS", Map.of(
                "reason", "BACKFILL_MISSING_ARTIFACTS",
                "standardTable", standardTable,
                "landedRows", landedRows
            ));
            return true;
        }
        return false;
    }

    private boolean ensureFusionArtifactsForCompletedTask(String ownerUsername, Map<String, Object> fusionTask) {
        if (fusionTask == null || fusionTask.isEmpty()) {
            return false;
        }
        Long taskId = toLong(fusionTask.get("id"));
        String status = text(fusionTask.get("status")).toUpperCase();
        if (taskId == null || !"COMPLETED".equals(status)) {
            return false;
        }

        String targetTable = sanitizeTableName(text(fusionTask.get("targetTable")));
        Integer landedRows = queryFusionRows(targetTable, taskId);
        if (landedRows == null || landedRows <= 0) {
            return false;
        }

        int goldRows = countRows(
            "SELECT COUNT(1) FROM gold_fusion_wide_record WHERE owner_username=? AND fusion_task_id=?",
            ownerUsername,
            taskId
        );
        int qualityRows = countRows(
            "SELECT COUNT(1) FROM etl_quality_report WHERE owner_username=? AND task_type='FUSION' AND task_id=? AND table_name=?",
            ownerUsername,
            taskId,
            targetTable
        );
        int snapshotRows = countRows(
            "SELECT COUNT(1) FROM etl_table_snapshot WHERE owner_username=? AND task_type='FUSION' AND task_id=? AND table_name=?",
            ownerUsername,
            taskId,
            targetTable
        );
        int lineageRows = countRows(
            "SELECT COUNT(1) FROM etl_field_lineage WHERE owner_username=? AND task_type='FUSION' AND task_id=? AND target_table=?",
            ownerUsername,
            taskId,
            targetTable
        );

        if (goldRows == 0 && qualityRows == 0 && snapshotRows == 0 && lineageRows == 0) {
            String strategy = text(fusionTask.get("strategy"));
            List<String> sourceTables = castStringList(fusionTask.get("standardTables"));
            stagingTableService.persistFusionResultToGold(ownerUsername, taskId, targetTable, strategy);
            persistGovernanceArtifacts(ownerUsername, "FUSION", taskId, targetTable, sourceTables);
            recordAudit(ownerUsername, "REPAIR", "FUSION_TASK", String.valueOf(taskId), "SUCCESS", Map.of(
                "reason", "BACKFILL_MISSING_ARTIFACTS",
                "targetTable", targetTable,
                "landedRows", landedRows
            ));
            return true;
        }
        return false;
    }

    private int countRows(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void persistCleanLayersAndGovernance(String ownerUsername, Long taskId, String standardTable, List<Map<String, Object>> cleanObjects) {
        stagingTableService.persistCleanResultToLayers(ownerUsername, taskId, standardTable);
        persistGovernanceArtifacts(ownerUsername, "CLEAN", taskId, standardTable, cleanObjects.stream()
            .map(it -> text(it.get("objectName")))
            .filter(it -> !isBlank(it))
            .toList());
    }

    private boolean isTaskRunningTimeout(Map<String, Object> task) {
        String updatedAt = text(task.get("updatedAt"));
        if (isBlank(updatedAt)) {
            return false;
        }
        try {
            LocalDateTime updateTime = LocalDateTime.parse(updatedAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long runningSeconds = java.time.Duration.between(updateTime.atZone(ZoneId.systemDefault()).toInstant(), Instant.now()).getSeconds();
            return runningSeconds >= nifiRunningTimeoutSeconds;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Map<String, Object> dispatchCleanTaskToNifi(
        String ownerUsername,
        Long taskId,
        Map<String, Object> task,
        String outputTable,
        String strategyCode,
        List<Map<String, Object>> cleanObjects,
        List<String> ruleNames
    ) {
        Map<String, Object> payload = Map.of(
            "flowType", "CLEAN",
            "parameters", Map.of(
                "ownerUsername", ownerUsername,
                "taskId", taskId,
                "taskName", text(task.get("taskName")),
                "standardTable", outputTable,
                "strategy", strategyCode,
                "ruleNames", toJson(ruleNames),
                "cleanObjects", toJson(cleanObjects)
            )
        );
        Map<String, Object> result = triggerNifiFlow(ownerUsername, payload);
        assertNifiSubmitted(result, "CLEAN", taskId);
        return result;
    }

    private Map<String, Object> dispatchFusionTaskToNifi(
        String ownerUsername,
        Long taskId,
        Map<String, Object> task,
        String targetTable,
        List<String> standardTables,
        String strategy,
        Map<String, Object> fusionConfig
    ) {
        Map<String, Object> payload = Map.of(
            "flowType", "FUSION",
            "parameters", Map.of(
                "ownerUsername", ownerUsername,
                "taskId", taskId,
                "taskName", text(task.get("taskName")),
                "targetTable", targetTable,
                "strategy", strategy,
                "standardTables", toJson(standardTables),
                "fusionConfig", toJson(fusionConfig)
            )
        );
        Map<String, Object> result = triggerNifiFlow(ownerUsername, payload);
        assertNifiSubmitted(result, "FUSION", taskId);
        return result;
    }

    private void assertNifiSubmitted(Map<String, Object> result, String taskType, Long taskId) {
        String dispatchStatus = text(result.get("dispatchStatus"));
        if (!"SUBMITTED".equalsIgnoreCase(dispatchStatus)) {
            throw new IllegalStateException("NiFi " + taskType + " 任务下发失败 taskId=" + taskId + ", status=" + dispatchStatus + ", message=" + text(result.get("errorMessage")));
        }
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
            throw new IllegalArgumentException("未认证用户不允许执行写操作");
        }
    }


    private Map<String, Object> executeWorkflowNode(String ownerUsername, WorkflowDefinitionService.WorkflowNode node) {
        return switch (node.taskType()) {
            case "CLEAN" -> runCleanTask(ownerUsername, node.taskId());
            case "FUSION" -> runFusionTask(ownerUsername, node.taskId());
            default -> throw new IllegalArgumentException("不支持的节点任务类型: " + node.taskType());
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

    private String buildRawTableName(String standardTableName) {
        String safeStandard = sanitizeTableName(standardTableName);
        String candidate = safeStandard + "_raw";
        if (candidate.length() <= 64) {
            return candidate;
        }
        String hash = Integer.toHexString(safeStandard.hashCode()).replace('-', '0');
        int keep = Math.max(1, 64 - 5 - hash.length());
        return safeStandard.substring(0, keep) + "_raw_" + hash;
    }

}



