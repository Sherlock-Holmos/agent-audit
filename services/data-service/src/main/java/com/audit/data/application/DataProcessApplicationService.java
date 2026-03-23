package com.audit.data.application;

import com.audit.data.service.api.IDataProcessAsyncService;
import com.audit.data.service.api.IDataProcessService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 数据处理应用服务：统一封装清洗、融合、异步任务与治理能力入口。
 */
public class DataProcessApplicationService implements IDataProcessApplicationService {

    private final IDataProcessService dataProcessService;
    private final IDataProcessAsyncService dataProcessAsyncService;

    public DataProcessApplicationService(
        IDataProcessService dataProcessService,
        IDataProcessAsyncService dataProcessAsyncService
    ) {
        this.dataProcessService = dataProcessService;
        this.dataProcessAsyncService = dataProcessAsyncService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCleanTasks(String username, String keyword, String sourceId, String status) {
        return dataProcessService.listCleanTasks(normalizeUser(username), keyword, sourceId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCleanRules(String username) {
        return dataProcessService.listCleanRules(normalizeUser(username));
    }

    @Override
    @Transactional
    public Map<String, Object> uploadCleanRule(String username, Map<String, Object> payload) {
        return dataProcessService.uploadCleanRule(normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> toggleCleanRule(String username, Long id, boolean enabled) {
        return dataProcessService.toggleCleanRule(normalizeUser(username), id, enabled);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCleanRuleDetail(String username, Long id) {
        return dataProcessService.getCleanRuleDetail(normalizeUser(username), id);
    }

    @Override
    @Transactional
    public Map<String, Object> updateCleanRule(String username, Long id, Map<String, Object> payload) {
        return dataProcessService.updateCleanRule(normalizeUser(username), id, payload);
    }

    @Override
    @Transactional
    public void deleteCleanRule(String username, Long id) {
        dataProcessService.deleteCleanRule(normalizeUser(username), id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCleanStrategies(String username) {
        return dataProcessService.listCleanStrategies(normalizeUser(username));
    }

    @Override
    @Transactional
    public Map<String, Object> createCleanStrategy(String username, Map<String, Object> payload) {
        return dataProcessService.createCleanStrategy(normalizeUser(username), payload);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCleanStrategyDetail(String username, Long id) {
        return dataProcessService.getCleanStrategyDetail(normalizeUser(username), id);
    }

    @Override
    @Transactional
    public Map<String, Object> updateCleanStrategy(String username, Long id, Map<String, Object> payload) {
        return dataProcessService.updateCleanStrategy(normalizeUser(username), id, payload);
    }

    @Override
    @Transactional
    public Map<String, Object> toggleCleanStrategy(String username, Long id, boolean enabled) {
        return dataProcessService.toggleCleanStrategy(normalizeUser(username), id, enabled);
    }

    @Override
    @Transactional
    public void deleteCleanStrategy(String username, Long id) {
        dataProcessService.deleteCleanStrategy(normalizeUser(username), id);
    }

    @Override
    @Transactional
    public Map<String, Object> createCleanTask(String username, Map<String, Object> payload) {
        return dataProcessService.createCleanTask(normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> runCleanTask(String username, Long id) {
        return dataProcessService.runCleanTask(normalizeUser(username), id);
    }

    @Override
    @Transactional
    public Map<String, Object> runCleanTaskAsync(String username, Long id, String idempotencyKey) {
        return dataProcessAsyncService.startCleanTask(normalizeUser(username), id, idempotencyKey);
    }

    @Override
    @Transactional
    public void deleteCleanTask(String username, Long id) {
        dataProcessService.deleteCleanTask(normalizeUser(username), id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFusionTasks(String username, String keyword, String status) {
        return dataProcessService.listFusionTasks(normalizeUser(username), keyword, status);
    }

    @Override
    @Transactional
    public Map<String, Object> createFusionTask(String username, Map<String, Object> payload) {
        return dataProcessService.createFusionTask(normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> runFusionTask(String username, Long id) {
        return dataProcessService.runFusionTask(normalizeUser(username), id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> previewFusionTask(String username, Long id, Integer limit) {
        return dataProcessService.previewFusionTask(normalizeUser(username), id, limit);
    }

    @Override
    @Transactional
    public Map<String, Object> runFusionTaskAsync(String username, Long id, String idempotencyKey) {
        return dataProcessAsyncService.startFusionTask(normalizeUser(username), id, idempotencyKey);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getJobStatus(String username, String jobId) {
        return dataProcessAsyncService.getJobStatus(normalizeUser(username), jobId);
    }

    @Override
    @Transactional
    public void deleteFusionTask(String username, Long id) {
        dataProcessService.deleteFusionTask(normalizeUser(username), id);
    }

    @Override
    @Transactional
    public Map<String, Object> cleanupOrphanGeneratedTables(String username) {
        return dataProcessService.cleanupOrphanGeneratedTables(normalizeUser(username));
    }

    @Override
    @Transactional
    public Map<String, Object> runWorkflow(String username, Map<String, Object> payload) {
        return dataProcessService.runWorkflow(normalizeUser(username), payload);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listLineageRecords(String username, String taskType, Long taskId) {
        return dataProcessService.listLineageRecords(normalizeUser(username), taskType, taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listQualityReports(String username, String taskType, Long taskId) {
        return dataProcessService.listQualityReports(normalizeUser(username), taskType, taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSnapshotRecords(String username, String taskType, Long taskId) {
        return dataProcessService.listSnapshotRecords(normalizeUser(username), taskType, taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAuditRecords(String username, Integer limit) {
        return dataProcessService.listAuditRecords(normalizeUser(username), limit);
    }

    private String normalizeUser(String username) {
        return (username == null || username.isBlank()) ? "anonymous" : username;
    }
}

