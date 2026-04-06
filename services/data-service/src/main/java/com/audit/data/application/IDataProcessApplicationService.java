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

    List<Map<String, Object>> listFusionKeySynonyms(String username);
    Map<String, Object> createFusionKeySynonym(String username, Map<String, Object> payload);
    Map<String, Object> getFusionKeySynonymDetail(String username, Long id);
    Map<String, Object> updateFusionKeySynonym(String username, Long id, Map<String, Object> payload);
    Map<String, Object> toggleFusionKeySynonym(String username, Long id, boolean enabled);
    void deleteFusionKeySynonym(String username, Long id);
    List<Map<String, Object>> listFusionKeySynonymHistory(String username, Long id, Integer limit);
    List<Map<String, Object>> listFusionKeySynonymHistoryByCanonicalKey(String username, String canonicalKey, Integer limit);

    Map<String, Object> createCleanTask(String username, Map<String, Object> payload);
    Map<String, Object> updateCleanTask(String username, Long id, Map<String, Object> payload);
    Map<String, Object> runCleanTask(String username, Long id);
    Map<String, Object> previewCleanTask(String username, Long id, Integer limit);
    Map<String, Object> runCleanTaskAsync(String username, Long id, String idempotencyKey);
    void deleteCleanTask(String username, Long id);

    List<Map<String, Object>> listFusionTasks(String username, String keyword, String status);
    Map<String, Object> createFusionTask(String username, Map<String, Object> payload);
    Map<String, Object> updateFusionTask(String username, Long id, Map<String, Object> payload);
    Map<String, Object> runFusionTask(String username, Long id);
    Map<String, Object> previewFusionTask(String username, Long id, Integer limit);
    Map<String, Object> runFusionTaskAsync(String username, Long id, String idempotencyKey);
    Map<String, Object> getJobStatus(String username, String jobId);
    void deleteFusionTask(String username, Long id);

    Map<String, Object> cleanupOrphanGeneratedTables(String username);

    Map<String, Object> runWorkflow(String username, Map<String, Object> payload);
    Map<String, Object> getNifiStatus(String username);
    Map<String, Object> triggerNifiFlow(String username, Map<String, Object> payload);
    List<Map<String, Object>> listNifiFlowRuns(String username, Integer limit);
    List<Map<String, Object>> listNifiFlowTemplates(String username);
    Map<String, Object> saveNifiFlowTemplate(String username, Map<String, Object> payload);
    Map<String, Object> bootstrapNifiEtlTemplates(String username);
    Map<String, Object> provisionNifiFlowBlueprint(String username, Map<String, Object> payload);
    Map<String, Object> reconcileNifiRunningTasks(String username, Integer limit);
    Map<String, Object> reconcileNifiTask(String username, String taskType, Long taskId);
    Map<String, Object> repairCompletedTaskArtifacts(String username, String taskType, Integer limit);
    List<Map<String, Object>> listNifiReconcileRecords(String username, Integer limit);
    Map<String, Object> getLayerStats(String username, String taskType, Long taskId);
    List<Map<String, Object>> listLineageRecords(String username, String taskType, Long taskId);
    List<Map<String, Object>> listQualityReports(String username, String taskType, Long taskId);
    List<Map<String, Object>> listSnapshotRecords(String username, String taskType, Long taskId);
    List<Map<String, Object>> listAuditRecords(String username, Integer limit);
}

