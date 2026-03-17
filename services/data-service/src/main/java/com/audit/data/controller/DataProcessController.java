package com.audit.data.controller;

import com.audit.data.service.IDataProcessService;
import com.audit.data.service.IDataProcessAsyncService;
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

    private final IDataProcessService dataProcessService;
    private final IDataProcessAsyncService dataProcessAsyncService;

    public DataProcessController(IDataProcessService dataProcessService, IDataProcessAsyncService dataProcessAsyncService) {
        this.dataProcessService = dataProcessService;
        this.dataProcessAsyncService = dataProcessAsyncService;
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
            "data", dataProcessService.listCleanTasks(user(username), keyword, sourceId, status)
        ));
    }

    @GetMapping("/clean/rules")
    public ResponseEntity<Map<String, Object>> listCleanRules(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ResponseEntity.ok(Map.of(
            "code", 0,
            "message", "ok",
            "data", dataProcessService.listCleanRules(user(username))
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
                "data", dataProcessService.uploadCleanRule(user(username), payload)
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
                "data", dataProcessService.toggleCleanRule(user(username), id, enabled)
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
                "data", dataProcessService.getCleanRuleDetail(user(username), id)
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
                "data", dataProcessService.updateCleanRule(user(username), id, payload)
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
            dataProcessService.deleteCleanRule(user(username), id);
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
            "data", dataProcessService.listCleanStrategies(user(username))
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
                "data", dataProcessService.createCleanStrategy(user(username), payload)
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
                "data", dataProcessService.getCleanStrategyDetail(user(username), id)
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
                "data", dataProcessService.updateCleanStrategy(user(username), id, payload)
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
                "data", dataProcessService.toggleCleanStrategy(user(username), id, enabled)
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
            dataProcessService.deleteCleanStrategy(user(username), id);
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
                "data", dataProcessService.createCleanTask(user(username), payload)
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
                "data", dataProcessService.runCleanTask(user(username), id)
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
                "data", dataProcessAsyncService.startCleanTask(user(username), id, idempotencyKey)
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
            dataProcessService.deleteCleanTask(user(username), id);
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
            "data", dataProcessService.listFusionTasks(user(username), keyword, status)
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
                "data", dataProcessService.createFusionTask(user(username), payload)
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
                "data", dataProcessService.runFusionTask(user(username), id)
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
                "data", dataProcessService.previewFusionTask(user(username), id, limit)
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
                "data", dataProcessAsyncService.startFusionTask(user(username), id, idempotencyKey)
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
                "data", dataProcessAsyncService.getJobStatus(user(username), jobId)
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
            dataProcessService.deleteFusionTask(user(username), id);
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

    private String user(String username) {
        return (username == null || username.isBlank()) ? "anonymous" : username;
    }
}
