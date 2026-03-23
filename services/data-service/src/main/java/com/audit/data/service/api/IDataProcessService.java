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

    // 清洗任务管理
    List<Map<String, Object>> listCleanTasks(String ownerUsername, String keyword, String sourceId, String status);
    Map<String, Object> createCleanTask(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> runCleanTask(String ownerUsername, Long id);
    void deleteCleanTask(String ownerUsername, Long id);

    // 融合任务管理
    List<Map<String, Object>> listFusionTasks(String ownerUsername, String keyword, String status);
    Map<String, Object> createFusionTask(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> runFusionTask(String ownerUsername, Long id);
    void deleteFusionTask(String ownerUsername, Long id);
    Map<String, Object> previewFusionTask(String ownerUsername, Long id, Integer limit);

    // 维护清理
    Map<String, Object> cleanupOrphanGeneratedTables(String ownerUsername);

    // 工作流编排
    Map<String, Object> runWorkflow(String ownerUsername, Map<String, Object> payload);

    // 数据治理
    List<Map<String, Object>> listLineageRecords(String ownerUsername, String taskType, Long taskId);
    List<Map<String, Object>> listQualityReports(String ownerUsername, String taskType, Long taskId);
    List<Map<String, Object>> listSnapshotRecords(String ownerUsername, String taskType, Long taskId);
    List<Map<String, Object>> listAuditRecords(String ownerUsername, Integer limit);
}

