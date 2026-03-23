package com.audit.data.controller;

import com.audit.data.application.IDataProcessApplicationService;
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

    public DataProcessController(IDataProcessApplicationService dataProcessApplicationService) {
        this.dataProcessApplicationService = dataProcessApplicationService;
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

    @PostMapping("/clean/tasks")
    public ApiResponse<Object> createCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", dataProcessApplicationService.createCleanTask(username, payload));
    }

    @PostMapping("/clean/tasks/{id}/run")
    public ApiResponse<Object> runCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("执行成功", dataProcessApplicationService.runCleanTask(username, id));
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

