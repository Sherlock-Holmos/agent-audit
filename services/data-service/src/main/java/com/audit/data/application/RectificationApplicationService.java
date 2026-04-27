package com.audit.data.application;

import com.audit.data.service.IRectificationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
/**
 * 整改业务应用服务。
 */
public class RectificationApplicationService implements IRectificationApplicationService {

    private static final String OWNER_SCOPE = "rectification_global";

    private final IRectificationService rectificationService;

    public RectificationApplicationService(IRectificationService rectificationService) {
        this.rectificationService = rectificationService;
    }

    @Override
    @Transactional
    public Map<String, Object> snapshot(String username) {
        return rectificationService.snapshot(OWNER_SCOPE, normalizeUser(username));
    }

    @Override
    @Transactional
    public Map<String, Object> createIssue(String username, Map<String, Object> payload) {
        return rectificationService.createIssue(OWNER_SCOPE, normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public void deleteIssue(String username, Long issueId) {
        rectificationService.deleteIssue(OWNER_SCOPE, normalizeUser(username), issueId);
    }

    @Override
    @Transactional
    public Map<String, Object> addIssueSupervision(String username, Long issueId, Map<String, Object> payload) {
        return rectificationService.addIssueSupervision(OWNER_SCOPE, normalizeUser(username), issueId, payload);
    }

    @Override
    @Transactional
    public void deleteIssueSupervision(String username, Long issueId, Long supervisionId) {
        rectificationService.deleteIssueSupervision(OWNER_SCOPE, issueId, supervisionId);
    }

    @Override
    @Transactional
    public Map<String, Object> shareIssue(String username, Long issueId, Map<String, Object> payload) {
        return rectificationService.shareIssue(OWNER_SCOPE, normalizeUser(username), issueId, payload);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listIssueShares(String username, Long issueId) {
        return rectificationService.listIssueShares(OWNER_SCOPE, issueId);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listShareInbox(String username) {
        return rectificationService.listShareInbox(OWNER_SCOPE, normalizeUser(username));
    }

    @Override
    @Transactional
    public Map<String, Object> acknowledgeShare(String username, Long shareId) {
        return rectificationService.acknowledgeShare(OWNER_SCOPE, normalizeUser(username), shareId);
    }

    @Override
    @Transactional
    public Map<String, Object> submitShareFeedback(String username, Long shareId, Map<String, Object> payload) {
        return rectificationService.submitShareFeedback(OWNER_SCOPE, normalizeUser(username), shareId, payload);
    }

    @Override
    @Transactional
    public Map<String, Object> createTask(String username, Long issueId, Map<String, Object> payload) {
        return rectificationService.createTask(OWNER_SCOPE, normalizeUser(username), issueId, payload);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> splitIssueTasks(String username, Long issueId, Map<String, Object> payload) {
        return rectificationService.splitIssueTasks(OWNER_SCOPE, normalizeUser(username), issueId, payload);
    }

    @Override
    @Transactional
    public Map<String, Object> dispatchSubTask(String username, Long parentTaskId, Map<String, Object> payload) {
        return rectificationService.dispatchSubTask(OWNER_SCOPE, normalizeUser(username), parentTaskId, payload);
    }

    @Override
    @Transactional
    public void deleteTask(String username, Long taskId) {
        rectificationService.deleteTask(OWNER_SCOPE, normalizeUser(username), taskId);
    }

    @Override
    @Transactional
    public Map<String, Object> acceptTask(String username, Long taskId) {
        return rectificationService.acceptTask(OWNER_SCOPE, normalizeUser(username), taskId);
    }

    @Override
    @Transactional
    public Map<String, Object> claimTask(String username, Long taskId) {
        return rectificationService.claimTask(OWNER_SCOPE, normalizeUser(username), taskId);
    }

    @Override
    @Transactional
    public Map<String, Object> submitTaskExecution(String username, Long taskId, Map<String, Object> payload) {
        return rectificationService.submitTaskExecution(OWNER_SCOPE, normalizeUser(username), taskId, payload);
    }

    @Override
    @Transactional
    public Map<String, Object> uploadTaskAttachment(String username, Long taskId, MultipartFile file) {
        return rectificationService.uploadTaskAttachment(OWNER_SCOPE, normalizeUser(username), taskId, file);
    }

    @Override
    @Transactional
    public Map<String, Object> getTaskAttachment(String username, Long taskId, Integer attachmentIndex) {
        return rectificationService.getTaskAttachment(OWNER_SCOPE, normalizeUser(username), taskId, attachmentIndex);
    }

    @Override
    @Transactional
    public Map<String, Object> reviewTask(String username, Long taskId, Map<String, Object> payload) {
        return rectificationService.reviewTask(OWNER_SCOPE, normalizeUser(username), taskId, payload);
    }

    @Override
    @Transactional
    public Map<String, Object> updateTaskDeadline(String username, Long taskId, String deadline) {
        return rectificationService.updateTaskDeadline(OWNER_SCOPE, taskId, deadline);
    }

    @Override
    @Transactional
    public Map<String, Object> addRule(String username, String name) {
        return rectificationService.addRule(OWNER_SCOPE, normalizeUser(username), name);
    }

    @Override
    @Transactional
    public Map<String, Object> updateRule(String username, Long ruleId, boolean enabled) {
        return rectificationService.updateRule(OWNER_SCOPE, ruleId, enabled);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listReminderRules(String username) {
        return rectificationService.listReminderRules(OWNER_SCOPE);
    }

    @Override
    @Transactional
    public Map<String, Object> createReminderRule(String username, Map<String, Object> payload) {
        return rectificationService.createReminderRule(OWNER_SCOPE, normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> updateReminderRule(String username, Long ruleId, Map<String, Object> payload) {
        return rectificationService.updateReminderRule(OWNER_SCOPE, normalizeUser(username), ruleId, payload);
    }

    @Override
    @Transactional
    public void deleteReminderRule(String username, Long ruleId) {
        rectificationService.deleteReminderRule(OWNER_SCOPE, normalizeUser(username), ruleId);
    }

    @Override
    @Transactional
    public int runReminderScan(String username) {
        return rectificationService.runReminderScan(OWNER_SCOPE);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listUsers(String username) {
        return rectificationService.listUsers(OWNER_SCOPE);
    }

    @Override
    @Transactional
    public Map<String, Object> createUser(String username, Map<String, Object> payload) {
        return rectificationService.createUser(OWNER_SCOPE, normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> updateUser(String username, Long userId, Map<String, Object> payload) {
        return rectificationService.updateUser(OWNER_SCOPE, normalizeUser(username), userId, payload);
    }

    @Override
    @Transactional
    public Map<String, Object> updateUserRole(String username, Long userId, String role) {
        return rectificationService.updateUserRole(OWNER_SCOPE, normalizeUser(username), userId, role);
    }

    @Override
    @Transactional
    public Map<String, Object> updateUserStatus(String username, Long userId, String status) {
        return rectificationService.updateUserStatus(OWNER_SCOPE, normalizeUser(username), userId, status);
    }

    @Override
    @Transactional
    public void deleteUser(String username, Long userId) {
        rectificationService.deleteUser(OWNER_SCOPE, normalizeUser(username), userId);
    }

    @Override
    @Transactional
    public Map<String, Object> bindUserDepartment(String username, Long userId, String department) {
        return rectificationService.bindUserDepartment(OWNER_SCOPE, normalizeUser(username), userId, department);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listDepartments(String username) {
        return rectificationService.listDepartments(OWNER_SCOPE);
    }

    @Override
    @Transactional
    public Map<String, Object> createDepartment(String username, String name) {
        return rectificationService.createDepartment(OWNER_SCOPE, normalizeUser(username), name);
    }

    @Override
    @Transactional
    public Map<String, Object> updateDepartment(String username, Long departmentId, String name) {
        return rectificationService.updateDepartment(OWNER_SCOPE, normalizeUser(username), departmentId, name);
    }

    @Override
    @Transactional
    public void deleteDepartment(String username, Long departmentId) {
        rectificationService.deleteDepartment(OWNER_SCOPE, normalizeUser(username), departmentId);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listDeletedUsers(String username) {
        return rectificationService.listDeletedUsers(OWNER_SCOPE);
    }

    @Override
    @Transactional
    public Map<String, Object> restoreUser(String username, Long userId) {
        return rectificationService.restoreUser(OWNER_SCOPE, normalizeUser(username), userId);
    }

    @Override
    @Transactional
    public Map<String, Object> submitReport(String username, Map<String, Object> payload) {
        return rectificationService.submitReport(OWNER_SCOPE, normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listNotifications(String username) {
        return rectificationService.listNotifications(OWNER_SCOPE, normalizeUser(username));
    }

    @Override
    @Transactional
    public void markNotificationRead(String username, Long notificationId) {
        rectificationService.markNotificationRead(OWNER_SCOPE, normalizeUser(username), notificationId);
    }

    @Override
    @Transactional
    public Map<String, Object> interactNotification(String username, Long notificationId, Map<String, Object> payload) {
        return rectificationService.interactNotification(OWNER_SCOPE, normalizeUser(username), notificationId, payload);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listOrgDepartments(String username) {
        return rectificationService.listOrgDepartments(OWNER_SCOPE, normalizeUser(username));
    }

    @Override
    @Transactional
    public Map<String, Object> createOrgDepartment(String username, Map<String, Object> payload) {
        return rectificationService.createOrgDepartment(OWNER_SCOPE, normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> updateOrgDepartment(String username, Long departmentId, Map<String, Object> payload) {
        return rectificationService.updateOrgDepartment(OWNER_SCOPE, normalizeUser(username), departmentId, payload);
    }

    @Override
    @Transactional
    public void deleteOrgDepartment(String username, Long departmentId) {
        rectificationService.deleteOrgDepartment(OWNER_SCOPE, normalizeUser(username), departmentId);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listOrgDepartmentMembers(String username, String department) {
        return rectificationService.listOrgDepartmentMembers(OWNER_SCOPE, normalizeUser(username), department);
    }

    @Override
    @Transactional
    public Map<String, Object> createOrgDepartmentMember(String username, Map<String, Object> payload) {
        return rectificationService.createOrgDepartmentMember(OWNER_SCOPE, normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> updateOrgDepartmentMember(String username, Long userId, Map<String, Object> payload) {
        return rectificationService.updateOrgDepartmentMember(OWNER_SCOPE, normalizeUser(username), userId, payload);
    }

    @Override
    @Transactional
    public void deleteOrgDepartmentMember(String username, Long userId) {
        rectificationService.deleteOrgDepartmentMember(OWNER_SCOPE, normalizeUser(username), userId);
    }

    private String normalizeUser(String username) {
        return (username == null || username.isBlank()) ? "anonymous" : username.trim();
    }
}
