package com.audit.data.application;

import java.util.List;
import java.util.Map;

/**
 * 数据处理应用服务接口。
 */
public interface IDataProcessApplicationService {

    List<Map<String, Object>> listCleanTasks(String username, String keyword, String sourceId, String status);
    List<Map<String, Object>> listCleanRules(String username);
    Map<String, Object> uploadCleanRule(String username, Map<String, Object> payload);
    Map<String, Object> toggleCleanRule(String username, Long id, boolean enabled);
    Map<String, Object> getCleanRuleDetail(String username, Long id);
    Map<String, Object> updateCleanRule(String username, Long id, Map<String, Object> payload);
    void deleteCleanRule(String username, Long id);

    List<Map<String, Object>> listCleanStrategies(String username);
    Map<String, Object> createCleanStrategy(String username, Map<String, Object> payload);
    Map<String, Object> getCleanStrategyDetail(String username, Long id);
    Map<String, Object> updateCleanStrategy(String username, Long id, Map<String, Object> payload);
    Map<String, Object> toggleCleanStrategy(String username, Long id, boolean enabled);
    void deleteCleanStrategy(String username, Long id);

    Map<String, Object> createCleanTask(String username, Map<String, Object> payload);
    Map<String, Object> runCleanTask(String username, Long id);
    Map<String, Object> runCleanTaskAsync(String username, Long id, String idempotencyKey);
    void deleteCleanTask(String username, Long id);

    List<Map<String, Object>> listFusionTasks(String username, String keyword, String status);
    Map<String, Object> createFusionTask(String username, Map<String, Object> payload);
    Map<String, Object> runFusionTask(String username, Long id);
    Map<String, Object> previewFusionTask(String username, Long id, Integer limit);
    Map<String, Object> runFusionTaskAsync(String username, Long id, String idempotencyKey);
    Map<String, Object> getJobStatus(String username, String jobId);
    void deleteFusionTask(String username, Long id);

    Map<String, Object> cleanupOrphanGeneratedTables(String username);

    Map<String, Object> runWorkflow(String username, Map<String, Object> payload);
    List<Map<String, Object>> listLineageRecords(String username, String taskType, Long taskId);
    List<Map<String, Object>> listQualityReports(String username, String taskType, Long taskId);
    List<Map<String, Object>> listSnapshotRecords(String username, String taskType, Long taskId);
    List<Map<String, Object>> listAuditRecords(String username, Integer limit);
}

