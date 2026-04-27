package com.audit.data.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 整改业务服务接口。
 */
public interface IRectificationService {

    Map<String, Object> snapshot(String ownerKey, String viewerUsername);

    Map<String, Object> createIssue(String ownerKey, String actorUsername, Map<String, Object> payload);

    void deleteIssue(String ownerKey, String actorUsername, Long issueId);

    Map<String, Object> addIssueSupervision(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload);

    void deleteIssueSupervision(String ownerKey, Long issueId, Long supervisionId);

    Map<String, Object> shareIssue(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload);

    List<Map<String, Object>> listIssueShares(String ownerKey, Long issueId);

    List<Map<String, Object>> listShareInbox(String ownerKey, String actorUsername);

    Map<String, Object> acknowledgeShare(String ownerKey, String actorUsername, Long shareId);

    Map<String, Object> submitShareFeedback(String ownerKey, String actorUsername, Long shareId, Map<String, Object> payload);

    Map<String, Object> createTask(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload);

    List<Map<String, Object>> splitIssueTasks(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload);

    Map<String, Object> dispatchSubTask(String ownerKey, String actorUsername, Long parentTaskId, Map<String, Object> payload);

    void deleteTask(String ownerKey, String actorUsername, Long taskId);

    Map<String, Object> acceptTask(String ownerKey, String actorUsername, Long taskId);

    Map<String, Object> claimTask(String ownerKey, String actorUsername, Long taskId);

    Map<String, Object> submitTaskExecution(String ownerKey, String actorUsername, Long taskId, Map<String, Object> payload);

    Map<String, Object> uploadTaskAttachment(String ownerKey, String actorUsername, Long taskId, MultipartFile file);

    Map<String, Object> getTaskAttachment(String ownerKey, String actorUsername, Long taskId, Integer attachmentIndex);

    Map<String, Object> reviewTask(String ownerKey, String actorUsername, Long taskId, Map<String, Object> payload);

    Map<String, Object> updateTaskDeadline(String ownerKey, Long taskId, String deadline);

    Map<String, Object> addRule(String ownerKey, String actorUsername, String name);

    Map<String, Object> updateRule(String ownerKey, Long ruleId, boolean enabled);

    List<Map<String, Object>> listReminderRules(String ownerKey);

    Map<String, Object> createReminderRule(String ownerKey, String actorUsername, Map<String, Object> payload);

    Map<String, Object> updateReminderRule(String ownerKey, String actorUsername, Long ruleId, Map<String, Object> payload);

    void deleteReminderRule(String ownerKey, String actorUsername, Long ruleId);

    int runReminderScan(String ownerKey);

    List<Map<String, Object>> listUsers(String ownerKey);

    Map<String, Object> createUser(String ownerKey, String actorUsername, Map<String, Object> payload);

    Map<String, Object> updateUser(String ownerKey, String actorUsername, Long userId, Map<String, Object> payload);

    Map<String, Object> updateUserRole(String ownerKey, String actorUsername, Long userId, String role);

    Map<String, Object> updateUserStatus(String ownerKey, String actorUsername, Long userId, String status);

    void deleteUser(String ownerKey, String actorUsername, Long userId);

    Map<String, Object> bindUserDepartment(String ownerKey, String actorUsername, Long userId, String department);

    List<Map<String, Object>> listDepartments(String ownerKey);

    Map<String, Object> createDepartment(String ownerKey, String actorUsername, String name);

    Map<String, Object> updateDepartment(String ownerKey, String actorUsername, Long departmentId, String name);

    void deleteDepartment(String ownerKey, String actorUsername, Long departmentId);

    List<Map<String, Object>> listDeletedUsers(String ownerKey);

    Map<String, Object> restoreUser(String ownerKey, String actorUsername, Long userId);

    Map<String, Object> submitReport(String ownerKey, String actorUsername, Map<String, Object> payload);

    List<Map<String, Object>> listNotifications(String ownerKey, String username);

    void markNotificationRead(String ownerKey, String username, Long notificationId);

    Map<String, Object> interactNotification(String ownerKey, String username, Long notificationId, Map<String, Object> payload);

    List<Map<String, Object>> listOrgDepartments(String ownerKey, String actorUsername);

    Map<String, Object> createOrgDepartment(String ownerKey, String actorUsername, Map<String, Object> payload);

    Map<String, Object> updateOrgDepartment(String ownerKey, String actorUsername, Long departmentId, Map<String, Object> payload);

    void deleteOrgDepartment(String ownerKey, String actorUsername, Long departmentId);

    List<Map<String, Object>> listOrgDepartmentMembers(String ownerKey, String actorUsername, String department);

    Map<String, Object> createOrgDepartmentMember(String ownerKey, String actorUsername, Map<String, Object> payload);

    Map<String, Object> updateOrgDepartmentMember(String ownerKey, String actorUsername, Long userId, Map<String, Object> payload);

    void deleteOrgDepartmentMember(String ownerKey, String actorUsername, Long userId);
}
