package com.audit.data.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
/**
 * 整改业务服务实现（真实持久化）。
 */
public class RectificationService implements IRectificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Set<String> REMINDER_TRIGGER_TYPES = Set.of("BEFORE_DEADLINE", "OVERDUE", "INTERVAL_DAYS");
    private static final long MAX_ATTACHMENT_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_ATTACHMENT_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "txt", "png", "jpg", "jpeg", "zip");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;

    public RectificationService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        @Value("${app.datasource.upload-dir:../../data/uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        ensureUserUnitColumn();
        ensureIssueExtendedColumns();
        ensureOrgDepartmentTable();
    }

    @Override
    public Map<String, Object> snapshot(String ownerKey, String viewerUsername) {
        ensureSeed(ownerKey);
        Map<String, Object> result = new HashMap<>();
        result.put("issues", listIssues(ownerKey));
        result.put("tasks", listTasks(ownerKey));
        result.put("rules", listRules(ownerKey));
        result.put("users", listUsers(ownerKey));
        result.put("departments", listDepartments(ownerKey));
        result.put("reports", listReports(ownerKey));
        result.put("notifications", listNotifications(ownerKey, viewerUsername));
        return result;
    }

    @Override
    public Map<String, Object> createIssue(String ownerKey, String actorUsername, Map<String, Object> payload) {
        Map<String, Object> actor = ensureActorWithRoles(ownerKey, actorUsername, Set.of("AUDITOR", "AUDIT_ADMIN"));
        String title = sanitizeBusinessText(payload.get("title"));
        String unit = sanitizeDepartmentName(payload.get("unit"));
        if (isBlank(title) || isBlank(unit)) {
            throw new IllegalArgumentException("问题标题和被审单位不能为空");
        }

        String level = defaultIfBlank(sanitizeBusinessText(payload.get("level")), "中");
        String description = sanitizeBusinessText(payload.get("description"));
        String regulationClause = sanitizeBusinessText(payload.get("regulationClause"));
        List<String> evidenceList = toStringList(payload.get("evidenceList"));
        List<String> sanitizedEvidenceList = evidenceList.stream()
            .map(this::sanitizeBusinessText)
            .filter(item -> !isBlank(item))
            .collect(Collectors.toList());
        String now = now();
        String code = nextIssueCode(ownerKey);

        Long id = insertAndGetId(
            """
            INSERT INTO rect_issue_record(owner_key, code, title, level, unit, description, evidence_json, regulation_clause, status, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, '待派发', ?, ?, ?)
            """,
            ownerKey,
            code,
            title,
            level,
            unit,
            description,
            toJson(sanitizedEvidenceList),
            regulationClause,
            text(actor.get("username")),
            now,
            now
        );
        Map<String, Object> issue = getIssueById(ownerKey, id);
        List<String> receivers = mergeUsers(
            listEnabledOrgAdminsByUnit(ownerKey, unit),
            listEnabledUsersByRole(ownerKey, "AUDITOR"),
            listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN")
        );
        createNotification(
            ownerKey,
            "ISSUE_CREATED",
            "发现新审计问题",
            "问题《" + title + "》已录入，涉及单位：" + unit + "，请尽快下达整改任务。",
            actorUsername,
            null,
            id,
            receivers
        );
        return issue;
    }

    @Override
    public void deleteIssue(String ownerKey, String actorUsername, Long issueId) {
        Map<String, Object> actor = ensureActorWithRoles(ownerKey, actorUsername, Set.of("AUDITOR", "AUDIT_ADMIN"));
        Map<String, Object> issue = getIssueById(ownerKey, issueId);
        String actorRole = text(actor.get("role"));
        if ("AUDITOR".equals(actorRole) && !text(actor.get("username")).equals(text(issue.get("createdBy")))) {
            throw new IllegalArgumentException("审计人员仅可删除本人录入的问题");
        }
        List<Long> taskIds = jdbcTemplate.query(
            "SELECT id FROM rect_task_record WHERE owner_key=? AND issue_id=?",
            (rs, i) -> rs.getLong(1),
            ownerKey,
            issueId
        );
        List<Long> shareIds = jdbcTemplate.query(
            "SELECT id FROM rect_issue_share_record WHERE owner_key=? AND issue_id=?",
            (rs, i) -> rs.getLong(1),
            ownerKey,
            issueId
        );

        if (!taskIds.isEmpty()) {
            String placeholders = taskIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Object> args = new ArrayList<>();
            args.add(ownerKey);
            args.addAll(taskIds);
            jdbcTemplate.update(
                "DELETE FROM rect_notification_receiver_record WHERE owner_key=? AND notification_id IN (SELECT id FROM rect_notification_record WHERE id IN (SELECT id FROM rect_notification_record WHERE related_task_id IN (" + placeholders + ")))",
                args.toArray()
            );
            jdbcTemplate.update(
                "DELETE FROM rect_notification_read_record WHERE owner_key=? AND notification_id IN (SELECT id FROM rect_notification_record WHERE related_task_id IN (" + placeholders + "))",
                args.toArray()
            );
            jdbcTemplate.update(
                "DELETE FROM rect_notification_interaction_record WHERE owner_key=? AND notification_id IN (SELECT id FROM rect_notification_record WHERE related_task_id IN (" + placeholders + "))",
                args.toArray()
            );
            jdbcTemplate.update(
                "DELETE FROM rect_notification_record WHERE owner_key=? AND related_task_id IN (" + placeholders + ")",
                args.toArray()
            );
        }

        if (!shareIds.isEmpty()) {
            String placeholders = shareIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Object> args = new ArrayList<>();
            args.add(ownerKey);
            args.addAll(shareIds);
            jdbcTemplate.update(
                "DELETE FROM rect_issue_share_feedback_record WHERE owner_key=? AND share_id IN (" + placeholders + ")",
                args.toArray()
            );
            jdbcTemplate.update(
                "DELETE FROM rect_issue_share_record WHERE owner_key=? AND id IN (" + placeholders + ")",
                args.toArray()
            );
        }

        jdbcTemplate.update("DELETE FROM rect_supervision_record WHERE owner_key=? AND issue_id=?", ownerKey, issueId);
        jdbcTemplate.update("DELETE FROM rect_task_record WHERE owner_key=? AND issue_id=?", ownerKey, issueId);
        jdbcTemplate.update("DELETE FROM rect_notification_receiver_record WHERE owner_key=? AND notification_id IN (SELECT id FROM rect_notification_record WHERE owner_key=? AND related_issue_id=?)", ownerKey, ownerKey, issueId);
        jdbcTemplate.update("DELETE FROM rect_notification_read_record WHERE owner_key=? AND notification_id IN (SELECT id FROM rect_notification_record WHERE owner_key=? AND related_issue_id=?)", ownerKey, ownerKey, issueId);
        jdbcTemplate.update("DELETE FROM rect_notification_interaction_record WHERE owner_key=? AND notification_id IN (SELECT id FROM rect_notification_record WHERE owner_key=? AND related_issue_id=?)", ownerKey, ownerKey, issueId);
        jdbcTemplate.update("DELETE FROM rect_notification_record WHERE owner_key=? AND related_issue_id=?", ownerKey, issueId);
        jdbcTemplate.update("DELETE FROM rect_issue_record WHERE owner_key=? AND id=?", ownerKey, issueId);
    }

    @Override
    public Map<String, Object> addIssueSupervision(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload) {
        Map<String, Object> actor = ensureActorWithRoles(ownerKey, actorUsername, Set.of("AUDITOR", "AUDIT_ADMIN", "ORG_ADMIN"));
        Map<String, Object> issue = getIssueById(ownerKey, issueId);
        String actorRole = text(actor.get("role"));
        String issueUnit = text(issue.get("unit"));
        Long taskId = toLong(payload.get("taskId"));
        if ("ORG_ADMIN".equals(actorRole)) {
            if (!text(actor.get("unit")).equals(issueUnit)) {
                throw new IllegalArgumentException("仅可对本单位问题发起督办");
            }
            if (taskId == null) {
                throw new IllegalArgumentException("被审计单位管理员只能督办自己派发的子任务");
            }
            Map<String, Object> task = getTaskById(ownerKey, taskId);
            if (toLong(task.get("parentId")) == null) {
                throw new IllegalArgumentException("被审计单位管理员不能督办主任务");
            }
            if (!actorUsername.equals(text(task.get("createdBy")))) {
                throw new IllegalArgumentException("被审计单位管理员只能督办自己派发的子任务");
            }
            if (!text(actor.get("unit")).equals(text(task.get("unit")))) {
                throw new IllegalArgumentException("仅可督办本单位子任务");
            }
        }
        String note = text(payload.get("note"));
        if (isBlank(note)) {
            throw new IllegalArgumentException("督办说明不能为空");
        }
        String supervisor = actorUsername;
        String now = now();

        insertAndGetId(
            "INSERT INTO rect_supervision_record(owner_key, issue_id, note, supervisor, created_at) VALUES (?, ?, ?, ?, ?)",
            ownerKey,
            issueId,
            note,
            supervisor,
            now
        );

        List<String> receivers;
        if ("ORG_ADMIN".equals(actorRole)) {
            receivers = mergeUsers(
                listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"),
                listEnabledUsersByRole(ownerKey, "AUDITOR"),
                listEnabledUsersByRoleAndUnit(ownerKey, "ORG_OPERATOR", issueUnit),
                listIssueTaskReceivers(ownerKey, issueId),
                List.of(actorUsername)
            );
        } else {
            receivers = mergeUsers(
                listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"),
                listEnabledUsersByRole(ownerKey, "AUDITOR"),
                listEnabledOrgAdminsByUnit(ownerKey, issueUnit),
                listIssueTaskReceivers(ownerKey, issueId),
                List.of(actorUsername)
            );
        }

        createNotification(
            ownerKey,
            "ISSUE_SUPERVISION",
            "重点问题督办通知",
            "问题《" + text(issue.get("title")) + "》收到新的督办要求：" + note,
            supervisor,
            taskId,
            issueId,
            receivers
        );
        return getIssueById(ownerKey, issueId);
    }

    @Override
    public void deleteIssueSupervision(String ownerKey, Long issueId, Long supervisionId) {
        getIssueById(ownerKey, issueId);
        jdbcTemplate.update(
            "DELETE FROM rect_supervision_record WHERE owner_key=? AND issue_id=? AND id=?",
            ownerKey,
            issueId,
            supervisionId
        );
    }

    @Override
    public Map<String, Object> shareIssue(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload) {
        Map<String, Object> issue = getIssueById(ownerKey, issueId);
        String toDepartment = text(payload.get("toDepartment"));
        String purpose = text(payload.get("purpose"));
        if (isBlank(toDepartment) || isBlank(purpose)) {
            throw new IllegalArgumentException("分享部门与运用目的不能为空");
        }
        String fromDepartment = defaultIfBlank(text(issue.get("unit")), getActorUnit(ownerKey, actorUsername));

        Long shareId = insertAndGetId(
            "INSERT INTO rect_issue_share_record(owner_key, issue_id, from_department, to_department, purpose, status, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)",
            ownerKey,
            issueId,
            fromDepartment,
            toDepartment,
            purpose,
            actorUsername,
            now(),
            now()
        );

        createNotification(
            ownerKey,
            "ISSUE_SHARED",
            "整改问题成果共享",
            "问题《" + text(issue.get("title")) + "》已分享至部门【" + toDepartment + "】，请确认接收并反馈运用情况。",
            actorUsername,
            null,
            issueId,
            mergeUsers(
                listEnabledUsersByUnit(ownerKey, toDepartment),
                listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN")
            )
        );

        return getShareById(ownerKey, shareId);
    }

    @Override
    public List<Map<String, Object>> listIssueShares(String ownerKey, Long issueId) {
        getIssueById(ownerKey, issueId);
        List<Map<String, Object>> shares = jdbcTemplate.query(
            """
            SELECT s.id, s.issue_id, i.title AS issue_title, s.from_department, s.to_department,
                   s.purpose, s.status, s.created_by, s.created_at, s.updated_at
              FROM rect_issue_share_record s
              JOIN rect_issue_record i ON i.owner_key=s.owner_key AND i.id=s.issue_id
             WHERE s.owner_key=? AND s.issue_id=?
             ORDER BY s.id DESC
            """,
            (rs, i) -> toShareView(rs),
            ownerKey,
            issueId
        );
        for (Map<String, Object> share : shares) {
            share.put("feedbacks", listShareFeedbacks(ownerKey, toLong(share.get("id"))));
        }
        return shares;
    }

    @Override
    public List<Map<String, Object>> listShareInbox(String ownerKey, String actorUsername) {
        String unit = getActorUnit(ownerKey, actorUsername);
        List<Map<String, Object>> shares = jdbcTemplate.query(
            """
            SELECT s.id, s.issue_id, i.title AS issue_title, s.from_department, s.to_department,
                   s.purpose, s.status, s.created_by, s.created_at, s.updated_at
              FROM rect_issue_share_record s
              JOIN rect_issue_record i ON i.owner_key=s.owner_key AND i.id=s.issue_id
             WHERE s.owner_key=? AND s.to_department=?
             ORDER BY s.id DESC
            """,
            (rs, i) -> toShareView(rs),
            ownerKey,
            unit
        );
        for (Map<String, Object> share : shares) {
            share.put("feedbacks", listShareFeedbacks(ownerKey, toLong(share.get("id"))));
        }
        return shares;
    }

    @Override
    public Map<String, Object> acknowledgeShare(String ownerKey, String actorUsername, Long shareId) {
        Map<String, Object> share = getShareById(ownerKey, shareId);
        ensureShareAccessible(ownerKey, actorUsername, share);
        jdbcTemplate.update(
            "UPDATE rect_issue_share_record SET status='ACKED', updated_at=? WHERE owner_key=? AND id=?",
            now(),
            ownerKey,
            shareId
        );

        createNotification(
            ownerKey,
            "ISSUE_SHARE_ACK",
            "成果共享已确认接收",
            "部门【" + text(share.get("toDepartment")) + "】已确认接收问题共享《" + text(share.get("issueTitle")) + "》。",
            actorUsername,
            null,
            toLong(share.get("issueId")),
            mergeUsers(List.of(text(share.get("createdBy"))), listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"))
        );
        return getShareById(ownerKey, shareId);
    }

    @Override
    public Map<String, Object> submitShareFeedback(String ownerKey, String actorUsername, Long shareId, Map<String, Object> payload) {
        Map<String, Object> share = getShareById(ownerKey, shareId);
        ensureShareAccessible(ownerKey, actorUsername, share);
        String feedback = text(payload.get("feedback"));
        if (isBlank(feedback)) {
            throw new IllegalArgumentException("成果运用反馈不能为空");
        }
        List<String> attachments = toStringList(payload.get("attachments"));

        insertAndGetId(
            "INSERT INTO rect_issue_share_feedback_record(owner_key, share_id, feedback_text, attachments_json, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            ownerKey,
            shareId,
            feedback,
            toJson(attachments),
            actorUsername,
            now()
        );

        jdbcTemplate.update(
            "UPDATE rect_issue_share_record SET status='APPLIED', updated_at=? WHERE owner_key=? AND id=?",
            now(),
            ownerKey,
            shareId
        );

        createNotification(
            ownerKey,
            "ISSUE_SHARE_FEEDBACK",
            "成果运用反馈已提交",
            "部门【" + text(share.get("toDepartment")) + "】已提交共享问题《" + text(share.get("issueTitle")) + "》的成果运用反馈。",
            actorUsername,
            null,
            toLong(share.get("issueId")),
            mergeUsers(List.of(text(share.get("createdBy"))), listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"))
        );
        return getShareById(ownerKey, shareId);
    }

    @Override
    public Map<String, Object> createTask(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload) {
        Map<String, Object> issue = getIssueById(ownerKey, issueId);
        String title = sanitizeBusinessText(payload.get("title"));
        String assignee = text(payload.get("assignee"));
        String deadline = text(payload.get("deadline"));
        if (isBlank(title) || isBlank(assignee) || isBlank(deadline)) {
            throw new IllegalArgumentException("任务标题、责任人、截止日期不能为空");
        }
        Long taskId = createMainTask(ownerKey, actorUsername, issueId, title, assignee, sanitizeDepartmentName(issue.get("unit")), deadline);
        return getTaskById(ownerKey, taskId);
    }

    @Override
    public List<Map<String, Object>> splitIssueTasks(String ownerKey, String actorUsername, Long issueId, Map<String, Object> payload) {
        getIssueById(ownerKey, issueId);
        List<Map<String, Object>> taskItems = toObjectList(payload.get("tasks"));
        if (taskItems.isEmpty()) {
            throw new IllegalArgumentException("请至少提供一条拆分任务");
        }

        List<Map<String, Object>> createdTasks = new ArrayList<>();
        for (Map<String, Object> item : taskItems) {
            String title = sanitizeBusinessText(item.get("title"));
            String unit = sanitizeDepartmentName(item.get("unit"));
            String assignee = text(item.get("assignee"));
            String deadline = text(item.get("deadline"));
            if (isBlank(title) || isBlank(unit) || isBlank(assignee) || isBlank(deadline)) {
                throw new IllegalArgumentException("拆分任务的标题、责任单位、责任人、截止日期不能为空");
            }

            Long taskId = createMainTask(ownerKey, actorUsername, issueId, title, assignee, unit, deadline);
            createdTasks.add(getTaskById(ownerKey, taskId));
        }
        return createdTasks;
    }

    @Override
    public Map<String, Object> dispatchSubTask(String ownerKey, String actorUsername, Long parentTaskId, Map<String, Object> payload) {
        Map<String, Object> parent = getTaskById(ownerKey, parentTaskId);
        if (toLong(parent.get("parentId")) != null) {
            throw new IllegalArgumentException("仅主任务允许派发子任务");
        }
        if (!Set.of("执行中", "待审核").contains(text(parent.get("status")))) {
            throw new IllegalArgumentException("主任务签收后才可派发子任务");
        }
        String title = text(payload.get("title"));
        String assignee = text(payload.get("assignee"));
        String deadline = defaultIfBlank(text(payload.get("deadline")), text(parent.get("deadline")));
        if (isBlank(title) || isBlank(assignee)) {
            throw new IllegalArgumentException("子任务标题和经办人不能为空");
        }
        ensureAssignableUser(ownerKey, assignee, "ORG_OPERATOR", text(parent.get("unit")));

        String now = now();
        Long taskId = insertAndGetId(
            """
            INSERT INTO rect_task_record(owner_key, issue_id, parent_id, title, unit, assignee, claimed_by, created_by,
              status, progress, deadline, review_status, review_comment, measure, attachments_json, feedback, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, '', ?, '待认领', 0, ?, '待审核', '', '', '[]', '', ?, ?)
            """,
            ownerKey,
            toLong(parent.get("issueId")),
            parentTaskId,
            title,
            text(parent.get("unit")),
            assignee,
            actorUsername,
            deadline,
            now,
            now
        );

        jdbcTemplate.update("UPDATE rect_task_record SET updated_at=? WHERE owner_key=? AND id=?", now, ownerKey, parentTaskId);

        createNotification(
            ownerKey,
            "SUBTASK_ASSIGNED",
            "收到新的整改子任务",
            "子任务《" + title + "》已派发，请尽快认领并执行。",
            actorUsername,
            taskId,
            toLong(parent.get("issueId")),
            mergeUsers(List.of(assignee, actorUsername), listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"))
        );
        return getTaskById(ownerKey, taskId);
    }

    @Override
    public void deleteTask(String ownerKey, String actorUsername, Long taskId) {
        Map<String, Object> actor = ensureActorWithRoles(ownerKey, actorUsername, Set.of("ORG_ADMIN"));
        Map<String, Object> task = getTaskById(ownerKey, taskId);
        if (toLong(task.get("parentId")) == null) {
            throw new IllegalArgumentException("仅可删除自己派发的子任务");
        }
        if (!text(actor.get("unit")).equals(text(task.get("unit")))) {
            throw new IllegalArgumentException("仅可删除本单位任务");
        }
        if (!actorUsername.equals(text(task.get("createdBy")))) {
            throw new IllegalArgumentException("仅可删除自己派发的子任务");
        }
        if ("已完成".equals(text(task.get("status")))) {
            throw new IllegalArgumentException("已完成的子任务不可删除");
        }

        deleteTaskNotifications(ownerKey, taskId);
        jdbcTemplate.update("DELETE FROM rect_task_record WHERE owner_key=? AND id=?", ownerKey, taskId);
        jdbcTemplate.update(
            "UPDATE rect_task_record SET updated_at=? WHERE owner_key=? AND id=?",
            now(),
            ownerKey,
            toLong(task.get("parentId"))
        );
    }

    @Override
    public Map<String, Object> acceptTask(String ownerKey, String actorUsername, Long taskId) {
        Map<String, Object> actor = ensureActorWithRoles(ownerKey, actorUsername, Set.of("ORG_ADMIN"));
        Map<String, Object> task = getTaskById(ownerKey, taskId);
        if (toLong(task.get("parentId")) != null) {
            throw new IllegalArgumentException("仅主任务支持签收");
        }
        if (!text(actor.get("unit")).equals(text(task.get("unit")))) {
            throw new IllegalArgumentException("仅可签收本单位任务");
        }
        if (!"待接收".equals(text(task.get("status")))) {
            throw new IllegalArgumentException("任务已签收，请勿重复签收");
        }
        String nextStatus = "待接收".equals(text(task.get("status"))) ? "执行中" : text(task.get("status"));
        String now = now();

        jdbcTemplate.update(
            "UPDATE rect_task_record SET status=?, claimed_by=IF(claimed_by='', ?, claimed_by), updated_at=? WHERE owner_key=? AND id=?",
            nextStatus,
            actorUsername,
            now,
            ownerKey,
            taskId
        );

        createNotification(
            ownerKey,
            "TASK_ACCEPTED",
            "任务签收回执",
            "任务《" + text(task.get("title")) + "》已被 " + actorUsername + " 签收。",
            actorUsername,
            taskId,
            toLong(task.get("issueId")),
            List.of(text(task.get("createdBy")))
        );
        return getTaskById(ownerKey, taskId);
    }

    @Override
    public Map<String, Object> claimTask(String ownerKey, String actorUsername, Long taskId) {
        Map<String, Object> task = getTaskById(ownerKey, taskId);
        if (toLong(task.get("parentId")) == null) {
            throw new IllegalArgumentException("仅子任务允许认领");
        }
        if (!"待认领".equals(text(task.get("status")))) {
            throw new IllegalArgumentException("任务已认领，请勿重复签收");
        }
        if (!actorUsername.equals(text(task.get("assignee")))) {
            throw new IllegalArgumentException("仅指派给当前账号的子任务允许认领");
        }
        ensureAssignableUser(ownerKey, actorUsername, "ORG_OPERATOR", text(task.get("unit")));
        String now = now();
        jdbcTemplate.update(
            "UPDATE rect_task_record SET claimed_by=?, status='执行中', updated_at=? WHERE owner_key=? AND id=?",
            actorUsername,
            now,
            ownerKey,
            taskId
        );

        createNotification(
            ownerKey,
            "TASK_CLAIMED",
            "子任务已被认领",
            "任务《" + text(task.get("title")) + "》已由 " + actorUsername + " 认领。",
            actorUsername,
            taskId,
            toLong(task.get("issueId")),
            List.of(text(task.get("createdBy")), text(task.get("assignee")))
        );
        return getTaskById(ownerKey, taskId);
    }

    @Override
    public Map<String, Object> submitTaskExecution(String ownerKey, String actorUsername, Long taskId, Map<String, Object> payload) {
        Map<String, Object> task = getTaskById(ownerKey, taskId);
        boolean mainTask = toLong(task.get("parentId")) == null;
        String measure = defaultIfBlank(text(payload.get("measure")), text(task.get("measure")));
        String feedback = defaultIfBlank(text(payload.get("feedback")), text(task.get("feedback")));
        List<Map<String, Object>> attachments = toAttachmentRecords(payload.get("attachments"));
        int progress = toInt(payload.get("progress"), toInt(task.get("progress"), 0));
        if (!mainTask && progress < 100) {
            progress = 100;
        }
        String status = progress >= 100 ? "待审核" : "执行中";
        String now = now();

        if (isBlank(measure)) {
            throw new IllegalArgumentException("请先填写整改措施");
        }
        if (!mainTask && attachments.isEmpty()) {
            throw new IllegalArgumentException("子任务提交需至少上传一份证明材料");
        }
        if (mainTask && progress >= 100) {
            ensureMainTaskReadyForAudit(ownerKey, taskId);
            if (isBlank(feedback)) {
                throw new IllegalArgumentException("主任务汇总提交时请填写执行反馈");
            }
        }

        jdbcTemplate.update(
            """
            UPDATE rect_task_record
               SET measure=?, feedback=?, attachments_json=?, progress=?, status=?, review_status='待审核', updated_at=?
             WHERE owner_key=? AND id=?
            """,
            measure,
            feedback,
            toJson(attachments),
            progress,
            status,
            now,
            ownerKey,
            taskId
        );

        if (mainTask && progress >= 100) {
            jdbcTemplate.update(
                "UPDATE rect_issue_record SET status='待审核', updated_at=? WHERE owner_key=? AND id=?",
                now,
                ownerKey,
                toLong(task.get("issueId"))
            );
        }

        createNotification(
            ownerKey,
            "TASK_PROGRESS",
            "整改任务有新进展",
            mainTask && progress >= 100
                ? "任务《" + text(task.get("title")) + "》已由单位管理员汇总提交，等待审计审核。"
                : "任务《" + text(task.get("title")) + "》执行进度已更新为 " + progress + "% 。",
            actorUsername,
            taskId,
            toLong(task.get("issueId")),
            mergeUsers(
                List.of(text(task.get("createdBy")), text(task.get("assignee")), text(task.get("claimedBy"))),
                listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"),
                mainTask && progress >= 100 ? listEnabledUsersByRole(ownerKey, "AUDITOR") : Collections.emptyList()
            )
        );
        return getTaskById(ownerKey, taskId);
    }

    @Override
    public Map<String, Object> uploadTaskAttachment(String ownerKey, String actorUsername, Long taskId, MultipartFile file) {
        Map<String, Object> task = getTaskById(ownerKey, taskId);
        if (!actorUsername.equals(text(task.get("assignee"))) && !actorUsername.equals(text(task.get("claimedBy")))) {
            throw new IllegalArgumentException("仅任务执行人可上传证明材料");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("上传文件不能超过20MB");
        }

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown");
        String fileExt = getFileExt(originalName);
        if (!ALLOWED_ATTACHMENT_EXT.contains(fileExt)) {
            throw new IllegalArgumentException("仅支持 pdf/doc/docx/xls/xlsx/csv/txt/png/jpg/jpeg/zip 文件");
        }

        String safeFileName = sanitizeFileName(originalName);
        String storedName = ownerKey + "_task_" + taskId + "_" + Instant.now().toEpochMilli() + "_" + safeFileName;
        Path savedFile;
        try {
            Files.createDirectories(uploadRoot);
            Path attachmentDir = uploadRoot.resolve("rectification").resolve("task-attachments");
            Files.createDirectories(attachmentDir);
            savedFile = attachmentDir.resolve(storedName);
            file.transferTo(Objects.requireNonNull(savedFile));
        } catch (IOException ex) {
            throw new IllegalArgumentException("文件保存失败: " + ex.getMessage());
        } catch (Exception ex) {
            throw new IllegalArgumentException("文件保存失败: " + ex.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileName", originalName);
        result.put("storedName", storedName);
        result.put("filePath", savedFile.toString());
        result.put("size", file.getSize());
        result.put("uploadedAt", now());
        return result;
    }

    @Override
    public Map<String, Object> reviewTask(String ownerKey, String actorUsername, Long taskId, Map<String, Object> payload) {
        Map<String, Object> actor = ensureActorWithRoles(ownerKey, actorUsername, Set.of("AUDIT_ADMIN", "AUDITOR", "ORG_ADMIN"));
        Map<String, Object> task = getTaskById(ownerKey, taskId);
        Long parentId = toLong(task.get("parentId"));
        String actorRole = text(actor.get("role"));
        if (parentId == null) {
            if (!"AUDIT_ADMIN".equals(actorRole) && !"AUDITOR".equals(actorRole)) {
                throw new IllegalArgumentException("仅审计管理员或审计人员可审核主任务");
            }
        } else {
            if (!"ORG_ADMIN".equals(actorRole)) {
                throw new IllegalArgumentException("仅被审计单位管理员可审核经办人提交内容");
            }
            if (!text(actor.get("unit")).equals(text(task.get("unit")))) {
                throw new IllegalArgumentException("仅可审核本单位经办人提交内容");
            }
        }
        boolean passed = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("passed", Boolean.FALSE)));
        String comment = text(payload.get("comment"));
        if (passed && parentId == null) {
            ensureMainTaskReadyForAudit(ownerKey, taskId);
        }
        int progress = passed ? 100 : Math.min(toInt(task.get("progress"), 0), 95);
        String status = passed ? "已完成" : "执行中";
        String reviewStatus = passed ? "审核通过" : "退回修改";
        String now = now();

        jdbcTemplate.update(
            "UPDATE rect_task_record SET review_status=?, review_comment=?, status=?, progress=?, updated_at=? WHERE owner_key=? AND id=?",
            reviewStatus,
            comment,
            status,
            progress,
            now,
            ownerKey,
            taskId
        );

        Long issueId = toLong(task.get("issueId"));
        if (parentId == null) {
            jdbcTemplate.update(
                "UPDATE rect_issue_record SET status=?, updated_at=? WHERE owner_key=? AND id=?",
                passed ? "已完成" : "整改中",
                now,
                ownerKey,
                issueId
            );
        }

        createNotification(
            ownerKey,
            "TASK_REVIEWED",
            passed ? (parentId == null ? "整改任务审核通过" : "经办提交审核通过") : (parentId == null ? "整改任务被退回" : "经办提交被退回"),
            passed
                ? (parentId == null
                    ? "任务《" + text(task.get("title")) + "》已审核通过并销号。"
                    : "任务《" + text(task.get("title")) + "》经单位管理员审核通过。")
                : "任务《" + text(task.get("title")) + "》被退回，请按意见修改：" + defaultIfBlank(comment, "无"),
            actorUsername,
            taskId,
            issueId,
            mergeUsers(
                List.of(text(task.get("claimedBy")), text(task.get("assignee")), text(task.get("createdBy"))),
                listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN")
            )
        );
        return getTaskById(ownerKey, taskId);
    }

    @Override
    public Map<String, Object> updateTaskDeadline(String ownerKey, Long taskId, String deadline) {
        if (isBlank(deadline)) {
            throw new IllegalArgumentException("截止日期不能为空");
        }
        getTaskById(ownerKey, taskId);
        jdbcTemplate.update(
            "UPDATE rect_task_record SET deadline=?, updated_at=? WHERE owner_key=? AND id=?",
            deadline,
            now(),
            ownerKey,
            taskId
        );
        return getTaskById(ownerKey, taskId);
    }

    @Override
    public Map<String, Object> addRule(String ownerKey, String actorUsername, String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        String now = now();
        Long id = insertAndGetId(
            "INSERT INTO rect_rule_record(owner_key, name, enabled, updated_at) VALUES (?, ?, 1, ?)",
            ownerKey,
            name,
            now
        );
        return getRuleById(ownerKey, id);
    }

    @Override
    public Map<String, Object> updateRule(String ownerKey, Long ruleId, boolean enabled) {
        getRuleById(ownerKey, ruleId);
        jdbcTemplate.update("UPDATE rect_rule_record SET enabled=?, updated_at=? WHERE owner_key=? AND id=?", enabled, now(), ownerKey, ruleId);
        return getRuleById(ownerKey, ruleId);
    }

    @Override
    public List<Map<String, Object>> listReminderRules(String ownerKey) {
        return jdbcTemplate.query(
            "SELECT id, name, trigger_type, trigger_value, enabled, updated_at FROM rect_reminder_rule_record WHERE owner_key=? ORDER BY id DESC",
            (rs, i) -> toReminderRuleView(rs),
            ownerKey
        );
    }

    @Override
    public Map<String, Object> createReminderRule(String ownerKey, String actorUsername, Map<String, Object> payload) {
        String name = text(payload.get("name"));
        String triggerType = defaultIfBlank(text(payload.get("triggerType")), "BEFORE_DEADLINE").toUpperCase();
        int triggerValue = toInt(payload.get("triggerValue"), 1);
        boolean enabled = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("enabled", Boolean.TRUE)));

        if (isBlank(name)) {
            throw new IllegalArgumentException("提醒规则名称不能为空");
        }
        if (!REMINDER_TRIGGER_TYPES.contains(triggerType)) {
            throw new IllegalArgumentException("提醒触发类型不支持");
        }
        if (triggerValue <= 0 || triggerValue > 365) {
            throw new IllegalArgumentException("提醒天数范围应为 1~365");
        }

        Long id = insertAndGetId(
            "INSERT INTO rect_reminder_rule_record(owner_key, name, trigger_type, trigger_value, enabled, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            ownerKey,
            name,
            triggerType,
            triggerValue,
            enabled,
            now()
        );

        createNotification(
            ownerKey,
            "RULE_UPDATED",
            "整改提醒规则已新增",
            "规则《" + name + "》已创建并生效。",
            actorUsername,
            null,
            null,
            listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN")
        );
        return getReminderRuleById(ownerKey, id);
    }

    @Override
    public Map<String, Object> updateReminderRule(String ownerKey, String actorUsername, Long ruleId, Map<String, Object> payload) {
        Map<String, Object> existing = getReminderRuleById(ownerKey, ruleId);
        String name = defaultIfBlank(text(payload.get("name")), text(existing.get("name")));
        String triggerType = defaultIfBlank(text(payload.get("triggerType")), text(existing.get("triggerType"))).toUpperCase();
        int triggerValue = payload.containsKey("triggerValue")
            ? toInt(payload.get("triggerValue"), toInt(existing.get("triggerValue"), 1))
            : toInt(existing.get("triggerValue"), 1);
        boolean enabled = payload.containsKey("enabled")
            ? Boolean.parseBoolean(String.valueOf(payload.get("enabled")))
            : Boolean.parseBoolean(String.valueOf(existing.get("enabled")));

        if (isBlank(name)) {
            throw new IllegalArgumentException("提醒规则名称不能为空");
        }
        if (!REMINDER_TRIGGER_TYPES.contains(triggerType)) {
            throw new IllegalArgumentException("提醒触发类型不支持");
        }
        if (triggerValue <= 0 || triggerValue > 365) {
            throw new IllegalArgumentException("提醒天数范围应为 1~365");
        }

        jdbcTemplate.update(
            "UPDATE rect_reminder_rule_record SET name=?, trigger_type=?, trigger_value=?, enabled=?, updated_at=? WHERE owner_key=? AND id=?",
            name,
            triggerType,
            triggerValue,
            enabled,
            now(),
            ownerKey,
            ruleId
        );

        createNotification(
            ownerKey,
            "RULE_UPDATED",
            "整改提醒规则已更新",
            "规则《" + name + "》已更新。",
            actorUsername,
            null,
            null,
            listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN")
        );
        return getReminderRuleById(ownerKey, ruleId);
    }

    @Override
    public void deleteReminderRule(String ownerKey, String actorUsername, Long ruleId) {
        Map<String, Object> existing = getReminderRuleById(ownerKey, ruleId);
        jdbcTemplate.update("DELETE FROM rect_reminder_rule_record WHERE owner_key=? AND id=?", ownerKey, ruleId);
        createNotification(
            ownerKey,
            "RULE_UPDATED",
            "整改提醒规则已删除",
            "规则《" + text(existing.get("name")) + "》已删除。",
            actorUsername,
            null,
            null,
            listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN")
        );
    }

    @Override
    public int runReminderScan(String ownerKey) {
        List<Map<String, Object>> rules = jdbcTemplate.query(
            "SELECT id, name, trigger_type, trigger_value, enabled FROM rect_reminder_rule_record WHERE owner_key=? AND enabled=1",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("name", text(rs.getString("name")));
                row.put("triggerType", text(rs.getString("trigger_type")));
                row.put("triggerValue", rs.getInt("trigger_value"));
                row.put("enabled", rs.getBoolean("enabled"));
                return row;
            },
            ownerKey
        );
        if (rules.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> tasks = jdbcTemplate.query(
            """
            SELECT id, issue_id, title, assignee, created_by, deadline, status
              FROM rect_task_record
             WHERE owner_key=?
               AND parent_id IS NULL
               AND status <> '已完成'
               AND deadline IS NOT NULL
               AND deadline <> ''
            """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("issueId", rs.getLong("issue_id"));
                row.put("title", text(rs.getString("title")));
                row.put("assignee", text(rs.getString("assignee")));
                row.put("createdBy", text(rs.getString("created_by")));
                row.put("deadline", text(rs.getString("deadline")));
                row.put("status", text(rs.getString("status")));
                return row;
            },
            ownerKey
        );

        LocalDate today = LocalDate.now();
        int sentCount = 0;
        for (Map<String, Object> task : tasks) {
            LocalDate deadline = parseDeadlineDate(text(task.get("deadline")));
            if (deadline == null) {
                continue;
            }
            long daysUntil = ChronoUnit.DAYS.between(today, deadline);
            long overdueDays = ChronoUnit.DAYS.between(deadline, today);

            for (Map<String, Object> rule : rules) {
                String triggerType = text(rule.get("triggerType"));
                int triggerValue = toInt(rule.get("triggerValue"), 1);
                boolean matched = false;
                if ("BEFORE_DEADLINE".equals(triggerType)) {
                    matched = daysUntil == triggerValue;
                } else if ("OVERDUE".equals(triggerType)) {
                    matched = overdueDays == triggerValue;
                } else if ("INTERVAL_DAYS".equals(triggerType)) {
                    matched = overdueDays > 0 && (overdueDays % triggerValue == 0);
                }

                if (!matched) {
                    continue;
                }

                Long ruleId = toLong(rule.get("id"));
                Long taskId = toLong(task.get("id"));
                if (!tryRecordReminderDispatch(ownerKey, ruleId, taskId, today)) {
                    continue;
                }

                Long issueId = toLong(task.get("issueId"));
                createNotification(
                    ownerKey,
                    "RECTIFICATION_REMINDER",
                    "整改任务提醒",
                    "任务《" + text(task.get("title")) + "》触发提醒规则《" + text(rule.get("name")) + "》，请及时处理。",
                    "system",
                    taskId,
                    issueId,
                    mergeUsers(
                        List.of(text(task.get("assignee")), text(task.get("createdBy"))),
                        listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN")
                    )
                );
                sentCount++;
            }
        }
        return sentCount;
    }

    @Override
    public List<Map<String, Object>> listUsers(String ownerKey) {
        ensureSeed(ownerKey);
        syncUsersFromAuth(ownerKey);
        return jdbcTemplate.query(
            "SELECT id, username, nickname, role, status, unit, department, created_at, updated_at FROM rect_user_record WHERE owner_key=? AND status <> 'DELETED' ORDER BY id DESC",
            (rs, i) -> toUserView(rs),
            ownerKey
        );
    }

    @Override
    public Map<String, Object> createUser(String ownerKey, String actorUsername, Map<String, Object> payload) {
        String username = text(payload.get("username"));
        if (isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String nickname = defaultIfBlank(text(payload.get("nickname")), username);
        String role = defaultIfBlank(text(payload.get("role")), "AUDITOR");
        String status = defaultIfBlank(text(payload.get("status")), "ENABLED");
        String unit = sanitizeDepartmentName(payload.get("unit"));
        String department = sanitizeDepartmentName(payload.get("department"));
        if (isBlank(unit)) {
            unit = department;
        }
        if (isBlank(department)) {
            department = unit;
        }
        ensureDepartmentAllowed(ownerKey, defaultIfBlank(unit, department));
        ensureBindableDepartment(defaultIfBlank(unit, department));
        String now = now();

        try {
            Long id = insertAndGetId(
                """
                INSERT INTO rect_user_record(owner_key, username, nickname, role, status, unit, department, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ownerKey,
                username,
                nickname,
                role,
                status,
                unit,
                department,
                now,
                now
            );

            createNotification(
                ownerKey,
                "USER_CREATED",
                "账号已创建",
                "您的账号 " + username + " 已创建，请登录后完善个人信息。",
                actorUsername,
                null,
                null,
                List.of(username)
            );
            return getUserById(ownerKey, id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    @Override
    public Map<String, Object> updateUser(String ownerKey, String actorUsername, Long userId, Map<String, Object> payload) {
        Map<String, Object> user = getUserById(ownerKey, userId);
        ensureUserActive(user);
        String nickname = defaultIfBlank(text(payload.get("nickname")), text(user.get("nickname")));
        String role = defaultIfBlank(text(payload.get("role")), text(user.get("role")));
        String status = defaultIfBlank(text(payload.get("status")), text(user.get("status")));
        String unit = defaultIfBlank(sanitizeDepartmentName(payload.get("unit")), sanitizeDepartmentName(user.get("unit")));
        String department = defaultIfBlank(sanitizeDepartmentName(payload.get("department")), sanitizeDepartmentName(user.get("department")));
        if (isBlank(unit)) {
            unit = department;
        }
        ensureDepartmentAllowed(ownerKey, defaultIfBlank(unit, department));
        ensureBindableDepartment(defaultIfBlank(unit, department));

        jdbcTemplate.update(
            "UPDATE rect_user_record SET nickname=?, role=?, status=?, unit=?, department=?, updated_at=? WHERE owner_key=? AND id=?",
            nickname,
            role,
            status,
            unit,
            department,
            now(),
            ownerKey,
            userId
        );

        createNotification(
            ownerKey,
            "USER_UPDATED",
            "账号信息已更新",
            "您的账号信息已被管理员更新，当前角色：" + role + "，状态：" + ("ENABLED".equals(status) ? "启用" : "停用") + "。",
            actorUsername,
            null,
            null,
            List.of(text(user.get("username"))));
        return getUserById(ownerKey, userId);
    }

    @Override
    public Map<String, Object> updateUserRole(String ownerKey, String actorUsername, Long userId, String role) {
        if (isBlank(role)) {
            throw new IllegalArgumentException("角色不能为空");
        }
        Map<String, Object> user = getUserById(ownerKey, userId);
        ensureUserActive(user);
        jdbcTemplate.update("UPDATE rect_user_record SET role=?, updated_at=? WHERE owner_key=? AND id=?", role, now(), ownerKey, userId);

        createNotification(
            ownerKey,
            "USER_UPDATED",
            "用户角色已更新",
            "您的账号角色已调整为：" + role,
            actorUsername,
            null,
            null,
            List.of(text(user.get("username")))
        );
        return getUserById(ownerKey, userId);
    }

    @Override
    public Map<String, Object> updateUserStatus(String ownerKey, String actorUsername, Long userId, String status) {
        if (!"ENABLED".equalsIgnoreCase(status) && !"DISABLED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("状态仅支持 ENABLED 或 DISABLED");
        }
        Map<String, Object> user = getUserById(ownerKey, userId);
        ensureUserActive(user);
        jdbcTemplate.update("UPDATE rect_user_record SET status=?, updated_at=? WHERE owner_key=? AND id=?", status.toUpperCase(), now(), ownerKey, userId);

        createNotification(
            ownerKey,
            "USER_STATUS_CHANGED",
            "账号状态变更通知",
            "您的账号状态已变更为：" + ("ENABLED".equalsIgnoreCase(status) ? "启用" : "停用") + "。",
            actorUsername,
            null,
            null,
            List.of(text(user.get("username")))
        );
        return getUserById(ownerKey, userId);
    }

    @Override
    public void deleteUser(String ownerKey, String actorUsername, Long userId) {
        Map<String, Object> user = getUserById(ownerKey, userId);
        ensureUserActive(user);

        String username = text(user.get("username"));
        if ("admin".equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("系统内置管理员不允许删除");
        }

        jdbcTemplate.update(
            "UPDATE rect_user_record SET status='DELETED', updated_at=? WHERE owner_key=? AND id=?",
            now(),
            ownerKey,
            userId
        );

        createNotification(
            ownerKey,
            "USER_DELETED",
            "账号已删除",
            "账号 " + username + " 已被管理员删除并移出管理列表。",
            actorUsername,
            null,
            null,
            List.of(actorUsername)
        );
    }

    @Override
    public Map<String, Object> bindUserDepartment(String ownerKey, String actorUsername, Long userId, String department) {
        String normalizedDepartment = sanitizeDepartmentName(department);
        if (isBlank(normalizedDepartment)) {
            throw new IllegalArgumentException("部门不能为空");
        }
        ensureDepartmentAllowed(ownerKey, normalizedDepartment);
        ensureBindableDepartment(normalizedDepartment);
        Map<String, Object> user = getUserById(ownerKey, userId);
        ensureUserActive(user);
        jdbcTemplate.update(
            "UPDATE rect_user_record SET unit=?, department=?, updated_at=? WHERE owner_key=? AND id=?",
            normalizedDepartment,
            normalizedDepartment,
            now(),
            ownerKey,
            userId
        );
        try {
            jdbcTemplate.update(
                "UPDATE auth_user SET unit=?, department=?, updated_at=? WHERE username=?",
                normalizedDepartment,
                normalizedDepartment,
                now(),
                text(user.get("username"))
            );
        } catch (DataAccessException ignore) {
            // auth_user may not exist in some deployments; rectification record remains authoritative
        }

        createNotification(
            ownerKey,
            "USER_UPDATED",
            "账号部门已绑定",
            "您的账号已绑定至部门：" + normalizedDepartment,
            actorUsername,
            null,
            null,
            List.of(text(user.get("username")))
        );

        return getUserById(ownerKey, userId);
    }

    @Override
    public List<Map<String, Object>> listDepartments(String ownerKey) {
        ensureSeed(ownerKey);
        return jdbcTemplate.query(
            "SELECT id, name, updated_at FROM rect_department_record WHERE owner_key=? ORDER BY name ASC",
            (rs, i) -> toDepartmentView(rs),
            ownerKey
        );
    }

    @Override
    public Map<String, Object> createDepartment(String ownerKey, String actorUsername, String name) {
        String departmentName = text(name);
        if (isBlank(departmentName)) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        if (isGarbledDepartmentName(departmentName)) {
            throw new IllegalArgumentException("部门名称非法，请重新输入");
        }
        try {
            Long id = insertAndGetId(
                "INSERT INTO rect_department_record(owner_key, name, updated_at) VALUES (?, ?, ?)",
                ownerKey,
                departmentName,
                now()
            );
            return getDepartmentById(ownerKey, id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("部门已存在");
        }
    }

    @Override
    public Map<String, Object> updateDepartment(String ownerKey, String actorUsername, Long departmentId, String name) {
        String departmentName = text(name);
        if (isBlank(departmentName)) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        if (isGarbledDepartmentName(departmentName)) {
            throw new IllegalArgumentException("部门名称非法，请重新输入");
        }
        Map<String, Object> existing = getDepartmentById(ownerKey, departmentId);
        String oldName = text(existing.get("name"));
        if (oldName.equals(departmentName)) {
            return existing;
        }

        try {
            jdbcTemplate.update(
                "UPDATE rect_department_record SET name=?, updated_at=? WHERE owner_key=? AND id=?",
                departmentName,
                now(),
                ownerKey,
                departmentId
            );
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("部门已存在");
        }

        jdbcTemplate.update(
            "UPDATE rect_user_record SET unit=?, updated_at=? WHERE owner_key=? AND unit=?",
            departmentName,
            now(),
            ownerKey,
            oldName
        );
        return getDepartmentById(ownerKey, departmentId);
    }

    @Override
    public void deleteDepartment(String ownerKey, String actorUsername, Long departmentId) {
        Map<String, Object> department = getDepartmentById(ownerKey, departmentId);
        String name = text(department.get("name"));
        Integer usedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_user_record WHERE owner_key=? AND unit=? AND status<>'DELETED'",
            Integer.class,
            ownerKey,
            name
        );
        if (usedCount != null && usedCount > 0) {
            throw new IllegalArgumentException("该部门下仍有关联用户，无法删除");
        }

        jdbcTemplate.update("DELETE FROM rect_department_record WHERE owner_key=? AND id=?", ownerKey, departmentId);
    }

    @Override
    public List<Map<String, Object>> listDeletedUsers(String ownerKey) {
        ensureSeed(ownerKey);
        syncUsersFromAuth(ownerKey);
        return jdbcTemplate.query(
            "SELECT id, username, nickname, role, status, unit, department, created_at, updated_at FROM rect_user_record WHERE owner_key=? AND status = 'DELETED' ORDER BY id DESC",
            (rs, i) -> toUserView(rs),
            ownerKey
        );
    }

    @Override
    public Map<String, Object> restoreUser(String ownerKey, String actorUsername, Long userId) {
        Map<String, Object> user = getUserById(ownerKey, userId);
        if (!"DELETED".equalsIgnoreCase(text(user.get("status")))) {
            throw new IllegalArgumentException("用户未处于已删除状态");
        }
        jdbcTemplate.update(
            "UPDATE rect_user_record SET status='DISABLED', updated_at=? WHERE owner_key=? AND id=?",
            now(),
            ownerKey,
            userId
        );

        createNotification(
            ownerKey,
            "USER_UPDATED",
            "账号已恢复",
            "您的账号已从回收站恢复，当前状态为：停用，请联系管理员启用。",
            actorUsername,
            null,
            null,
            List.of(text(user.get("username")))
        );

        return getUserById(ownerKey, userId);
    }

    @Override
    public Map<String, Object> submitReport(String ownerKey, String actorUsername, Map<String, Object> payload) {
        String unit = text(payload.get("unit"));
        String title = text(payload.get("title"));
        String summary = text(payload.get("summary"));
        if (isBlank(unit) || isBlank(title) || isBlank(summary)) {
            throw new IllegalArgumentException("单位、标题、总结不能为空");
        }

        Long id = insertAndGetId(
            "INSERT INTO rect_report_record(owner_key, unit, title, summary, submitter, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            ownerKey,
            unit,
            title,
            summary,
            actorUsername,
            now()
        );
        createNotification(
            ownerKey,
            "UNIT_REPORT_SUBMITTED",
            "单位整改汇总已提交",
            "单位【" + unit + "】已提交《" + title + "》，请审计人员核验整改闭环情况。",
            actorUsername,
            null,
            null,
            mergeUsers(listEnabledUsersByRole(ownerKey, "AUDITOR"), listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"))
        );
        return getReportById(ownerKey, id);
    }

    @Override
    public List<Map<String, Object>> listOrgDepartments(String ownerKey, String actorUsername) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        return jdbcTemplate.query(
            "SELECT id, name, parent_id, leader_username, unit, updated_at FROM rect_org_department_record WHERE owner_key=? AND unit=? ORDER BY id ASC",
            (rs, i) -> toOrgDepartmentView(rs),
            ownerKey,
            actor.get("unit")
        );
    }

    @Override
    public Map<String, Object> createOrgDepartment(String ownerKey, String actorUsername, Map<String, Object> payload) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        String unit = actor.get("unit");
        String name = text(payload.get("name"));
        if (isBlank(name)) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        if (isGarbledDepartmentName(name)) {
            throw new IllegalArgumentException("部门名称非法，请重新输入");
        }
        Long parentId = toLong(payload.get("parentId"));
        String leaderUsername = text(payload.get("leaderUsername"));
        if (parentId != null) {
            getOrgDepartmentById(ownerKey, unit, parentId);
        }
        if (!isBlank(leaderUsername)) {
            ensureUserInUnit(ownerKey, unit, leaderUsername);
        }

        try {
            Long id = insertAndGetId(
                "INSERT INTO rect_org_department_record(owner_key, unit, name, parent_id, leader_username, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                ownerKey,
                unit,
                name,
                parentId,
                leaderUsername,
                now()
            );
            return getOrgDepartmentById(ownerKey, unit, id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("部门已存在");
        }
    }

    @Override
    public Map<String, Object> updateOrgDepartment(String ownerKey, String actorUsername, Long departmentId, Map<String, Object> payload) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        String unit = actor.get("unit");
        Map<String, Object> existing = getOrgDepartmentById(ownerKey, unit, departmentId);
        String oldName = text(existing.get("name"));

        String name = defaultIfBlank(text(payload.get("name")), oldName);
        Long parentId = payload.containsKey("parentId") ? toLong(payload.get("parentId")) : toLong(existing.get("parentId"));
        String leaderUsername = payload.containsKey("leaderUsername")
            ? text(payload.get("leaderUsername"))
            : text(existing.get("leaderUsername"));

        if (isBlank(name)) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        if (isGarbledDepartmentName(name)) {
            throw new IllegalArgumentException("部门名称非法，请重新输入");
        }
        if (parentId != null) {
            if (departmentId.equals(parentId)) {
                throw new IllegalArgumentException("上级部门不能是自身");
            }
            getOrgDepartmentById(ownerKey, unit, parentId);
        }
        if (!isBlank(leaderUsername)) {
            ensureUserInUnit(ownerKey, unit, leaderUsername);
        }

        try {
            jdbcTemplate.update(
                "UPDATE rect_org_department_record SET name=?, parent_id=?, leader_username=?, updated_at=? WHERE owner_key=? AND unit=? AND id=?",
                name,
                parentId,
                leaderUsername,
                now(),
                ownerKey,
                unit,
                departmentId
            );
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("部门已存在");
        }

        if (!oldName.equals(name)) {
            jdbcTemplate.update(
                "UPDATE rect_user_record SET department=?, updated_at=? WHERE owner_key=? AND unit=? AND department=? AND status<>'DELETED'",
                name,
                now(),
                ownerKey,
                unit,
                oldName
            );
            try {
                jdbcTemplate.update(
                    "UPDATE auth_user SET department=? WHERE unit=? AND department=?",
                    name,
                    unit,
                    oldName
                );
            } catch (DataAccessException ignore) {
                // auth_user may not exist in some deployments; rectification record remains authoritative
            }
        }
        return getOrgDepartmentById(ownerKey, unit, departmentId);
    }

    @Override
    public void deleteOrgDepartment(String ownerKey, String actorUsername, Long departmentId) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        String unit = actor.get("unit");
        Map<String, Object> existing = getOrgDepartmentById(ownerKey, unit, departmentId);
        String name = text(existing.get("name"));

        Integer childCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_org_department_record WHERE owner_key=? AND unit=? AND parent_id=?",
            Integer.class,
            ownerKey,
            unit,
            departmentId
        );
        if (childCount != null && childCount > 0) {
            throw new IllegalArgumentException("该部门存在下级部门，无法删除");
        }

        Integer memberCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_user_record WHERE owner_key=? AND unit=? AND department=? AND status<>'DELETED'",
            Integer.class,
            ownerKey,
            unit,
            name
        );
        if (memberCount != null && memberCount > 0) {
            throw new IllegalArgumentException("该部门仍有关联成员，无法删除");
        }

        jdbcTemplate.update(
            "DELETE FROM rect_org_department_record WHERE owner_key=? AND unit=? AND id=?",
            ownerKey,
            unit,
            departmentId
        );
    }

    @Override
    public List<Map<String, Object>> listOrgDepartmentMembers(String ownerKey, String actorUsername, String department) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        String unit = actor.get("unit");
        String departmentName = text(department);

        if (!isBlank(departmentName)) {
            ensureOrgDepartmentByName(ownerKey, unit, departmentName);
            return jdbcTemplate.query(
                "SELECT id, username, nickname, role, status, unit, department, created_at, updated_at FROM rect_user_record WHERE owner_key=? AND unit=? AND department=? AND status<>'DELETED' ORDER BY id DESC",
                (rs, i) -> toUserView(rs),
                ownerKey,
                unit,
                departmentName
            );
        }

        return jdbcTemplate.query(
            "SELECT id, username, nickname, role, status, unit, department, created_at, updated_at FROM rect_user_record WHERE owner_key=? AND unit=? AND status<>'DELETED' ORDER BY id DESC",
            (rs, i) -> toUserView(rs),
            ownerKey,
            unit
        );
    }

    @Override
    public Map<String, Object> createOrgDepartmentMember(String ownerKey, String actorUsername, Map<String, Object> payload) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        String unit = actor.get("unit");

        String username = text(payload.get("username"));
        if (isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String nickname = defaultIfBlank(text(payload.get("nickname")), username);
        String department = text(payload.get("department"));
        if (isBlank(department)) {
            throw new IllegalArgumentException("部门不能为空");
        }
        ensureOrgDepartmentByName(ownerKey, unit, department);

        String role = defaultIfBlank(text(payload.get("role")), "ORG_OPERATOR");
        if (!"ORG_OPERATOR".equals(role) && !"ORG_ADMIN".equals(role)) {
            throw new IllegalArgumentException("角色仅支持 ORG_OPERATOR 或 ORG_ADMIN");
        }
        String status = defaultIfBlank(text(payload.get("status")), "ENABLED").toUpperCase();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("状态仅支持 ENABLED 或 DISABLED");
        }

        String now = now();
        try {
            Long id = insertAndGetId(
                "INSERT INTO rect_user_record(owner_key, username, nickname, role, status, unit, department, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ownerKey,
                username,
                nickname,
                role,
                status,
                unit,
                department,
                now,
                now
            );
            try {
                jdbcTemplate.update(
                    "UPDATE auth_user SET nickname=?, role=?, status=?, unit=?, department=? WHERE username=?",
                    nickname,
                    role,
                    status,
                    unit,
                    department,
                    username
                );
            } catch (DataAccessException ignore) {
                // auth_user may not exist in some deployments; rectification record remains authoritative
            }
            return getUserById(ownerKey, id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    @Override
    public Map<String, Object> updateOrgDepartmentMember(String ownerKey, String actorUsername, Long userId, Map<String, Object> payload) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        String unit = actor.get("unit");
        Map<String, Object> user = getUserById(ownerKey, userId);
        ensureUserInUnit(ownerKey, unit, text(user.get("username")));
        ensureUserActive(user);

        String nickname = defaultIfBlank(text(payload.get("nickname")), text(user.get("nickname")));
        String department = defaultIfBlank(text(payload.get("department")), text(user.get("department")));
        String role = defaultIfBlank(text(payload.get("role")), text(user.get("role")));
        String status = defaultIfBlank(text(payload.get("status")), text(user.get("status"))).toUpperCase();

        if (!isBlank(department)) {
            ensureOrgDepartmentByName(ownerKey, unit, department);
        }
        if (!"ORG_OPERATOR".equals(role) && !"ORG_ADMIN".equals(role)) {
            throw new IllegalArgumentException("角色仅支持 ORG_OPERATOR 或 ORG_ADMIN");
        }
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("状态仅支持 ENABLED 或 DISABLED");
        }

        jdbcTemplate.update(
            "UPDATE rect_user_record SET nickname=?, role=?, status=?, department=?, updated_at=? WHERE owner_key=? AND id=?",
            nickname,
            role,
            status,
            department,
            now(),
            ownerKey,
            userId
        );
        try {
            jdbcTemplate.update(
                "UPDATE auth_user SET nickname=?, role=?, status=?, unit=?, department=? WHERE username=?",
                nickname,
                role,
                status,
                unit,
                department,
                text(user.get("username"))
            );
        } catch (DataAccessException ignore) {
            // auth_user may not exist in some deployments; rectification record remains authoritative
        }
        return getUserById(ownerKey, userId);
    }

    @Override
    public void deleteOrgDepartmentMember(String ownerKey, String actorUsername, Long userId) {
        ensureSeed(ownerKey);
        Map<String, String> actor = ensureOrgAdminActor(ownerKey, actorUsername);
        String unit = actor.get("unit");
        Map<String, Object> user = getUserById(ownerKey, userId);
        String username = text(user.get("username"));
        ensureUserInUnit(ownerKey, unit, username);
        ensureUserActive(user);
        if (username.equals(actorUsername)) {
            throw new IllegalArgumentException("不能删除当前登录账号");
        }

        jdbcTemplate.update(
            "UPDATE rect_user_record SET status='DELETED', updated_at=? WHERE owner_key=? AND id=?",
            now(),
            ownerKey,
            userId
        );
    }

    private void ensureAssignableUser(String ownerKey, String username, String requiredRole, String requiredUnit) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
              FROM rect_user_record
             WHERE owner_key=?
               AND username=?
               AND status='ENABLED'
               AND role=?
               AND unit=?
            """,
            Integer.class,
            ownerKey,
            username,
            requiredRole,
            requiredUnit
        );
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("责任人不在可分配范围，请选择" + requiredUnit + "单位下的" + requiredRole + "用户");
        }
    }

    private String getActorUnit(String ownerKey, String actorUsername) {
        String username = text(actorUsername);
        if (isBlank(username)) {
            throw new IllegalArgumentException("当前用户无效，无法执行该操作");
        }
        String unit = jdbcTemplate.query(
            "SELECT unit FROM rect_user_record WHERE owner_key=? AND username=? AND status='ENABLED' ORDER BY id DESC LIMIT 1",
            rs -> rs.next() ? text(rs.getString(1)) : "",
            ownerKey,
            username
        );
        if (isBlank(unit)) {
            throw new IllegalArgumentException("当前用户未绑定单位，无法执行该操作");
        }
        return unit;
    }

    private Long createMainTask(
        String ownerKey,
        String actorUsername,
        Long issueId,
        String title,
        String assignee,
        String unit,
        String deadline
    ) {
        ensureAssignableUser(ownerKey, assignee, "ORG_ADMIN", unit);

        String now = now();
        Long taskId = insertAndGetId(
            """
            INSERT INTO rect_task_record(owner_key, issue_id, parent_id, title, unit, assignee, claimed_by, created_by,
              status, progress, deadline, review_status, review_comment, measure, attachments_json, feedback, created_at, updated_at)
            VALUES (?, ?, NULL, ?, ?, ?, '', ?, '待接收', 0, ?, '待审核', '', '', '[]', '', ?, ?)
            """,
            ownerKey,
            issueId,
            title,
            unit,
            assignee,
            actorUsername,
            deadline,
            now,
            now
        );

        jdbcTemplate.update(
            "UPDATE rect_issue_record SET status='整改中', updated_at=? WHERE owner_key=? AND id=?",
            now,
            ownerKey,
            issueId
        );

        createNotification(
            ownerKey,
            "TASK_ASSIGNED",
            "收到新的整改主任务",
            "任务《" + title + "》已下达，请及时签收并推进整改。",
            actorUsername,
            taskId,
            issueId,
            mergeUsers(List.of(assignee, actorUsername), listEnabledUsersByRole(ownerKey, "AUDIT_ADMIN"))
        );

        return taskId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toObjectList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> mapValue) {
                result.add((Map<String, Object>) mapValue);
            }
        }
        return result;
    }

    private void ensureMainTaskReadyForAudit(String ownerKey, Long mainTaskId) {
        List<Map<String, Object>> children = jdbcTemplate.query(
            """
            SELECT id, title, progress, measure, attachments_json
              FROM rect_task_record
             WHERE owner_key=? AND parent_id=?
             ORDER BY id ASC
            """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("title", text(rs.getString("title")));
                row.put("progress", rs.getInt("progress"));
                row.put("measure", text(rs.getString("measure")));
                row.put("attachments", toStringList(rs.getString("attachments_json")));
                return row;
            },
            ownerKey,
            mainTaskId
        );
        if (children.isEmpty()) {
            throw new IllegalArgumentException("请先拆解并完成子任务后再提交审核");
        }

        for (Map<String, Object> child : children) {
            if (toInt(child.get("progress"), 0) < 100) {
                throw new IllegalArgumentException("子任务《" + text(child.get("title")) + "》未完成，不能提交审核");
            }
            if (isBlank(text(child.get("measure")))) {
                throw new IllegalArgumentException("子任务《" + text(child.get("title")) + "》缺少整改措施");
            }
            if (toStringList(child.get("attachments")).isEmpty()) {
                throw new IllegalArgumentException("子任务《" + text(child.get("title")) + "》缺少证明材料");
            }
        }
    }

    private LocalDate parseDeadlineDate(String deadline) {
        String value = text(deadline);
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean tryRecordReminderDispatch(String ownerKey, Long ruleId, Long taskId, LocalDate reminderDate) {
        try {
            jdbcTemplate.update(
                "INSERT INTO rect_reminder_dispatch_record(owner_key, rule_id, task_id, reminder_date, created_at) VALUES (?, ?, ?, ?, ?)",
                ownerKey,
                ruleId,
                taskId,
                reminderDate.toString(),
                now()
            );
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private List<String> listEnabledOrgAdminsByUnit(String ownerKey, String unit) {
        return jdbcTemplate.query(
            "SELECT username FROM rect_user_record WHERE owner_key=? AND role='ORG_ADMIN' AND status='ENABLED' AND unit=?",
            (rs, i) -> text(rs.getString(1)),
            ownerKey,
            unit
        );
    }

    private List<String> listEnabledUsersByRole(String ownerKey, String role) {
        return jdbcTemplate.query(
            "SELECT username FROM rect_user_record WHERE owner_key=? AND role=? AND status='ENABLED'",
            (rs, i) -> text(rs.getString(1)),
            ownerKey,
            role
        );
    }

    private List<String> listEnabledUsersByUnit(String ownerKey, String unit) {
        return jdbcTemplate.query(
            "SELECT username FROM rect_user_record WHERE owner_key=? AND unit=? AND status='ENABLED'",
            (rs, i) -> text(rs.getString(1)),
            ownerKey,
            unit
        );
    }

    private List<String> listEnabledUsersByRoleAndUnit(String ownerKey, String role, String unit) {
        return jdbcTemplate.query(
            "SELECT username FROM rect_user_record WHERE owner_key=? AND role=? AND unit=? AND status='ENABLED'",
            (rs, i) -> text(rs.getString(1)),
            ownerKey,
            role,
            unit
        );
    }

    private List<String> listIssueTaskReceivers(String ownerKey, Long issueId) {
        return jdbcTemplate.query(
            "SELECT assignee, claimed_by, created_by FROM rect_task_record WHERE owner_key=? AND issue_id=?",
            rs -> {
                Set<String> users = new HashSet<>();
                while (rs.next()) {
                    String assignee = text(rs.getString("assignee"));
                    String claimedBy = text(rs.getString("claimed_by"));
                    String createdBy = text(rs.getString("created_by"));
                    if (!isBlank(assignee)) {
                        users.add(assignee);
                    }
                    if (!isBlank(claimedBy)) {
                        users.add(claimedBy);
                    }
                    if (!isBlank(createdBy)) {
                        users.add(createdBy);
                    }
                }
                return new ArrayList<>(users);
            },
            ownerKey,
            issueId
        );
    }

    @SafeVarargs
    private final List<String> mergeUsers(List<String>... groups) {
        Set<String> merged = new HashSet<>();
        for (List<String> group : groups) {
            if (group == null) {
                continue;
            }
            for (String item : group) {
                String username = text(item);
                if (!isBlank(username)) {
                    merged.add(username);
                }
            }
        }
        return new ArrayList<>(merged);
    }

    @Override
    public List<Map<String, Object>> listNotifications(String ownerKey, String username) {
        ensureSeed(ownerKey);
        List<Map<String, Object>> notifications = jdbcTemplate.query(
            """
            SELECT n.id, n.type, n.title, n.content, n.from_user, n.related_task_id, n.related_issue_id, n.created_at
              FROM rect_notification_record n
              JOIN rect_notification_receiver_record r ON n.id=r.notification_id AND r.owner_key=n.owner_key
             WHERE n.owner_key=? AND r.receiver_username=?
             ORDER BY n.id DESC
            """,
            (rs, i) -> toNotificationBaseView(rs),
            ownerKey,
            username
        );

        for (Map<String, Object> item : notifications) {
            Long notificationId = toLong(item.get("id"));
            item.put("toUsers", listNotificationReceivers(ownerKey, notificationId));
            item.put("readBy", listNotificationReadUsers(ownerKey, notificationId));
            item.put("interactions", listNotificationInteractions(ownerKey, notificationId));
            item.put("isRead", listNotificationReadUsers(ownerKey, notificationId).contains(username));
        }
        return notifications;
    }

    @Override
    public void markNotificationRead(String ownerKey, String username, Long notificationId) {
        ensureNotificationVisible(ownerKey, username, notificationId);
        try {
            jdbcTemplate.update(
                "INSERT INTO rect_notification_read_record(owner_key, notification_id, username, read_at) VALUES (?, ?, ?, ?)",
                ownerKey,
                notificationId,
                username,
                now()
            );
        } catch (DuplicateKeyException ignore) {
            // already read
        }
    }

    @Override
    public Map<String, Object> interactNotification(String ownerKey, String username, Long notificationId, Map<String, Object> payload) {
        ensureNotificationVisible(ownerKey, username, notificationId);
        String action = defaultIfBlank(text(payload.get("action")), "REPLY").toUpperCase();
        String message = text(payload.get("message"));

        insertAndGetId(
            "INSERT INTO rect_notification_interaction_record(owner_key, notification_id, action, actor, message, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            ownerKey,
            notificationId,
            action,
            username,
            message,
            now()
        );
        markNotificationRead(ownerKey, username, notificationId);
        return getNotificationById(ownerKey, username, notificationId);
    }

    private void ensureSeed(String ownerKey) {
        syncUsersFromAuth(ownerKey);
    }

    private void ensureUserUnitColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE rect_user_record ADD COLUMN unit VARCHAR(255)");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
    }

    private void ensureIssueExtendedColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE rect_issue_record ADD COLUMN evidence_json LONGTEXT");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
        try {
            jdbcTemplate.execute("ALTER TABLE rect_issue_record ADD COLUMN regulation_clause TEXT");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
    }

    private boolean isDuplicateColumnError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("duplicate column") || normalized.contains("already exists")) {
                    return true;
                }
            }
            if (current instanceof SQLException sqlException) {
                if (sqlException.getErrorCode() == 1060) {
                    return true;
                }
                String sqlState = sqlException.getSQLState();
                if ("42S21".equalsIgnoreCase(sqlState)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void ensureOrgDepartmentTable() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS rect_org_department_record (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              owner_key VARCHAR(128) NOT NULL,
              unit VARCHAR(255) NOT NULL,
              name VARCHAR(255) NOT NULL,
              parent_id BIGINT,
              leader_username VARCHAR(128),
              updated_at DATETIME NOT NULL,
              UNIQUE KEY uk_rect_org_dept_owner_unit_name (owner_key, unit, name),
              INDEX idx_rect_org_dept_owner_unit (owner_key, unit)
            )
            """
        );
    }

    private void syncUsersFromAuth(String ownerKey) {
        List<Map<String, Object>> authUsers;
        try {
            authUsers = jdbcTemplate.query(
                "SELECT username, nickname, role, status, unit, department FROM auth_user",
                (rs, i) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("username", text(rs.getString("username")));
                    row.put("nickname", text(rs.getString("nickname")));
                    row.put("role", text(rs.getString("role")));
                    row.put("status", text(rs.getString("status")));
                    row.put("unit", text(rs.getString("unit")));
                    row.put("department", text(rs.getString("department")));
                    return row;
                }
            );
        } catch (DataAccessException ex) {
            return;
        }

        String now = now();
        for (Map<String, Object> user : authUsers) {
            String username = text(user.get("username"));
            if (isBlank(username)) {
                continue;
            }
            String nickname = defaultIfBlank(text(user.get("nickname")), username);
            String role = normalizeRoleValue(text(user.get("role")));
            String status = defaultIfBlank(text(user.get("status")), "ENABLED").toUpperCase();
            String unit = sanitizeDepartmentName(user.get("unit"));
            String department = sanitizeDepartmentName(user.get("department"));
            if (isBlank(unit)) {
                unit = department;
            }
            if (isBlank(department)) {
                department = unit;
            }
            if (isBlank(unit)) {
                unit = defaultUnitByUsername(username);
            }
            if (isBlank(department)) {
                department = unit;
            }
            upsertDepartment(ownerKey, defaultIfBlank(unit, department), now);

            jdbcTemplate.update(
                """
                INSERT INTO rect_user_record(owner_key, username, nickname, role, status, unit, department, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    nickname=VALUES(nickname),
                    role=VALUES(role),
                    status=IF(status='DELETED', status, VALUES(status)),
                    unit=IF(status='DELETED', unit, IF(unit IS NULL OR unit='' OR unit='未分配部门' OR unit REGEXP '^[?？]+$' OR LOCATE('�', unit) > 0, VALUES(unit), unit)),
                    department=IF(status='DELETED', department, IF(department IS NULL OR department='' OR department='未分配部门' OR department REGEXP '^[?？]+$' OR LOCATE('�', department) > 0, VALUES(department), department)),
                    updated_at=VALUES(updated_at)
                """,
                ownerKey,
                username,
                nickname,
                role,
                status,
                unit,
                department,
                now,
                now
            );
        }
    }

    private String normalizeRoleValue(String role) {
        String normalized = text(role).toUpperCase();
        if (isBlank(normalized)) {
            return "AUDITOR";
        }
        if ("ADMIN".equals(normalized)) {
            return "AUDIT_ADMIN";
        }
        if ("AUDITEE_ADMIN".equals(normalized)) {
            return "ORG_ADMIN";
        }
        if ("AUDITEE_OPERATOR".equals(normalized)) {
            return "ORG_OPERATOR";
        }
        return normalized;
    }

    private void ensureUserActive(Map<String, Object> user) {
        if ("DELETED".equalsIgnoreCase(text(user.get("status")))) {
            throw new IllegalArgumentException("用户已删除");
        }
    }

    private List<Map<String, Object>> listIssues(String ownerKey) {
        List<Map<String, Object>> issues = jdbcTemplate.query(
            "SELECT id, code, title, level, unit, description, evidence_json, regulation_clause, status, created_by, created_at, updated_at FROM rect_issue_record WHERE owner_key=? ORDER BY id DESC",
            (rs, i) -> toIssueView(rs),
            ownerKey
        );

        Map<Long, Long> issueTaskMap = jdbcTemplate.query(
            "SELECT issue_id, id FROM rect_task_record WHERE owner_key=? AND parent_id IS NULL",
            rs -> {
                Map<Long, Long> m = new HashMap<>();
                while (rs.next()) {
                    m.put(rs.getLong("issue_id"), rs.getLong("id"));
                }
                return m;
            },
            ownerKey
        );
        if (issueTaskMap == null) {
            issueTaskMap = Collections.emptyMap();
        }

        for (Map<String, Object> issue : issues) {
            Long issueId = toLong(issue.get("id"));
            issue.put("taskId", issueTaskMap.get(issueId));
            issue.put("supervisions", listIssueSupervisions(ownerKey, issueId));
        }
        return issues;
    }

    private List<Map<String, Object>> listTasks(String ownerKey) {
        return jdbcTemplate.query(
            """
            SELECT id, issue_id, parent_id, title, unit, assignee, claimed_by, created_by, status, progress,
                   deadline, review_status, review_comment, measure, attachments_json, feedback, created_at, updated_at
              FROM rect_task_record
             WHERE owner_key=?
             ORDER BY id DESC
            """,
            (rs, i) -> toTaskView(rs),
            ownerKey
        );
    }

    private List<Map<String, Object>> listIssueSupervisions(String ownerKey, Long issueId) {
        return jdbcTemplate.query(
            "SELECT id, note, supervisor, created_at FROM rect_supervision_record WHERE owner_key=? AND issue_id=? ORDER BY id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("note", text(rs.getString("note")));
                row.put("supervisor", text(rs.getString("supervisor")));
                row.put("createdAt", formatDateTime(rs.getObject("created_at")));
                return row;
            },
            ownerKey,
            issueId
        );
    }

    private List<Map<String, Object>> listRules(String ownerKey) {
        return jdbcTemplate.query(
            "SELECT id, name, enabled, updated_at FROM rect_rule_record WHERE owner_key=? ORDER BY id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("name", text(rs.getString("name")));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
                return row;
            },
            ownerKey
        );
    }

    private List<Map<String, Object>> listReports(String ownerKey) {
        return jdbcTemplate.query(
            "SELECT id, unit, title, summary, submitter, created_at FROM rect_report_record WHERE owner_key=? ORDER BY id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("unit", text(rs.getString("unit")));
                row.put("title", text(rs.getString("title")));
                row.put("summary", text(rs.getString("summary")));
                row.put("submitter", text(rs.getString("submitter")));
                row.put("createdAt", formatDateTime(rs.getObject("created_at")));
                return row;
            },
            ownerKey
        );
    }

    private Map<String, Object> getIssueById(String ownerKey, Long issueId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, code, title, level, unit, description, evidence_json, regulation_clause, status, created_by, created_at, updated_at FROM rect_issue_record WHERE owner_key=? AND id=?",
            (rs, i) -> toIssueView(rs),
            ownerKey,
            issueId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("问题不存在");
        }
        Map<String, Object> issue = rows.get(0);
        issue.put("supervisions", listIssueSupervisions(ownerKey, issueId));
        issue.put("taskId", jdbcTemplate.query(
            "SELECT id FROM rect_task_record WHERE owner_key=? AND issue_id=? AND parent_id IS NULL ORDER BY id DESC LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null,
            ownerKey,
            issueId
        ));
        return issue;
    }

    private Map<String, Object> getTaskById(String ownerKey, Long taskId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
            SELECT id, issue_id, parent_id, title, unit, assignee, claimed_by, created_by, status, progress,
                   deadline, review_status, review_comment, measure, attachments_json, feedback, created_at, updated_at
              FROM rect_task_record
             WHERE owner_key=? AND id=?
            """,
            (rs, i) -> toTaskView(rs),
            ownerKey,
            taskId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("任务不存在");
        }
        return rows.get(0);
    }

    private void deleteTaskNotifications(String ownerKey, Long taskId) {
        List<Long> notificationIds = jdbcTemplate.query(
            "SELECT id FROM rect_notification_record WHERE owner_key=? AND related_task_id=?",
            (rs, i) -> rs.getLong(1),
            ownerKey,
            taskId
        );
        if (notificationIds.isEmpty()) {
            return;
        }
        String placeholders = notificationIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(ownerKey);
        args.addAll(notificationIds);
        jdbcTemplate.update(
            "DELETE FROM rect_notification_receiver_record WHERE owner_key=? AND notification_id IN (" + placeholders + ")",
            args.toArray()
        );
        jdbcTemplate.update(
            "DELETE FROM rect_notification_read_record WHERE owner_key=? AND notification_id IN (" + placeholders + ")",
            args.toArray()
        );
        jdbcTemplate.update(
            "DELETE FROM rect_notification_interaction_record WHERE owner_key=? AND notification_id IN (" + placeholders + ")",
            args.toArray()
        );
        jdbcTemplate.update(
            "DELETE FROM rect_notification_record WHERE owner_key=? AND id IN (" + placeholders + ")",
            args.toArray()
        );
    }

    private Map<String, Object> getShareById(String ownerKey, Long shareId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
            SELECT s.id, s.issue_id, i.title AS issue_title, s.from_department, s.to_department,
                   s.purpose, s.status, s.created_by, s.created_at, s.updated_at
              FROM rect_issue_share_record s
              JOIN rect_issue_record i ON i.owner_key=s.owner_key AND i.id=s.issue_id
             WHERE s.owner_key=? AND s.id=?
            """,
            (rs, i) -> toShareView(rs),
            ownerKey,
            shareId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("共享记录不存在");
        }
        Map<String, Object> share = rows.get(0);
        share.put("feedbacks", listShareFeedbacks(ownerKey, shareId));
        return share;
    }

    private void ensureShareAccessible(String ownerKey, String actorUsername, Map<String, Object> share) {
        String actorUnit = getActorUnit(ownerKey, actorUsername);
        if (!actorUnit.equals(text(share.get("toDepartment")))) {
            throw new IllegalArgumentException("仅接收部门成员可操作该共享记录");
        }
    }

    private List<Map<String, Object>> listShareFeedbacks(String ownerKey, Long shareId) {
        return jdbcTemplate.query(
            "SELECT id, feedback_text, attachments_json, created_by, created_at FROM rect_issue_share_feedback_record WHERE owner_key=? AND share_id=? ORDER BY id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("feedback", text(rs.getString("feedback_text")));
                row.put("attachments", toStringList(rs.getString("attachments_json")));
                row.put("createdBy", text(rs.getString("created_by")));
                row.put("createdAt", formatDateTime(rs.getObject("created_at")));
                return row;
            },
            ownerKey,
            shareId
        );
    }

    private Map<String, Object> getRuleById(String ownerKey, Long ruleId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, name, enabled, updated_at FROM rect_rule_record WHERE owner_key=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("name", text(rs.getString("name")));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
                return row;
            },
            ownerKey,
            ruleId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("规则不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getReminderRuleById(String ownerKey, Long ruleId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, name, trigger_type, trigger_value, enabled, updated_at FROM rect_reminder_rule_record WHERE owner_key=? AND id=?",
            (rs, i) -> toReminderRuleView(rs),
            ownerKey,
            ruleId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("提醒规则不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getUserById(String ownerKey, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, username, nickname, role, status, unit, department, created_at, updated_at FROM rect_user_record WHERE owner_key=? AND id=?",
            (rs, i) -> toUserView(rs),
            ownerKey,
            userId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getReportById(String ownerKey, Long reportId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, unit, title, summary, submitter, created_at FROM rect_report_record WHERE owner_key=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("unit", text(rs.getString("unit")));
                row.put("title", text(rs.getString("title")));
                row.put("summary", text(rs.getString("summary")));
                row.put("submitter", text(rs.getString("submitter")));
                row.put("createdAt", formatDateTime(rs.getObject("created_at")));
                return row;
            },
            ownerKey,
            reportId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("报告不存在");
        }
        return rows.get(0);
    }

    private Long createNotification(
        String ownerKey,
        String type,
        String title,
        String content,
        String fromUser,
        Long relatedTaskId,
        Long relatedIssueId,
        List<String> toUsers
    ) {
        List<String> uniqueUsers = normalizeNotificationReceivers(ownerKey, toUsers);
        if (uniqueUsers.isEmpty()) {
            return null;
        }

        Long notificationId = insertAndGetId(
            "INSERT INTO rect_notification_record(owner_key, type, title, content, from_user, related_task_id, related_issue_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ownerKey,
            type,
            title,
            content,
            defaultIfBlank(fromUser, "system"),
            relatedTaskId,
            relatedIssueId,
            now()
        );

        for (String receiver : uniqueUsers) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO rect_notification_receiver_record(owner_key, notification_id, receiver_username) VALUES (?, ?, ?)",
                    ownerKey,
                    notificationId,
                    receiver
                );
            } catch (DuplicateKeyException ignore) {
                // ignore duplicate
            }
        }
        return notificationId;
    }

    private List<String> normalizeNotificationReceivers(String ownerKey, List<String> rawUsers) {
        if (rawUsers == null || rawUsers.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (String raw : rawUsers) {
            String token = text(raw);
            if (isBlank(token)) {
                continue;
            }
            List<String> usernames = jdbcTemplate.query(
                "SELECT username FROM rect_user_record WHERE owner_key=? AND status='ENABLED' AND (username=? OR nickname=?)",
                (rs, i) -> text(rs.getString(1)),
                ownerKey,
                token,
                token
            );
            if (usernames.isEmpty()) {
                continue;
            }
            resolved.addAll(usernames);
        }
        return new ArrayList<>(resolved);
    }

    private void ensureNotificationVisible(String ownerKey, String username, Long notificationId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_notification_receiver_record WHERE owner_key=? AND notification_id=? AND receiver_username=?",
            Integer.class,
            ownerKey,
            notificationId,
            username
        );
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("通知不存在或无权限访问");
        }
    }

    private Map<String, Object> getNotificationById(String ownerKey, String username, Long notificationId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
            SELECT n.id, n.type, n.title, n.content, n.from_user, n.related_task_id, n.related_issue_id, n.created_at
              FROM rect_notification_record n
              JOIN rect_notification_receiver_record r ON n.id=r.notification_id AND r.owner_key=n.owner_key
             WHERE n.owner_key=? AND n.id=? AND r.receiver_username=?
            """,
            (rs, i) -> toNotificationBaseView(rs),
            ownerKey,
            notificationId,
            username
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("通知不存在");
        }
        Map<String, Object> notification = rows.get(0);
        notification.put("toUsers", listNotificationReceivers(ownerKey, notificationId));
        notification.put("readBy", listNotificationReadUsers(ownerKey, notificationId));
        notification.put("interactions", listNotificationInteractions(ownerKey, notificationId));
        notification.put("isRead", listNotificationReadUsers(ownerKey, notificationId).contains(username));
        return notification;
    }

    private List<String> listNotificationReceivers(String ownerKey, Long notificationId) {
        return jdbcTemplate.query(
            "SELECT receiver_username FROM rect_notification_receiver_record WHERE owner_key=? AND notification_id=?",
            (rs, i) -> text(rs.getString(1)),
            ownerKey,
            notificationId
        );
    }

    private List<String> listNotificationReadUsers(String ownerKey, Long notificationId) {
        return jdbcTemplate.query(
            "SELECT username FROM rect_notification_read_record WHERE owner_key=? AND notification_id=?",
            (rs, i) -> text(rs.getString(1)),
            ownerKey,
            notificationId
        );
    }

    private List<Map<String, Object>> listNotificationInteractions(String ownerKey, Long notificationId) {
        return jdbcTemplate.query(
            "SELECT id, action, actor, message, created_at FROM rect_notification_interaction_record WHERE owner_key=? AND notification_id=? ORDER BY id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("action", text(rs.getString("action")));
                row.put("actor", text(rs.getString("actor")));
                row.put("message", text(rs.getString("message")));
                row.put("createdAt", formatDateTime(rs.getObject("created_at")));
                return row;
            },
            ownerKey,
            notificationId
        );
    }

    private Map<String, Object> toIssueView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("code", text(rs.getString("code")));
        row.put("title", text(rs.getString("title")));
        row.put("level", text(rs.getString("level")));
        row.put("unit", text(rs.getString("unit")));
        row.put("description", text(rs.getString("description")));
        row.put("evidenceList", toStringList(rs.getString("evidence_json")));
        row.put("regulationClause", text(rs.getString("regulation_clause")));
        row.put("status", text(rs.getString("status")));
        row.put("createdBy", text(rs.getString("created_by")));
        row.put("createdAt", formatDateTime(rs.getObject("created_at")));
        row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return row;
    }

    private Map<String, Object> toTaskView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("issueId", rs.getLong("issue_id"));
        long parentId = rs.getLong("parent_id");
        row.put("parentId", rs.wasNull() ? null : parentId);
        row.put("title", text(rs.getString("title")));
        row.put("unit", text(rs.getString("unit")));
        row.put("assignee", text(rs.getString("assignee")));
        row.put("claimedBy", text(rs.getString("claimed_by")));
        row.put("createdBy", text(rs.getString("created_by")));
        row.put("status", text(rs.getString("status")));
        row.put("progress", rs.getInt("progress"));
        row.put("deadline", text(rs.getString("deadline")));
        row.put("reviewStatus", text(rs.getString("review_status")));
        row.put("reviewComment", text(rs.getString("review_comment")));
        row.put("measure", text(rs.getString("measure")));
        row.put("attachments", toAttachmentRecords(rs.getString("attachments_json")));
        row.put("feedback", text(rs.getString("feedback")));
        row.put("createdAt", formatDateTime(rs.getObject("created_at")));
        row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return row;
    }

    @Override
    public Map<String, Object> getTaskAttachment(String ownerKey, String actorUsername, Long taskId, Integer attachmentIndex) {
        Map<String, Object> actor = ensureActorWithRoles(ownerKey, actorUsername, Set.of("AUDIT_ADMIN", "AUDITOR", "ORG_ADMIN", "ORG_OPERATOR"));
        Map<String, Object> task = getTaskById(ownerKey, taskId);
        ensureTaskAttachmentAccessible(actorUsername, actor, task);

        List<Map<String, Object>> attachments = toAttachmentRecords(task.get("attachments"));
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("该任务没有可查看的证明材料");
        }
        int index = attachmentIndex == null ? -1 : attachmentIndex - 1;
        if (index < 0 || index >= attachments.size()) {
            throw new IllegalArgumentException("附件序号无效");
        }

        Map<String, Object> attachment = new HashMap<>(attachments.get(index));
        String filePath = text(attachment.get("filePath"));
        if (isBlank(filePath)) {
            String storedName = text(attachment.get("storedName"));
            if (isBlank(storedName)) {
                throw new IllegalArgumentException("附件文件不存在");
            }
            filePath = uploadRoot.resolve("rectification").resolve("task-attachments").resolve(storedName).toString();
        }

        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new IllegalArgumentException("附件文件不存在");
        }

        attachment.put("filePath", path.toString());
        attachment.put("downloadName", defaultIfBlank(text(attachment.get("fileName")), path.getFileName().toString()));
        return attachment;
    }

    private Map<String, Object> toShareView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("issueId", rs.getLong("issue_id"));
        row.put("issueTitle", text(rs.getString("issue_title")));
        row.put("fromDepartment", text(rs.getString("from_department")));
        row.put("toDepartment", text(rs.getString("to_department")));
        row.put("purpose", text(rs.getString("purpose")));
        row.put("status", text(rs.getString("status")));
        row.put("createdBy", text(rs.getString("created_by")));
        row.put("createdAt", formatDateTime(rs.getObject("created_at")));
        row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return row;
    }

    private Map<String, Object> toUserView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("username", text(rs.getString("username")));
        row.put("nickname", text(rs.getString("nickname")));
        row.put("role", text(rs.getString("role")));
        row.put("status", text(rs.getString("status")));
        row.put("unit", text(rs.getString("unit")));
        row.put("department", text(rs.getString("department")));
        row.put("createdAt", formatDateTime(rs.getObject("created_at")));
        row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return row;
    }

    private Map<String, Object> toDepartmentView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("name", text(rs.getString("name")));
        row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return row;
    }

    private Map<String, Object> toReminderRuleView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("name", text(rs.getString("name")));
        row.put("triggerType", text(rs.getString("trigger_type")));
        row.put("triggerValue", rs.getInt("trigger_value"));
        row.put("enabled", rs.getBoolean("enabled"));
        row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return row;
    }

    private Map<String, Object> toOrgDepartmentView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("name", text(rs.getString("name")));
        long parentId = rs.getLong("parent_id");
        row.put("parentId", rs.wasNull() ? null : parentId);
        row.put("leaderUsername", text(rs.getString("leader_username")));
        row.put("unit", text(rs.getString("unit")));
        row.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return row;
    }

    private Map<String, Object> getDepartmentById(String ownerKey, Long departmentId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, name, updated_at FROM rect_department_record WHERE owner_key=? AND id=?",
            (rs, i) -> toDepartmentView(rs),
            ownerKey,
            departmentId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("部门不存在");
        }
        return rows.get(0);
    }

    private void ensureDepartmentAllowed(String ownerKey, String department) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_department_record WHERE owner_key=? AND name=?",
            Integer.class,
            ownerKey,
            department
        );
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("部门不在可选列表中，请先在部门列表维护");
        }
    }

    private void ensureBindableDepartment(String department) {
        if ("未分配部门".equals(text(department))) {
            throw new IllegalArgumentException("未分配部门不能作为绑定部门，请选择真实部门");
        }
    }

    private Map<String, Object> getUserByUsername(String ownerKey, String username) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, username, nickname, role, status, unit, department, created_at, updated_at FROM rect_user_record WHERE owner_key=? AND username=?",
            (rs, i) -> toUserView(rs),
            ownerKey,
            username
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        return rows.get(0);
    }

    private Map<String, String> ensureOrgAdminActor(String ownerKey, String actorUsername) {
        Map<String, Object> actor = getUserByUsername(ownerKey, actorUsername);
        ensureUserActive(actor);
        if (!"ORG_ADMIN".equals(text(actor.get("role")))) {
            throw new IllegalArgumentException("仅被审单位管理员允许执行该操作");
        }
        String unit = text(actor.get("unit"));
        if (isBlank(unit)) {
            throw new IllegalArgumentException("当前账号未绑定单位");
        }
        Map<String, String> result = new HashMap<>();
        result.put("unit", unit);
        return result;
    }

    private Map<String, Object> ensureActorWithRoles(String ownerKey, String actorUsername, Set<String> allowedRoles) {
        Map<String, Object> actor = getUserByUsername(ownerKey, actorUsername);
        ensureUserActive(actor);
        String role = text(actor.get("role"));
        if (!allowedRoles.contains(role)) {
            throw new IllegalArgumentException("当前角色无权执行该操作");
        }
        return actor;
    }

    private void ensureUserInUnit(String ownerKey, String unit, String username) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_user_record WHERE owner_key=? AND username=? AND unit=? AND status<>'DELETED'",
            Integer.class,
            ownerKey,
            username,
            unit
        );
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("用户不在当前单位范围");
        }
    }

    private Map<String, Object> getOrgDepartmentById(String ownerKey, String unit, Long departmentId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, name, parent_id, leader_username, unit, updated_at FROM rect_org_department_record WHERE owner_key=? AND unit=? AND id=?",
            (rs, i) -> toOrgDepartmentView(rs),
            ownerKey,
            unit,
            departmentId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("部门不存在");
        }
        return rows.get(0);
    }

    private void ensureOrgDepartmentByName(String ownerKey, String unit, String departmentName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_org_department_record WHERE owner_key=? AND unit=? AND name=?",
            Integer.class,
            ownerKey,
            unit,
            departmentName
        );
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("部门不在当前单位可选范围");
        }
    }

    private void upsertDepartment(String ownerKey, String name, String now) {
        String departmentName = text(name);
        if (isBlank(departmentName) || isGarbledDepartmentName(departmentName)) {
            return;
        }
        jdbcTemplate.update(
            """
            INSERT INTO rect_department_record(owner_key, name, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at)
            """,
            ownerKey,
            departmentName,
            now
        );
    }

    private String sanitizeBusinessText(Object value) {
        String normalized = text(value);
        if (isLikelyGarbledText(normalized)) {
            throw new IllegalArgumentException("检测到乱码字符，请检查输入编码后重试");
        }
        return normalized;
    }

    private String sanitizeDepartmentName(Object value) {
        String normalized = text(value);
        if (isBlank(normalized)) {
            return "";
        }
        if (isGarbledDepartmentName(normalized)) {
            return "";
        }
        return normalized;
    }

    private String defaultUnitByUsername(String username) {
        String key = text(username);
        if ("admin".equalsIgnoreCase(key) || "audit_admin_demo".equalsIgnoreCase(key)) {
            return "审计局";
        }
        if ("auditor_demo".equalsIgnoreCase(key)) {
            return "审计一处";
        }
        if ("org_admin_demo".equalsIgnoreCase(key) || "org_operator_demo".equalsIgnoreCase(key)) {
            return "城建集团";
        }
        return "";
    }

    private boolean isLikelyGarbledText(String value) {
        String v = text(value);
        if (isBlank(v)) {
            return false;
        }
        if (v.contains("�")) {
            return true;
        }
        String plain = v.replaceAll("[\\s\\-_/()（）【】,.，。:：;；]", "");
        if (isBlank(plain)) {
            return false;
        }
        int badCount = 0;
        for (int i = 0; i < plain.length(); i++) {
            char ch = plain.charAt(i);
            if (ch == '?' || ch == '？') {
                badCount++;
            }
        }
        if (plain.matches("[?？]+")) {
            return true;
        }
        return plain.length() >= 3 && ((double) badCount / plain.length()) >= 0.5;
    }

    private boolean isGarbledDepartmentName(String value) {
        return isLikelyGarbledText(value);
    }

    private Map<String, Object> toNotificationBaseView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("type", text(rs.getString("type")));
        row.put("title", text(rs.getString("title")));
        row.put("content", text(rs.getString("content")));
        row.put("fromUser", text(rs.getString("from_user")));
        long relatedTaskId = rs.getLong("related_task_id");
        row.put("relatedTaskId", rs.wasNull() ? null : relatedTaskId);
        long relatedIssueId = rs.getLong("related_issue_id");
        row.put("relatedIssueId", rs.wasNull() ? null : relatedIssueId);
        row.put("createdAt", formatDateTime(rs.getObject("created_at")));
        return row;
    }

    private String nextIssueCode(String ownerKey) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM rect_issue_record WHERE owner_key=?",
            Integer.class,
            ownerKey
        );
        int serial = (count == null ? 0 : count) + 1;
        return "WT-" + java.time.LocalDate.now().getYear() + "-" + String.format("%03d", serial);
    }

    @SuppressWarnings("null")
    private Long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建记录失败");
        }
        return key.longValue();
    }

    private String now() {
        return DATE_TIME_FORMATTER.format(Instant.now());
    }

    private String formatDateTime(Object value) {
        if (value == null) return "";
        if (value instanceof Timestamp ts) {
            return DATE_TIME_FORMATTER.format(ts.toInstant());
        }
        if (value instanceof java.util.Date date) {
            return DATE_TIME_FORMATTER.format(date.toInstant());
        }
        String text = String.valueOf(value);
        if (text.contains("T")) {
            return text.replace("T", " ");
        }
        return text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> toStringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            return list.stream().map(this::attachmentLabel).filter(v -> !isBlank(v)).collect(Collectors.toList());
        }
        String raw = String.valueOf(value);
        if (isBlank(raw)) return new ArrayList<>();
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List<?> list) {
                return list.stream().map(this::attachmentLabel).filter(v -> !isBlank(v)).collect(Collectors.toList());
            }
            return new ArrayList<>();
        } catch (Exception ex) {
            return List.of(raw);
        }
    }

    private List<Map<String, Object>> toAttachmentRecords(Object value) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (value == null) {
            return records;
        }

        if (value instanceof List<?> list) {
            for (Object item : list) {
                addAttachmentRecord(records, item);
            }
            return records;
        }

        String raw = String.valueOf(value);
        if (isBlank(raw)) {
            return records;
        }

        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    addAttachmentRecord(records, item);
                }
                return records;
            }
        } catch (Exception ignore) {
            // fall through to plain string handling
        }

        addAttachmentRecord(records, raw);
        return records;
    }

    private void addAttachmentRecord(List<Map<String, Object>> records, Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> record = new HashMap<>();
            record.put("fileName", defaultIfBlank(text(map.get("fileName")), defaultIfBlank(text(map.get("originalName")), text(map.get("name")))));
            record.put("storedName", text(map.get("storedName")));
            record.put("filePath", text(map.get("filePath")));
            record.put("size", toLong(map.get("size")) == null ? 0L : toLong(map.get("size")));
            record.put("uploadedAt", defaultIfBlank(text(map.get("uploadedAt")), now()));
            if (!isBlank(text(record.get("fileName"))) || !isBlank(text(record.get("storedName")))) {
                records.add(record);
            }
            return;
        }

        String fileName = text(value);
        if (isBlank(fileName)) {
            return;
        }
        Map<String, Object> record = new HashMap<>();
        record.put("fileName", fileName);
        record.put("storedName", "");
        record.put("filePath", "");
        record.put("size", 0L);
        record.put("uploadedAt", now());
        records.add(record);
    }

    private void ensureTaskAttachmentAccessible(String actorUsername, Map<String, Object> actor, Map<String, Object> task) {
        String role = text(actor.get("role"));
        if ("AUDIT_ADMIN".equals(role) || "AUDITOR".equals(role)) {
            return;
        }
        if ("ORG_ADMIN".equals(role)) {
            if (!text(actor.get("unit")).equals(text(task.get("unit")))) {
                throw new IllegalArgumentException("仅可查看本单位任务附件");
            }
            return;
        }
        if ("ORG_OPERATOR".equals(role)) {
            String assignee = text(task.get("assignee"));
            String claimedBy = text(task.get("claimedBy"));
            if (!actorUsername.equals(assignee) && !actorUsername.equals(claimedBy)) {
                throw new IllegalArgumentException("仅可查看本人任务附件");
            }
            return;
        }
        throw new IllegalArgumentException("当前角色无权查看该附件");
    }

    private String attachmentLabel(Object value) {
        if (value instanceof Map<?, ?> map) {
            String fileName = defaultIfBlank(text(map.get("fileName")), defaultIfBlank(text(map.get("originalName")), text(map.get("name"))));
            if (!isBlank(fileName)) {
                return fileName;
            }
            String storedName = text(map.get("storedName"));
            if (!isBlank(storedName)) {
                return storedName;
            }
            return text(map.get("filePath"));
        }
        return text(value);
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String getFileExt(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return "";
        return fileName.substring(idx + 1).toLowerCase();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
