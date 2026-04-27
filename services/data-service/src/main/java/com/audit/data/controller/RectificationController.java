package com.audit.data.controller;

import com.audit.data.application.IRectificationApplicationService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/data/rectification")
/**
 * 整改业务接口。
 */
public class RectificationController {

    private final IRectificationApplicationService rectificationApplicationService;

    public RectificationController(IRectificationApplicationService rectificationApplicationService) {
        this.rectificationApplicationService = rectificationApplicationService;
    }

    @GetMapping("/snapshot")
    public ApiResponse<Object> snapshot(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.snapshot(username));
    }

    @PostMapping("/issues")
    public ApiResponse<Object> createIssue(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.createIssue(username, payload));
    }

    @DeleteMapping("/issues/{issueId}")
    public ApiResponse<Void> deleteIssue(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "问题ID不能为空") @Positive(message = "问题ID必须大于0") @PathVariable Long issueId
    ) {
        rectificationApplicationService.deleteIssue(username, issueId);
        return ApiResponse.success("删除成功");
    }

    @PostMapping("/issues/{issueId}/supervisions")
    public ApiResponse<Object> addIssueSupervision(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "问题ID不能为空") @Positive(message = "问题ID必须大于0") @PathVariable Long issueId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.addIssueSupervision(username, issueId, payload));
    }

    @DeleteMapping("/issues/{issueId}/supervisions/{supervisionId}")
    public ApiResponse<Void> deleteIssueSupervision(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "问题ID不能为空") @Positive(message = "问题ID必须大于0") @PathVariable Long issueId,
        @NotNull(message = "督办ID不能为空") @Positive(message = "督办ID必须大于0") @PathVariable Long supervisionId
    ) {
        rectificationApplicationService.deleteIssueSupervision(username, issueId, supervisionId);
        return ApiResponse.success("删除成功");
    }

    @PostMapping("/issues/{issueId}/shares")
    public ApiResponse<Object> shareIssue(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "问题ID不能为空") @Positive(message = "问题ID必须大于0") @PathVariable Long issueId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("分享成功", rectificationApplicationService.shareIssue(username, issueId, payload));
    }

    @GetMapping("/issues/{issueId}/shares")
    public ApiResponse<Object> listIssueShares(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "问题ID不能为空") @Positive(message = "问题ID必须大于0") @PathVariable Long issueId
    ) {
        return ApiResponse.success("ok", rectificationApplicationService.listIssueShares(username, issueId));
    }

    @GetMapping("/shares/inbox")
    public ApiResponse<Object> listShareInbox(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.listShareInbox(username));
    }

    @PostMapping("/shares/{shareId}/ack")
    public ApiResponse<Object> acknowledgeShare(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "分享ID不能为空") @Positive(message = "分享ID必须大于0") @PathVariable Long shareId
    ) {
        return ApiResponse.success("确认成功", rectificationApplicationService.acknowledgeShare(username, shareId));
    }

    @PostMapping("/shares/{shareId}/feedback")
    public ApiResponse<Object> submitShareFeedback(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "分享ID不能为空") @Positive(message = "分享ID必须大于0") @PathVariable Long shareId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("提交成功", rectificationApplicationService.submitShareFeedback(username, shareId, payload));
    }

    @PostMapping("/issues/{issueId}/tasks")
    public ApiResponse<Object> createTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "问题ID不能为空") @Positive(message = "问题ID必须大于0") @PathVariable Long issueId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.createTask(username, issueId, payload));
    }

    @PostMapping("/issues/{issueId}/split-tasks")
    public ApiResponse<Object> splitIssueTasks(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "问题ID不能为空") @Positive(message = "问题ID必须大于0") @PathVariable Long issueId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.splitIssueTasks(username, issueId, payload));
    }

    @PostMapping("/tasks/{taskId}/subtasks")
    public ApiResponse<Object> dispatchSubTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.dispatchSubTask(username, taskId, payload));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId
    ) {
        rectificationApplicationService.deleteTask(username, taskId);
        return ApiResponse.success("删除成功");
    }

    @PostMapping("/tasks/{taskId}/accept")
    public ApiResponse<Object> acceptTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.acceptTask(username, taskId));
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ApiResponse<Object> claimTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.claimTask(username, taskId));
    }

    @PostMapping("/tasks/{taskId}/execution")
    public ApiResponse<Object> submitTaskExecution(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.submitTaskExecution(username, taskId, payload));
    }

    @PostMapping(value = "/tasks/{taskId}/attachments", consumes = "multipart/form-data")
    public ApiResponse<Object> uploadTaskAttachment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId,
        @NotNull(message = "上传文件不能为空") @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success("上传成功", rectificationApplicationService.uploadTaskAttachment(username, taskId, file));
    }

    @SuppressWarnings("null")
    @GetMapping("/tasks/{taskId}/attachments/{attachmentIndex}")
    public ResponseEntity<Resource> downloadTaskAttachment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId,
        @NotNull(message = "附件序号不能为空") @Positive(message = "附件序号必须大于0") @PathVariable Integer attachmentIndex
    ) {
        Map<String, Object> attachment = rectificationApplicationService.getTaskAttachment(username, taskId, attachmentIndex);
        String filePath = String.valueOf(attachment.getOrDefault("filePath", "")).trim();
        String downloadName = String.valueOf(attachment.getOrDefault("downloadName", "attachment")).trim();
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new IllegalArgumentException("附件文件不存在");
        }

        Resource resource = new FileSystemResource(path.toFile());
        String contentType = null;
        try {
            contentType = Files.probeContentType(path);
        } catch (Exception ignore) {
            // fall back to octet-stream
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName.replace('"', '_') + "\"")
            .body(resource);
    }

    @PostMapping("/tasks/{taskId}/review")
    public ApiResponse<Object> reviewTask(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.reviewTask(username, taskId, payload));
    }

    @PatchMapping("/tasks/{taskId}/deadline")
    public ApiResponse<Object> updateTaskDeadline(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "任务ID不能为空") @Positive(message = "任务ID必须大于0") @PathVariable Long taskId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateTaskDeadline(username, taskId, payload.get("deadline")));
    }

    @GetMapping("/users")
    public ApiResponse<Object> listUsers(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.listUsers(username));
    }

    @PostMapping("/users")
    public ApiResponse<Object> createUser(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.createUser(username, payload));
    }

    @PatchMapping("/users/{userId}")
    public ApiResponse<Object> updateUser(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateUser(username, userId, payload));
    }

    @PatchMapping("/users/{userId}/role")
    public ApiResponse<Object> updateUserRole(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateUserRole(username, userId, payload.getOrDefault("role", "")));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<Object> updateUserStatus(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateUserStatus(username, userId, payload.getOrDefault("status", "")));
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId
    ) {
        rectificationApplicationService.deleteUser(username, userId);
        return ApiResponse.success("删除成功");
    }

    @PatchMapping("/users/{userId}/department")
    public ApiResponse<Object> bindUserDepartment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("绑定成功", rectificationApplicationService.bindUserDepartment(username, userId, payload.getOrDefault("department", "")));
    }

    @GetMapping("/departments")
    public ApiResponse<Object> listDepartments(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.listDepartments(username));
    }

    @PostMapping("/departments")
    public ApiResponse<Object> createDepartment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.createDepartment(username, payload.getOrDefault("name", "")));
    }

    @PatchMapping("/departments/{departmentId}")
    public ApiResponse<Object> updateDepartment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "部门ID不能为空") @Positive(message = "部门ID必须大于0") @PathVariable Long departmentId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateDepartment(username, departmentId, payload.getOrDefault("name", "")));
    }

    @DeleteMapping("/departments/{departmentId}")
    public ApiResponse<Void> deleteDepartment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "部门ID不能为空") @Positive(message = "部门ID必须大于0") @PathVariable Long departmentId
    ) {
        rectificationApplicationService.deleteDepartment(username, departmentId);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/users/deleted")
    public ApiResponse<Object> listDeletedUsers(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.listDeletedUsers(username));
    }

    @PostMapping("/users/{userId}/restore")
    public ApiResponse<Object> restoreUser(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId
    ) {
        return ApiResponse.success("恢复成功", rectificationApplicationService.restoreUser(username, userId));
    }

    @PostMapping("/rules")
    public ApiResponse<Object> addRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.addRule(username, payload.getOrDefault("name", "")));
    }

    @PatchMapping("/rules/{ruleId}")
    public ApiResponse<Object> updateRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "规则ID不能为空") @Positive(message = "规则ID必须大于0") @PathVariable Long ruleId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("enabled", Boolean.FALSE)));
        return ApiResponse.success("更新成功", rectificationApplicationService.updateRule(username, ruleId, enabled));
    }

    @GetMapping("/reminder-rules")
    public ApiResponse<Object> listReminderRules(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.listReminderRules(username));
    }

    @PostMapping("/reminder-rules")
    public ApiResponse<Object> createReminderRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.createReminderRule(username, payload));
    }

    @PatchMapping("/reminder-rules/{ruleId}")
    public ApiResponse<Object> updateReminderRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "规则ID不能为空") @Positive(message = "规则ID必须大于0") @PathVariable Long ruleId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateReminderRule(username, ruleId, payload));
    }

    @DeleteMapping("/reminder-rules/{ruleId}")
    public ApiResponse<Void> deleteReminderRule(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "规则ID不能为空") @Positive(message = "规则ID必须大于0") @PathVariable Long ruleId
    ) {
        rectificationApplicationService.deleteReminderRule(username, ruleId);
        return ApiResponse.success("删除成功");
    }

    @PostMapping("/reminder-rules/scan")
    public ApiResponse<Object> runReminderScan(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("执行成功", Map.of("count", rectificationApplicationService.runReminderScan(username)));
    }

    @PostMapping("/reports")
    public ApiResponse<Object> submitReport(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("提交成功", rectificationApplicationService.submitReport(username, payload));
    }

    @GetMapping("/notifications")
    public ApiResponse<Object> listNotifications(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.listNotifications(username));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<Void> markNotificationRead(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "通知ID不能为空") @Positive(message = "通知ID必须大于0") @PathVariable Long notificationId
    ) {
        rectificationApplicationService.markNotificationRead(username, notificationId);
        return ApiResponse.success("更新成功");
    }

    @PostMapping("/notifications/{notificationId}/interact")
    public ApiResponse<Object> interactNotification(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "通知ID不能为空") @Positive(message = "通知ID必须大于0") @PathVariable Long notificationId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.interactNotification(username, notificationId, payload));
    }

    @GetMapping("/org-admin/departments")
    public ApiResponse<Object> listOrgDepartments(@RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.success("ok", rectificationApplicationService.listOrgDepartments(username));
    }

    @PostMapping("/org-admin/departments")
    public ApiResponse<Object> createOrgDepartment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.createOrgDepartment(username, payload));
    }

    @PatchMapping("/org-admin/departments/{departmentId}")
    public ApiResponse<Object> updateOrgDepartment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "部门ID不能为空") @Positive(message = "部门ID必须大于0") @PathVariable Long departmentId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateOrgDepartment(username, departmentId, payload));
    }

    @DeleteMapping("/org-admin/departments/{departmentId}")
    public ApiResponse<Void> deleteOrgDepartment(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "部门ID不能为空") @Positive(message = "部门ID必须大于0") @PathVariable Long departmentId
    ) {
        rectificationApplicationService.deleteOrgDepartment(username, departmentId);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/org-admin/members")
    public ApiResponse<Object> listOrgDepartmentMembers(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String department
    ) {
        return ApiResponse.success("ok", rectificationApplicationService.listOrgDepartmentMembers(username, department));
    }

    @PostMapping("/org-admin/members")
    public ApiResponse<Object> createOrgDepartmentMember(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", rectificationApplicationService.createOrgDepartmentMember(username, payload));
    }

    @PatchMapping("/org-admin/members/{userId}")
    public ApiResponse<Object> updateOrgDepartmentMember(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("更新成功", rectificationApplicationService.updateOrgDepartmentMember(username, userId, payload));
    }

    @DeleteMapping("/org-admin/members/{userId}")
    public ApiResponse<Void> deleteOrgDepartmentMember(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "用户ID不能为空") @Positive(message = "用户ID必须大于0") @PathVariable Long userId
    ) {
        rectificationApplicationService.deleteOrgDepartmentMember(username, userId);
        return ApiResponse.success("删除成功");
    }
}
