import {
  addIssueSupervisionApi,
  addRuleApi,
  acceptTaskApi,
  claimTaskApi,
  createReminderRuleApi,
  createIssueApi,
  createRectificationTaskApi,
  splitIssueTasksApi,
  createUserApi,
  createDepartmentApi,
  deleteUserApi,
  deleteDepartmentApi,
  deleteIssueApi,
  deleteIssueSupervisionApi,
  dispatchSubTaskApi,
  deleteTaskApi,
  deleteReminderRuleApi,
  fetchRectificationSnapshotApi,
  downloadTaskAttachmentApi,
  listDepartmentsApi,
  listReminderRulesApi,
  listDeletedUsersApi,
  interactNotificationApi,
  markNotificationReadApi,
  reviewTaskApi,
  restoreUserApi,
  submitOrgReportApi,
  submitTaskExecutionApi,
  uploadTaskAttachmentApi,
  bindUserDepartmentApi,
  updateDepartmentApi,
  updateReminderRuleApi,
  updateRuleApi,
  updateTaskDeadlineApi,
  updateUserApi,
  updateUserRoleApi,
  updateUserStatusApi,
  listOrgDepartmentsApi,
  createOrgDepartmentApi,
  updateOrgDepartmentApi,
  deleteOrgDepartmentApi,
  listOrgMembersApi,
  createOrgMemberApi,
  updateOrgMemberApi,
  deleteOrgMemberApi,
  runReminderScanApi
} from '../api/rectification'

const RECTIFICATION_SNAPSHOT_CACHE_KEY = 'app:rectification:snapshot-cache:v1'

const EMPTY_SNAPSHOT = {
  issues: [],
  tasks: [],
  rules: [],
  users: [],
  departments: [],
  reports: [],
  notifications: []
}

let snapshotCache = { ...EMPTY_SNAPSHOT }

function loadCachedSnapshot() {
  if (typeof window === 'undefined') {
    return { ...EMPTY_SNAPSHOT }
  }
  try {
    const raw = window.localStorage.getItem(RECTIFICATION_SNAPSHOT_CACHE_KEY)
    if (!raw) {
      return { ...EMPTY_SNAPSHOT }
    }
    const parsed = JSON.parse(raw)
    return normalizeSnapshot(parsed)
  } catch {
    return { ...EMPTY_SNAPSHOT }
  }
}

function persistSnapshotCache(data) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.setItem(RECTIFICATION_SNAPSHOT_CACHE_KEY, JSON.stringify(normalizeSnapshot(data)))
  } catch {
    // ignore cache persistence failures
  }
}

snapshotCache = loadCachedSnapshot()

function notifyRectificationSnapshotChanged() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new Event('rectification-snapshot-updated'))
}

function clone(data) {
  return JSON.parse(JSON.stringify(data || {}))
}

function unwrap(response) {
  return response?.data?.data
}

function normalizeSnapshot(data) {
  return {
    issues: Array.isArray(data?.issues) ? data.issues : [],
    tasks: Array.isArray(data?.tasks) ? data.tasks : [],
    rules: Array.isArray(data?.rules) ? data.rules : [],
    users: Array.isArray(data?.users) ? data.users : [],
    departments: Array.isArray(data?.departments) ? data.departments : [],
    reports: Array.isArray(data?.reports) ? data.reports : [],
    notifications: Array.isArray(data?.notifications) ? data.notifications : []
  }
}

export async function fetchRectificationSnapshot() {
  const resp = await fetchRectificationSnapshotApi()
  snapshotCache = normalizeSnapshot(unwrap(resp))
  persistSnapshotCache(snapshotCache)
  return clone(snapshotCache)
}

export function getRectificationSnapshot() {
  return clone(snapshotCache)
}

export async function createIssue(payload) {
  const resp = await createIssueApi(payload)
  return unwrap(resp)
}

export async function createRectificationTask(issueId, payload) {
  const resp = await createRectificationTaskApi(issueId, payload)
  return unwrap(resp)
}

export async function splitIssueTasks(issueId, tasks) {
  const resp = await splitIssueTasksApi(issueId, { tasks })
  return unwrap(resp)
}

export async function dispatchSubTask(parentTaskId, payload) {
  const resp = await dispatchSubTaskApi(parentTaskId, payload)
  return unwrap(resp)
}

export async function deleteTask(taskId) {
  await deleteTaskApi(taskId)
  notifyRectificationSnapshotChanged()
  return true
}

export async function claimTask(taskId) {
  const resp = await claimTaskApi(taskId)
  return unwrap(resp)
}

export async function submitTaskExecution(taskId, payload) {
  const resp = await submitTaskExecutionApi(taskId, payload)
  notifyRectificationSnapshotChanged()
  return unwrap(resp)
}

export async function uploadTaskAttachment(taskId, file) {
  const resp = await uploadTaskAttachmentApi(taskId, file)
  notifyRectificationSnapshotChanged()
  return unwrap(resp)
}

export async function downloadTaskAttachment(taskId, attachmentIndex) {
  const resp = await downloadTaskAttachmentApi(taskId, attachmentIndex)
  return resp?.data
}

export async function reviewTask(taskId, payload) {
  const resp = await reviewTaskApi(taskId, payload)
  return unwrap(resp)
}

export async function updateRule(ruleId, enabled) {
  const resp = await updateRuleApi(ruleId, enabled)
  return unwrap(resp)
}

export async function addRule(ruleName) {
  const resp = await addRuleApi(ruleName)
  return unwrap(resp)
}

export async function listReminderRules() {
  const resp = await listReminderRulesApi()
  const data = unwrap(resp)
  return Array.isArray(data) ? data : []
}

export async function createReminderRule(payload) {
  const resp = await createReminderRuleApi(payload)
  return unwrap(resp)
}

export async function updateReminderRule(ruleId, payload) {
  const resp = await updateReminderRuleApi(ruleId, payload)
  return unwrap(resp)
}

export async function deleteReminderRule(ruleId) {
  await deleteReminderRuleApi(ruleId)
  return true
}

export async function runReminderScan() {
  const resp = await runReminderScanApi()
  return unwrap(resp)
}

export async function updateUserRole(userId, role) {
  const resp = await updateUserRoleApi(userId, role)
  return unwrap(resp)
}

export async function updateUserStatus(userId, status) {
  const resp = await updateUserStatusApi(userId, status)
  return unwrap(resp)
}

export async function createUser(payload) {
  const resp = await createUserApi(payload)
  return unwrap(resp)
}

export async function updateUserProfile(userId, payload) {
  const resp = await updateUserApi(userId, payload)
  return unwrap(resp)
}

export async function deleteUser(userId) {
  await deleteUserApi(userId)
  return true
}

export async function bindUserDepartment(userId, department) {
  const resp = await bindUserDepartmentApi(userId, department)
  return unwrap(resp)
}

export async function listDepartments() {
  const resp = await listDepartmentsApi()
  const data = unwrap(resp)
  return Array.isArray(data) ? data : []
}

export async function createDepartment(name) {
  const resp = await createDepartmentApi(name)
  return unwrap(resp)
}

export async function updateDepartment(departmentId, name) {
  const resp = await updateDepartmentApi(departmentId, name)
  return unwrap(resp)
}

export async function deleteDepartment(departmentId) {
  await deleteDepartmentApi(departmentId)
  return true
}

export async function listDeletedUsers() {
  const resp = await listDeletedUsersApi()
  return Array.isArray(unwrap(resp)) ? unwrap(resp) : []
}

export async function restoreUser(userId) {
  const resp = await restoreUserApi(userId)
  return unwrap(resp)
}

export async function submitOrgReport(payload) {
  const resp = await submitOrgReportApi(payload)
  return unwrap(resp)
}

export function getGlobalOverview() {
  const data = snapshotCache
  const totalIssues = data.issues.length
  const completedIssues = data.issues.filter((item) => item.status === '已完成').length
  const inProgressIssues = data.issues.filter((item) => item.status === '整改中').length
  const overdueTasks = data.tasks.filter((item) => item.status !== '已完成' && item.deadline && new Date(item.deadline).getTime() < Date.now()).length

  return {
    totalIssues,
    completedIssues,
    inProgressIssues,
    overdueTasks,
    focusIssues: data.issues.filter((item) => ['高', '重大'].includes(item.level) && item.status !== '已完成')
  }
}

export function acceptTask(taskId) {
  return acceptTaskApi(taskId).then(unwrap)
}

export function updateTaskDeadline(taskId, deadline) {
  return updateTaskDeadlineApi(taskId, deadline).then(unwrap)
}

export function addIssueSupervision(issueId, payload) {
  return addIssueSupervisionApi(issueId, payload).then(unwrap)
}

export function getIssueSupervisions(issueId) {
  const data = snapshotCache
  const issue = data.issues.find((item) => item.id === issueId)
  if (!issue) return []
  return Array.isArray(issue.supervisions) ? issue.supervisions : []
}

export function deleteIssue(issueId) {
  return deleteIssueApi(issueId).then(() => {
    notifyRectificationSnapshotChanged()
    return true
  })
}

export function deleteIssueSupervision(issueId, supervisionId) {
  return deleteIssueSupervisionApi(issueId, supervisionId).then(() => true)
}

export function getUserNotifications(username) {
  const data = snapshotCache
  const user = String(username || '').trim()
  if (!user) return []

  const notifications = Array.isArray(data.notifications) ? data.notifications : []
  return notifications.map((item) => ({
    ...item,
    isRead: Array.isArray(item.readBy) ? item.readBy.includes(user) : false
  }))
}

export function markNotificationRead(notificationId) {
  return markNotificationReadApi(notificationId).then(() => {
    notifyRectificationSnapshotChanged()
    return true
  })
}

export function interactNotification(notificationId, payload) {
  return interactNotificationApi(notificationId, payload).then((resp) => {
    notifyRectificationSnapshotChanged()
    return unwrap(resp)
  })
}

export async function listOrgDepartments() {
  const resp = await listOrgDepartmentsApi()
  const data = unwrap(resp)
  return Array.isArray(data) ? data : []
}

export async function createOrgDepartment(payload) {
  const resp = await createOrgDepartmentApi(payload)
  return unwrap(resp)
}

export async function updateOrgDepartment(departmentId, payload) {
  const resp = await updateOrgDepartmentApi(departmentId, payload)
  return unwrap(resp)
}

export async function deleteOrgDepartment(departmentId) {
  await deleteOrgDepartmentApi(departmentId)
  return true
}

export async function listOrgMembers(department = '') {
  const resp = await listOrgMembersApi(department)
  const data = unwrap(resp)
  return Array.isArray(data) ? data : []
}

export async function createOrgMember(payload) {
  const resp = await createOrgMemberApi(payload)
  return unwrap(resp)
}

export async function updateOrgMember(userId, payload) {
  const resp = await updateOrgMemberApi(userId, payload)
  return unwrap(resp)
}

export async function deleteOrgMember(userId) {
  await deleteOrgMemberApi(userId)
  return true
}
