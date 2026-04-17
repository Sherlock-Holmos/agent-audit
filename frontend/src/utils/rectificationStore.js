const STORAGE_KEY = 'rectification_center_data_v1'

function nextId(prefix) {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 1000)}`
}

function nowText() {
  return new Date().toLocaleString('zh-CN', { hour12: false })
}

function seedData() {
  return {
    issues: [
      {
        id: 'issue_1001',
        code: 'WT-2026-001',
        title: '专项资金报销附件缺失',
        level: '高',
        unit: '城建集团',
        description: '抽查 23 笔报销中 6 笔缺少审批附件。',
        status: '整改中',
        createdBy: 'auditor_demo',
        createdAt: nowText(),
        taskId: 'task_1001'
      },
      {
        id: 'issue_1002',
        code: 'WT-2026-002',
        title: '采购比价流程执行不到位',
        level: '中',
        unit: '交通投资公司',
        description: '5 个采购事项未留存完整比价过程记录。',
        status: '待派发',
        createdBy: 'auditor_demo',
        createdAt: nowText(),
        taskId: ''
      }
    ],
    tasks: [
      {
        id: 'task_1001',
        issueId: 'issue_1001',
        title: '资金报销附件补齐与流程补正',
        unit: '城建集团',
        assignee: 'org_admin_demo',
        createdBy: 'auditor_demo',
        status: '执行中',
        progress: 45,
        deadline: '2026-05-15',
        reviewStatus: '待审核',
        reviewComment: '',
        measure: '',
        attachments: [],
        feedback: '',
        parentId: '',
        claimedBy: '',
        createdAt: nowText(),
        updatedAt: nowText()
      }
    ],
    rules: [
      { id: 'rule_1', name: '重点问题逾期自动预警', enabled: true, updatedAt: nowText() },
      { id: 'rule_2', name: '证据材料缺失禁止提交审核', enabled: true, updatedAt: nowText() }
    ],
    users: [
      { id: 'u_1', username: 'admin', nickname: '系统管理员', role: 'AUDIT_ADMIN', status: 'ENABLED', department: '审计局' },
      { id: 'u_2', username: 'auditor_demo', nickname: '审计员-李明', role: 'AUDITOR', status: 'ENABLED', department: '审计一处' },
      { id: 'u_3', username: 'org_admin_demo', nickname: '城建集团管理员', role: 'ORG_ADMIN', status: 'ENABLED', department: '城建集团' },
      { id: 'u_4', username: 'org_operator_demo', nickname: '城建集团经办', role: 'ORG_OPERATOR', status: 'ENABLED', department: '城建集团' }
    ],
    reports: []
  }
}

function clone(data) {
  return JSON.parse(JSON.stringify(data))
}

export function loadRectificationData() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.issues) || !Array.isArray(parsed.tasks)) {
      const seeded = seedData()
      localStorage.setItem(STORAGE_KEY, JSON.stringify(seeded))
      return seeded
    }
    return parsed
  } catch {
    const seeded = seedData()
    localStorage.setItem(STORAGE_KEY, JSON.stringify(seeded))
    return seeded
  }
}

export function saveRectificationData(data) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

export function getRectificationSnapshot() {
  return clone(loadRectificationData())
}

export function createIssue(payload, username) {
  const data = loadRectificationData()
  const issue = {
    id: nextId('issue'),
    code: `WT-${new Date().getFullYear()}-${String(data.issues.length + 1).padStart(3, '0')}`,
    title: payload.title,
    level: payload.level || '中',
    unit: payload.unit,
    description: payload.description || '',
    status: '待派发',
    createdBy: username,
    createdAt: nowText(),
    taskId: ''
  }
  data.issues.unshift(issue)
  saveRectificationData(data)
  return issue
}

export function createRectificationTask(issueId, payload, username) {
  const data = loadRectificationData()
  const issue = data.issues.find((item) => item.id === issueId)
  if (!issue) throw new Error('问题不存在')
  const task = {
    id: nextId('task'),
    issueId,
    title: payload.title,
    unit: issue.unit,
    assignee: payload.assignee,
    createdBy: username,
    status: '待接收',
    progress: 0,
    deadline: payload.deadline,
    reviewStatus: '待审核',
    reviewComment: '',
    measure: '',
    attachments: [],
    feedback: '',
    parentId: '',
    claimedBy: '',
    createdAt: nowText(),
    updatedAt: nowText()
  }
  data.tasks.unshift(task)
  issue.taskId = task.id
  issue.status = '整改中'
  saveRectificationData(data)
  return task
}

export function dispatchSubTask(parentTaskId, payload, username) {
  const data = loadRectificationData()
  const parent = data.tasks.find((item) => item.id === parentTaskId)
  if (!parent) throw new Error('主任务不存在')
  const task = {
    id: nextId('subtask'),
    issueId: parent.issueId,
    title: payload.title,
    unit: parent.unit,
    assignee: payload.assignee,
    createdBy: username,
    status: '待认领',
    progress: 0,
    deadline: payload.deadline || parent.deadline,
    reviewStatus: '待审核',
    reviewComment: '',
    measure: '',
    attachments: [],
    feedback: '',
    parentId: parentTaskId,
    claimedBy: '',
    createdAt: nowText(),
    updatedAt: nowText()
  }
  data.tasks.unshift(task)
  parent.updatedAt = nowText()
  saveRectificationData(data)
  return task
}

export function claimTask(taskId, username) {
  const data = loadRectificationData()
  const task = data.tasks.find((item) => item.id === taskId)
  if (!task) throw new Error('任务不存在')
  task.claimedBy = username
  task.status = '执行中'
  task.updatedAt = nowText()
  saveRectificationData(data)
  return task
}

export function submitTaskExecution(taskId, payload) {
  const data = loadRectificationData()
  const task = data.tasks.find((item) => item.id === taskId)
  if (!task) throw new Error('任务不存在')
  task.measure = payload.measure || task.measure
  task.feedback = payload.feedback || task.feedback
  task.attachments = Array.isArray(payload.attachments) ? payload.attachments : task.attachments
  task.progress = Number(payload.progress ?? task.progress)
  task.status = task.progress >= 100 ? '待审核' : '执行中'
  task.reviewStatus = '待审核'
  task.updatedAt = nowText()
  saveRectificationData(data)
  return task
}

export function reviewTask(taskId, payload) {
  const data = loadRectificationData()
  const task = data.tasks.find((item) => item.id === taskId)
  if (!task) throw new Error('任务不存在')

  const passed = Boolean(payload.passed)
  task.reviewStatus = passed ? '审核通过' : '退回修改'
  task.reviewComment = payload.comment || ''
  task.status = passed ? '已完成' : '执行中'
  task.progress = passed ? 100 : Math.min(task.progress, 95)
  task.updatedAt = nowText()

  if (!task.parentId) {
    const issue = data.issues.find((item) => item.id === task.issueId)
    if (issue) {
      issue.status = passed ? '已完成' : '整改中'
    }
  }

  saveRectificationData(data)
  return task
}

export function updateRule(ruleId, enabled) {
  const data = loadRectificationData()
  const rule = data.rules.find((item) => item.id === ruleId)
  if (!rule) throw new Error('规则不存在')
  rule.enabled = Boolean(enabled)
  rule.updatedAt = nowText()
  saveRectificationData(data)
  return rule
}

export function addRule(ruleName) {
  const data = loadRectificationData()
  const rule = {
    id: nextId('rule'),
    name: ruleName,
    enabled: true,
    updatedAt: nowText()
  }
  data.rules.unshift(rule)
  saveRectificationData(data)
  return rule
}

export function updateUserRole(userId, role) {
  const data = loadRectificationData()
  const user = data.users.find((item) => item.id === userId)
  if (!user) throw new Error('用户不存在')
  user.role = role
  saveRectificationData(data)
  return user
}

export function submitOrgReport(payload) {
  const data = loadRectificationData()
  const report = {
    id: nextId('report'),
    unit: payload.unit,
    title: payload.title,
    summary: payload.summary,
    submitter: payload.submitter,
    createdAt: nowText()
  }
  data.reports.unshift(report)
  saveRectificationData(data)
  return report
}

export function getGlobalOverview() {
  const data = loadRectificationData()
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
