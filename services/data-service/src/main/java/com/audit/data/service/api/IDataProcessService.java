package com.audit.data.service.api;

import java.util.List;
import java.util.Map;

/**
 * 数据处理业务接口：清洗、融合、规则与治理
 */
public interface IDataProcessService {

    // 清洗规则管理
    List<Map<String, Object>> listCleanRules(String ownerUsername);
    Map<String, Object> uploadCleanRule(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> toggleCleanRule(String ownerUsername, Long id, boolean enabled);
    Map<String, Object> getCleanRuleDetail(String ownerUsername, Long id);
    Map<String, Object> updateCleanRule(String ownerUsername, Long id, Map<String, Object> payload);
    void deleteCleanRule(String ownerUsername, Long id);

    // 清洗策略管理
    List<Map<String, Object>> listCleanStrategies(String ownerUsername);
    Map<String, Object> createCleanStrategy(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> getCleanStrategyDetail(String ownerUsername, Long id);
    Map<String, Object> updateCleanStrategy(String ownerUsername, Long id, Map<String, Object> payload);
    Map<String, Object> toggleCleanStrategy(String ownerUsername, Long id, boolean enabled);
    void deleteCleanStrategy(String ownerUsername, Long id);

    // 融合主键同义词管理
    List<Map<String, Object>> listFusionKeySynonyms(String ownerUsername);
    Map<String, Object> createFusionKeySynonym(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> getFusionKeySynonymDetail(String ownerUsername, Long id);
    Map<String, Object> updateFusionKeySynonym(String ownerUsername, Long id, Map<String, Object> payload);
    Map<String, Object> toggleFusionKeySynonym(String ownerUsername, Long id, boolean enabled);
    void deleteFusionKeySynonym(String ownerUsername, Long id);
    List<Map<String, Object>> listFusionKeySynonymHistory(String ownerUsername, Long id, Integer limit);
    List<Map<String, Object>> listFusionKeySynonymHistoryByCanonicalKey(String ownerUsername, String canonicalKey, Integer limit);

    // 清洗任务管理
    List<Map<String, Object>> listCleanTasks(String ownerUsername, String keyword, String sourceId, String status);
    Map<String, Object> createCleanTask(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> updateCleanTask(String ownerUsername, Long id, Map<String, Object> payload);
    Map<String, Object> runCleanTask(String ownerUsername, Long id);
    Map<String, Object> previewCleanTask(String ownerUsername, Long id, Integer limit);
    void deleteCleanTask(String ownerUsername, Long id);

    // 融合任务管理
    List<Map<String, Object>> listFusionTasks(String ownerUsername, String keyword, String status);
    Map<String, Object> createFusionTask(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> updateFusionTask(String ownerUsername, Long id, Map<String, Object> payload);
    Map<String, Object> runFusionTask(String ownerUsername, Long id);
    void deleteFusionTask(String ownerUsername, Long id);
    Map<String, Object> previewFusionTask(String ownerUsername, Long id, Integer limit);

    // 维护清理
    Map<String, Object> cleanupOrphanGeneratedTables(String ownerUsername);

    // 工作流编排
    Map<String, Object> runWorkflow(String ownerUsername, Map<String, Object> payload);

    // NiFi 控制平面
    Map<String, Object> getNifiStatus(String ownerUsername);
    Map<String, Object> triggerNifiFlow(String ownerUsername, Map<String, Object> payload);
    List<Map<String, Object>> listNifiFlowRuns(String ownerUsername, Integer limit);
    List<Map<String, Object>> listNifiFlowTemplates(String ownerUsername);
    Map<String, Object> saveNifiFlowTemplate(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> bootstrapNifiEtlTemplates(String ownerUsername);
    Map<String, Object> provisionNifiFlowBlueprint(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> reconcileNifiRunningTasks(String ownerUsername, Integer limit);
    Map<String, Object> reconcileNifiTask(String ownerUsername, String taskType, Long taskId);
    Map<String, Object> repairCompletedTaskArtifacts(String ownerUsername, String taskType, Integer limit);
    List<Map<String, Object>> listNifiReconcileRecords(String ownerUsername, Integer limit);
    Map<String, Object> getLayerStats(String ownerUsername, String taskType, Long taskId);

    // 数据治理
    List<Map<String, Object>> listLineageRecords(String ownerUsername, String taskType, Long taskId);
    List<Map<String, Object>> listQualityReports(String ownerUsername, String taskType, Long taskId);
    List<Map<String, Object>> listSnapshotRecords(String ownerUsername, String taskType, Long taskId);
    List<Map<String, Object>> listAuditRecords(String ownerUsername, Integer limit);
}

