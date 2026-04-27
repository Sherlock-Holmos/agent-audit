package com.audit.data.application;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 整改业务应用服务接口。
 */
public interface IRectificationApplicationService {

    Map<String, Object> snapshot(String username);

    Map<String, Object> createIssue(String username, Map<String, Object> payload);

    void deleteIssue(String username, Long issueId);

    Map<String, Object> addIssueSupervision(String username, Long issueId, Map<String, Object> payload);

    void deleteIssueSupervision(String username, Long issueId, Long supervisionId);

    Map<String, Object> shareIssue(String username, Long issueId, Map<String, Object> payload);

    List<Map<String, Object>> listIssueShares(String username, Long issueId);

    List<Map<String, Object>> listShareInbox(String username);

    Map<String, Object> acknowledgeShare(String username, Long shareId);

    Map<String, Object> submitShareFeedback(String username, Long shareId, Map<String, Object> payload);

    Map<String, Object> createTask(String username, Long issueId, Map<String, Object> payload);

    List<Map<String, Object>> splitIssueTasks(String username, Long issueId, Map<String, Object> payload);

    Map<String, Object> dispatchSubTask(String username, Long parentTaskId, Map<String, Object> payload);

    void deleteTask(String username, Long taskId);

    Map<String, Object> acceptTask(String username, Long taskId);

    Map<String, Object> claimTask(String username, Long taskId);

    Map<String, Object> submitTaskExecution(String username, Long taskId, Map<String, Object> payload);

    Map<String, Object> uploadTaskAttachment(String username, Long taskId, MultipartFile file);

    Map<String, Object> getTaskAttachment(String username, Long taskId, Integer attachmentIndex);

    Map<String, Object> reviewTask(String username, Long taskId, Map<String, Object> payload);

    Map<String, Object> updateTaskDeadline(String username, Long taskId, String deadline);

    Map<String, Object> addRule(String username, String name);

    Map<String, Object> updateRule(String username, Long ruleId, boolean enabled);

    List<Map<String, Object>> listReminderRules(String username);

    Map<String, Object> createReminderRule(String username, Map<String, Object> payload);

    Map<String, Object> updateReminderRule(String username, Long ruleId, Map<String, Object> payload);

    void deleteReminderRule(String username, Long ruleId);

    int runReminderScan(String username);

    List<Map<String, Object>> listUsers(String username);

    Map<String, Object> createUser(String username, Map<String, Object> payload);

    Map<String, Object> updateUser(String username, Long userId, Map<String, Object> payload);

    Map<String, Object> updateUserRole(String username, Long userId, String role);

    Map<String, Object> updateUserStatus(String username, Long userId, String status);

    void deleteUser(String username, Long userId);

    Map<String, Object> bindUserDepartment(String username, Long userId, String department);

    List<Map<String, Object>> listDepartments(String username);

    Map<String, Object> createDepartment(String username, String name);

    Map<String, Object> updateDepartment(String username, Long departmentId, String name);

    void deleteDepartment(String username, Long departmentId);

    List<Map<String, Object>> listDeletedUsers(String username);

    Map<String, Object> restoreUser(String username, Long userId);

    Map<String, Object> submitReport(String username, Map<String, Object> payload);

    List<Map<String, Object>> listNotifications(String username);

    void markNotificationRead(String username, Long notificationId);

    Map<String, Object> interactNotification(String username, Long notificationId, Map<String, Object> payload);

    List<Map<String, Object>> listOrgDepartments(String username);

    Map<String, Object> createOrgDepartment(String username, Map<String, Object> payload);

    Map<String, Object> updateOrgDepartment(String username, Long departmentId, Map<String, Object> payload);

    void deleteOrgDepartment(String username, Long departmentId);

    List<Map<String, Object>> listOrgDepartmentMembers(String username, String department);

    Map<String, Object> createOrgDepartmentMember(String username, Map<String, Object> payload);

    Map<String, Object> updateOrgDepartmentMember(String username, Long userId, Map<String, Object> payload);

    void deleteOrgDepartmentMember(String username, Long userId);
}
