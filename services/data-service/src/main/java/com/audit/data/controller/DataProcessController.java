package com.audit.data.controller;

import com.audit.data.application.IDataProcessApplicationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/data")
public class DataProcessController {

    private final IDataProcessApplicationService dataProcessApplicationService;

    public DataProcessController(IDataProcessApplicationService dataProcessApplicationService) {
        this.dataProcessApplicationService = dataProcessApplicationService;
    }

    @GetMapping("/clean/tasks")
    public ResponseEntity<Map<String, Object>> listCleanTasks(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String sourceId,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listCleanTasks(username, keyword, sourceId, status)
        ));
    }

    @GetMapping("/clean/rules")
    public ResponseEntity<Map<String, Object>> listCleanRules(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listCleanRules(username)
        ));
    }

    @PostMapping("/clean/rules")
    public ResponseEntity<Map<String, Object>> uploadCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "上传成功",
                "data", dataProcessApplicationService.uploadCleanRule(username, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PatchMapping("/clean/rules/{id}/enabled")
    public ResponseEntity<Map<String, Object>> toggleCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "更新成功",
                "data", dataProcessApplicationService.toggleCleanRule(username, id, enabled)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @GetMapping("/clean/rules/{id}")
    public ResponseEntity<Map<String, Object>> getCleanRuleDetail(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "ok",
                "data", dataProcessApplicationService.getCleanRuleDetail(username, id)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PatchMapping("/clean/rules/{id}")
    public ResponseEntity<Map<String, Object>> updateCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "更新成功",
                "data", dataProcessApplicationService.updateCleanRule(username, id, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/clean/rules/{id}")
    public ResponseEntity<Map<String, Object>> deleteCleanRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            dataProcessApplicationService.deleteCleanRule(username, id);
            return ResponseEntity.ok(Map.of("code", 0, "message", "删除成功"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @GetMapping("/clean/strategies")
    public ResponseEntity<Map<String, Object>> listCleanStrategies(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listCleanStrategies(username)
        ));
    }

    @PostMapping("/clean/strategies")
    public ResponseEntity<Map<String, Object>> createCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "创建成功",
                "data", dataProcessApplicationService.createCleanStrategy(username, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @GetMapping("/clean/strategies/{id}")
    public ResponseEntity<Map<String, Object>> getCleanStrategyDetail(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "ok",
                "data", dataProcessApplicationService.getCleanStrategyDetail(username, id)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PatchMapping("/clean/strategies/{id}")
    public ResponseEntity<Map<String, Object>> updateCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "更新成功",
                "data", dataProcessApplicationService.updateCleanStrategy(username, id, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PatchMapping("/clean/strategies/{id}/enabled")
    public ResponseEntity<Map<String, Object>> toggleCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "更新成功",
                "data", dataProcessApplicationService.toggleCleanStrategy(username, id, enabled)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/clean/strategies/{id}")
    public ResponseEntity<Map<String, Object>> deleteCleanStrategy(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            dataProcessApplicationService.deleteCleanStrategy(username, id);
            return ResponseEntity.ok(Map.of("code", 0, "message", "删除成功"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PostMapping("/clean/tasks")
    public ResponseEntity<Map<String, Object>> createCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "创建成功",
                "data", dataProcessApplicationService.createCleanTask(username, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/clean/tasks/{id}/run")
    public ResponseEntity<Map<String, Object>> runCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "执行成功",
                "data", dataProcessApplicationService.runCleanTask(username, id)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/clean/tasks/{id}/run-async")
    public ResponseEntity<Map<String, Object>> runCleanTaskAsync(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "任务已提交",
                "data", dataProcessApplicationService.runCleanTaskAsync(username, id, idempotencyKey)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/clean/tasks/{id}")
    public ResponseEntity<Map<String, Object>> deleteCleanTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            dataProcessApplicationService.deleteCleanTask(username, id);
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "删除成功"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/fusion/tasks")
    public ResponseEntity<Map<String, Object>> listFusionTasks(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listFusionTasks(username, keyword, status)
        ));
    }

    @PostMapping("/fusion/tasks")
    public ResponseEntity<Map<String, Object>> createFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "创建成功",
                "data", dataProcessApplicationService.createFusionTask(username, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/fusion/tasks/{id}/run")
    public ResponseEntity<Map<String, Object>> runFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "执行成功",
                "data", dataProcessApplicationService.runFusionTask(username, id)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/fusion/tasks/{id}/preview")
    public ResponseEntity<Map<String, Object>> previewFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id,
        @RequestParam(required = false) Integer limit
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "ok",
                "data", dataProcessApplicationService.previewFusionTask(username, id, limit)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/fusion/tasks/{id}/run-async")
    public ResponseEntity<Map<String, Object>> runFusionTaskAsync(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "任务已提交",
                "data", dataProcessApplicationService.runFusionTaskAsync(username, id, idempotencyKey)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable String jobId
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "ok",
                "data", dataProcessApplicationService.getJobStatus(username, jobId)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/fusion/tasks/{id}")
    public ResponseEntity<Map<String, Object>> deleteFusionTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            dataProcessApplicationService.deleteFusionTask(username, id);
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "删除成功"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/maintenance/generated-tables/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupGeneratedTables(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "清理完成",
                "data", dataProcessApplicationService.cleanupOrphanGeneratedTables(username)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PostMapping("/workflows/run")
    public ResponseEntity<Map<String, Object>> runWorkflow(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "执行完成",
                "data", dataProcessApplicationService.runWorkflow(username, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @GetMapping("/governance/lineage")
    public ResponseEntity<Map<String, Object>> listLineage(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Long taskId
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listLineageRecords(username, taskType, taskId)
        ));
    }

    @GetMapping("/governance/quality")
    public ResponseEntity<Map<String, Object>> listQualityReports(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Long taskId
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listQualityReports(username, taskType, taskId)
        ));
    }

    @GetMapping("/governance/snapshots")
    public ResponseEntity<Map<String, Object>> listSnapshots(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) Long taskId
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listSnapshotRecords(username, taskType, taskId)
        ));
    }

    @GetMapping("/governance/audit")
    public ResponseEntity<Map<String, Object>> listAuditRecords(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessApplicationService.listAuditRecords(username, limit)
        ));
    }

}
