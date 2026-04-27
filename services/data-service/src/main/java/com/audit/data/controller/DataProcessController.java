package com.audit.data.controller;

import com.audit.data.application.IDataProcessApplicationService;
import com.audit.data.service.orchestration.DataProcessService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/data")
/**
 * 数据处理接口：清洗、融合、工作流与治理查询。
 */
public class DataProcessController {

    private final IDataProcessApplicationService dataProcessApplicationService;
    private final DataProcessService dataProcessService;

    public DataProcessController(
        IDataProcessApplicationService dataProcessApplicationService,
        DataProcessService dataProcessService
    ) {
        this.dataProcessApplicationService = dataProcessApplicationService;
        this.dataProcessService = dataProcessService;
    }

    @GetMapping("/clean/tasks")
    public ApiResponse<Object> listCleanTasks(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String sourceId,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listCleanTasks(username, keyword, sourceId, status));
    }

    @GetMapping("/clean/rules")
    public ApiResponse<Object> listCleanRules(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listCleanRules(username));
    }

    @PostMapping("/clean/rules")
    public ApiResponse<Object> uploadCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("上传成功", dataProcessApplicationService.uploadCleanRule(username, payload));
    }

    @PatchMapping("/clean/rules/{id}/enabled")
    public ApiResponse<Object> toggleCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "规则ID不能为空") @Positive(message = "规则ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
        return ApiResponse.success("更新成功", dataProcessApplicationService.toggleCleanRule(username, id, enabled));
    }

    @GetMapping("/clean/rules/{id}")
    public ApiResponse<Object> getCleanRuleDetail(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "规则ID不能为空") @Positive(message = "规则ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.getCleanRuleDetail(username, id));
    }

    @PatchMapping("/clean/rules/{id}")
    public ApiResponse<Object> updateCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "规则ID不能为空") @Positive(message = "规则ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", dataProcessApplicationService.updateCleanRule(username, id, payload));
    }

    @DeleteMapping("/clean/rules/{id}")
    public ApiResponse<Void> deleteCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "规则ID不能为空") @Positive(message = "规则ID必须大于0") @PathVariable Long id
    ) {
        dataProcessApplicationService.deleteCleanRule(username, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/clean/strategies")
    public ApiResponse<Object> listCleanStrategies(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listCleanStrategies(username));
    }

    @PostMapping("/clean/strategies")
    public ApiResponse<Object> createCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", dataProcessApplicationService.createCleanStrategy(username, payload));
    }

    @GetMapping("/clean/strategies/{id}")
    public ApiResponse<Object> getCleanStrategyDetail(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "策略ID不能为空") @Positive(message = "策略ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.getCleanStrategyDetail(username, id));
    }

    @PatchMapping("/clean/strategies/{id}")
    public ApiResponse<Object> updateCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "策略ID不能为空") @Positive(message = "策略ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", dataProcessApplicationService.updateCleanStrategy(username, id, payload));
    }

    @PatchMapping("/clean/strategies/{id}/enabled")
    public ApiResponse<Object> toggleCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "策略ID不能为空") @Positive(message = "策略ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
        return ApiResponse.success("更新成功", dataProcessApplicationService.toggleCleanStrategy(username, id, enabled));
    }

    @DeleteMapping("/clean/strategies/{id}")
    public ApiResponse<Void> deleteCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "策略ID不能为空") @Positive(message = "策略ID必须大于0") @PathVariable Long id
    ) {
        dataProcessApplicationService.deleteCleanStrategy(username, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/fusion/key-synonyms")
    public ApiResponse<Object> listFusionKeySynonyms(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listFusionKeySynonyms(username));
    }

    @PostMapping("/fusion/key-synonyms")
    public ApiResponse<Object> createFusionKeySynonym(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", dataProcessApplicationService.createFusionKeySynonym(username, payload));
    }

    @GetMapping("/fusion/key-synonyms/{id}")
    public ApiResponse<Object> getFusionKeySynonymDetail(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "映射ID不能为空") @Positive(message = "映射ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.getFusionKeySynonymDetail(username, id));
    }

    @PatchMapping("/fusion/key-synonyms/{id}")
    public ApiResponse<Object> updateFusionKeySynonym(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "映射ID不能为空") @Positive(message = "映射ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", dataProcessApplicationService.updateFusionKeySynonym(username, id, payload));
    }

    @PatchMapping("/fusion/key-synonyms/{id}/enabled")
    public ApiResponse<Object> toggleFusionKeySynonym(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "映射ID不能为空") @Positive(message = "映射ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
        return ApiResponse.success("更新成功", dataProcessApplicationService.toggleFusionKeySynonym(username, id, enabled));
    }

    @DeleteMapping("/fusion/key-synonyms/{id}")
    public ApiResponse<Void> deleteFusionKeySynonym(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "映射ID不能为空") @Positive(message = "映射ID必须大于0") @PathVariable Long id
    ) {
        dataProcessApplicationService.deleteFusionKeySynonym(username, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/fusion/key-synonyms/{id}/history")
    public ApiResponse<Object> listFusionKeySynonymHistory(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "映射ID不能为空") @Positive(message = "映射ID必须大于0") @PathVariable Long id,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listFusionKeySynonymHistory(username, id, limit));
    }

    @GetMapping("/fusion/key-synonyms/history")
    public ApiResponse<Object> listFusionKeySynonymHistoryByCanonicalKey(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotBlank(message = "标准主键不能为空") @RequestParam String canonicalKey,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listFusionKeySynonymHistoryByCanonicalKey(username, canonicalKey, limit));
    }

    @PostMapping("/clean/tasks")
    public ApiResponse<Object> createCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", dataProcessApplicationService.createCleanTask(username, payload));
    }

    @PatchMapping("/clean/tasks/{id}")
    public ApiResponse<Object> updateCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", dataProcessApplicationService.updateCleanTask(username, id, payload));
    }

    @PostMapping("/clean/tasks/{id}/run")
    public ApiResponse<Object> runCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("执行成功", dataProcessApplicationService.runCleanTask(username, id));
    }

    @GetMapping("/clean/tasks/{id}/preview")
    public ApiResponse<Object> previewCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.previewCleanTask(username, id, limit));
    }

    @PostMapping("/clean/tasks/{id}/run-async")
    public ApiResponse<Object> runCleanTaskAsync(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("任务已提交", dataProcessApplicationService.runCleanTaskAsync(username, id, idempotencyKey));
    }

    @DeleteMapping("/clean/tasks/{id}")
    public ApiResponse<Void> deleteCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id
    ) {
        dataProcessApplicationService.deleteCleanTask(username, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/fusion/tasks")
    public ApiResponse<Object> listFusionTasks(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listFusionTasks(username, keyword, status));
    }

    @PostMapping("/fusion/tasks")
    public ApiResponse<Object> createFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", dataProcessApplicationService.createFusionTask(username, payload));
    }

    @PatchMapping("/fusion/tasks/{id}")
    public ApiResponse<Object> updateFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", dataProcessApplicationService.updateFusionTask(username, id, payload));
    }

    @PostMapping("/fusion/tasks/{id}/run")
    public ApiResponse<Object> runFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("执行成功", dataProcessApplicationService.runFusionTask(username, id));
    }

    @GetMapping("/fusion/tasks/{id}/preview")
    public ApiResponse<Object> previewFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.previewFusionTask(username, id, limit));
    }

    @PostMapping("/fusion/tasks/{id}/run-async")
    public ApiResponse<Object> runFusionTaskAsync(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("任务已提交", dataProcessApplicationService.runFusionTaskAsync(username, id, idempotencyKey));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<Object> getJobStatus(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotBlank(message = "作业ID不能为空") @PathVariable String jobId
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.getJobStatus(username, jobId));
    }

    @DeleteMapping("/fusion/tasks/{id}")
    public ApiResponse<Void> deleteFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id
    ) {
        dataProcessApplicationService.deleteFusionTask(username, id);
        return ApiResponse.success("删除成功");
    }

    @PostMapping("/maintenance/generated-tables/cleanup")
    public ApiResponse<Object> cleanupGeneratedTables(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("清理完成", dataProcessApplicationService.cleanupOrphanGeneratedTables(username));
    }

    @PostMapping("/workflows/run")
    public ApiResponse<Object> runWorkflow(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("执行完成", dataProcessApplicationService.runWorkflow(username, payload));
    }

    @GetMapping("/control-plane/nifi/status")
    public ApiResponse<Object> getNifiStatus(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.getNifiStatus(username));
    }

    @PostMapping("/control-plane/nifi/flows/run")
    public ApiResponse<Object> triggerNifiFlow(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("已处理", dataProcessApplicationService.triggerNifiFlow(username, payload));
    }

    @GetMapping("/control-plane/nifi/flows")
    public ApiResponse<Object> listNifiFlowRuns(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listNifiFlowRuns(username, limit));
    }

    @GetMapping("/control-plane/nifi/templates")
    public ApiResponse<Object> listNifiFlowTemplates(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listNifiFlowTemplates(username));
    }

    @PostMapping("/control-plane/nifi/templates")
    public ApiResponse<Object> saveNifiFlowTemplate(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("已保存", dataProcessApplicationService.saveNifiFlowTemplate(username, payload));
    }

    @PostMapping("/control-plane/nifi/templates/bootstrap")
    public ApiResponse<Object> bootstrapNifiEtlTemplates(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("初始化完成", dataProcessApplicationService.bootstrapNifiEtlTemplates(username));
    }

    @PostMapping("/control-plane/nifi/flows/provision")
    public ApiResponse<Object> provisionNifiFlowBlueprint(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("已创建", dataProcessApplicationService.provisionNifiFlowBlueprint(username, payload));
    }

    @PostMapping("/control-plane/nifi/tasks/reconcile")
    public ApiResponse<Object> reconcileNifiRunningTasks(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("对账完成", dataProcessApplicationService.reconcileNifiRunningTasks(username, limit));
    }

    @PostMapping("/control-plane/nifi/tasks/reconcile/one")
    public ApiResponse<Object> reconcileNifiTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        String taskType = String.valueOf(payload.getOrDefault("taskType", "")).trim();
        Long taskId = payload.get("taskId") instanceof Number n ? n.longValue() : null;
        return ApiResponse.success("对账完成", dataProcessApplicationService.reconcileNifiTask(username, taskType, taskId));
    }

    @PostMapping(value = "/control-plane/nifi/callback/clean/poll", consumes = "*/*")
    public ApiResponse<Object> callbackCleanPoll() {
        // 兼容历史 NiFi callback URL，统一触发自动对账。
        dataProcessService.autoReconcileNifiRunningTasks();
        return ApiResponse.success("ok", Map.of("accepted", true, "taskType", "CLEAN"));
    }

    @PostMapping(value = "/control-plane/nifi/callback/fusion/poll", consumes = "*/*")
    public ApiResponse<Object> callbackFusionPoll() {
        // 兼容历史 NiFi callback URL，统一触发自动对账。
        dataProcessService.autoReconcileNifiRunningTasks();
        return ApiResponse.success("ok", Map.of("accepted", true, "taskType", "FUSION"));
    }

    @PostMapping("/control-plane/nifi/tasks/repair-artifacts")
    public ApiResponse<Object> repairCompletedTaskArtifacts(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("补偿完成", dataProcessApplicationService.repairCompletedTaskArtifacts(username, taskType, limit));
    }

    @GetMapping("/control-plane/nifi/tasks/reconcile/history")
    public ApiResponse<Object> listNifiReconcileRecords(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listNifiReconcileRecords(username, limit));
    }

    @DeleteMapping("/control-plane/nifi/tasks/reconcile/history/{id}")
    public ApiResponse<Object> deleteNifiReconcileRecord(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "历史记录ID不能为空") @Positive(message = "历史记录ID必须大于0") @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean stopRunningTask
    ) {
        String message = stopRunningTask ? "任务已停止并删除历史" : "历史已删除";
        return ApiResponse.success(message, dataProcessApplicationService.deleteNifiReconcileRecord(username, id, stopRunningTask));
    }

    @GetMapping("/control-plane/layers/stats")
    public ApiResponse<Object> getLayerStats(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Long taskId
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.getLayerStats(username, taskType, taskId));
    }

    @GetMapping("/governance/lineage")
    public ApiResponse<Object> listLineage(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Long taskId
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listLineageRecords(username, taskType, taskId));
    }

    @GetMapping("/governance/quality")
    public ApiResponse<Object> listQualityReports(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Long taskId
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listQualityReports(username, taskType, taskId));
    }

    @GetMapping("/governance/snapshots")
    public ApiResponse<Object> listSnapshots(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Long taskId
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listSnapshotRecords(username, taskType, taskId));
    }

    @GetMapping("/governance/audit")
    public ApiResponse<Object> listAuditRecords(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success("ok", dataProcessApplicationService.listAuditRecords(username, limit));
    }

}

