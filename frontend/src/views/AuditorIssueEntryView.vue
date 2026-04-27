<template>
  <div class="auditor-workbench page-wrap">
    <AuditorIssueToolbar
      :filters="filters"
      @update:filters="handleFilterChange"
      @search="handleSearch"
      @reset="handleReset"
      @create="openIssueDialog"
    />

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">审计问题整改台账</div>
            <div class="page-subtitle">在同一张表中完成录入、下达、拆分、督办、进度跟踪和在线审核</div>
          </div>
          <el-tag type="info">共 {{ displayIssues.length }} 条问题</el-tag>
        </div>
      </template>

      <AppDataTable
        :data="displayIssues"
        :loading="loading"
        layout-storage-key="app:table-layout:auditor:issue-entry"
        :show-pagination="false"
        :with-card="false"
        :empty-text="'暂无问题，请先点击“新增问题”录入。'"
      >
        <template #default>
          <el-table-column prop="code" label="编号" width="130" />
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="150" />
          <el-table-column prop="level" label="等级" width="90" align="center" />
          <el-table-column prop="status" label="状态" width="110" align="center" />
          <el-table-column label="进度" width="180">
            <template #default="scope">
              <div v-if="getRootTask(scope.row)">
                <el-progress :percentage="issueProgress(scope.row)" :stroke-width="14" />
                <div class="table-subtext">{{ issueReviewStatus(scope.row) }}</div>
              </div>
              <span v-else>未下达</span>
            </template>
          </el-table-column>
          <el-table-column label="任务数" width="90" align="center">
            <template #default="scope">{{ issueTaskCount(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="180">
            <template #default="scope">{{ issueLatestTime(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="440" fixed="right">
            <template #default="scope">
              <div class="row-actions">
                <el-button link type="primary" @click="openProgressDrawer(scope.row)">进度详情</el-button>
                <el-button v-if="!getRootTask(scope.row)" link type="primary" @click="openAssignDialog(scope.row)">下达任务</el-button>
                <el-button v-if="getRootTask(scope.row)" link type="success" @click="openSplitDialog(scope.row)">拆分任务</el-button>
                <el-button link type="warning" @click="openSupervisionDialog(scope.row)">督办</el-button>
                <el-button v-if="getRootTask(scope.row)" link type="success" @click="openReviewDrawer(scope.row)">在线审核</el-button>
                <el-button link type="danger" @click="removeIssue(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <AuditorIssueFormDialog
      v-model="issueDialogVisible"
      :unit-options="unitOptions"
      :submitting="submitting"
      @submit="createIssueRecord"
    />

    <el-dialog v-model="assignDialogVisible" title="下达整改任务" width="560px" destroy-on-close>
      <el-form :model="taskForm" label-width="95px">
        <el-form-item label="问题标题">
          <el-input v-model="taskForm.issueTitle" disabled />
        </el-form-item>
        <el-form-item label="任务标题">
          <el-input v-model="taskForm.title" />
        </el-form-item>
        <el-form-item label="责任人账号">
          <el-select v-model="taskForm.assignee" filterable placeholder="请选择单位管理员" style="width: 100%">
            <el-option
              v-for="user in assigneeOptions"
              :key="user.username"
              :label="`${user.nickname || user.username} (${user.username})`"
              :value="user.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="taskForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSubmitting" @click="confirmAssign">确认下达</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="splitDialogVisible" title="跨单位拆分整改任务" width="900px" destroy-on-close>
      <div class="dialog-topline">
        <div class="dialog-summary">{{ splitIssue?.title || '未选择问题' }}</div>
        <el-button type="primary" plain @click="addSplitRow">新增拆分项</el-button>
      </div>
      <AppDataTable :data="splitRows" layout-storage-key="app:table-layout:auditor:task-assign-split" :show-pagination="false" :with-card="false">
        <template #default>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="任务标题" min-width="220">
            <template #default="scope">
              <el-input v-model="scope.row.title" />
            </template>
          </el-table-column>
          <el-table-column label="责任单位" width="180">
            <template #default="scope">
              <el-select
                v-model="scope.row.unit"
                filterable
                placeholder="请选择单位"
                style="width: 100%"
                @change="() => onSplitUnitChange(scope.row)"
              >
                <el-option v-for="item in unitOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="责任人" width="220">
            <template #default="scope">
              <el-select v-model="scope.row.assignee" filterable placeholder="请选择单位管理员" style="width: 100%">
                <el-option
                  v-for="user in getAssigneeOptionsByUnit(scope.row.unit)"
                  :key="user.username"
                  :label="`${user.nickname || user.username} (${user.username})`"
                  :value="user.username"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="截止日期" width="150">
            <template #default="scope">
              <el-date-picker v-model="scope.row.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="scope">
              <el-button link type="danger" @click="removeSplitRow(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </template>
      </AppDataTable>
      <template #footer>
        <el-button @click="splitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSubmitting" @click="confirmSplitAssign">确认拆分下达</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="supervisionDialogVisible" title="发起问题督办" width="560px" destroy-on-close>
      <el-form :model="supervisionForm" label-width="90px">
        <el-form-item label="问题标题">
          <el-input v-model="supervisionForm.issueTitle" disabled />
        </el-form-item>
        <el-form-item label="督办说明">
          <el-input v-model="supervisionForm.note" type="textarea" :rows="4" placeholder="请输入本次督办要求与时限" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supervisionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="supervisionSubmitting" @click="submitSupervision">确认发起</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="progressDrawerVisible" title="整改进度详情" size="58%" destroy-on-close>
      <template v-if="activeIssue">
        <el-descriptions :column="2" border class="drawer-descriptions">
          <el-descriptions-item label="问题编号">{{ activeIssue.code }}</el-descriptions-item>
          <el-descriptions-item label="问题标题">{{ activeIssue.title }}</el-descriptions-item>
          <el-descriptions-item label="被审单位">{{ activeIssue.unit }}</el-descriptions-item>
          <el-descriptions-item label="问题等级">{{ activeIssue.level }}</el-descriptions-item>
          <el-descriptions-item label="问题状态">{{ activeIssue.status }}</el-descriptions-item>
          <el-descriptions-item label="整改进度">{{ issueProgress(activeIssue) }}%</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">{{ activeIssue.description || '无' }}</el-descriptions-item>
          <el-descriptions-item label="相关证据" :span="2">
            <div v-if="(activeIssue.evidenceList || []).length" class="tag-wrap">
              <el-tag v-for="item in activeIssue.evidenceList" :key="item" size="small">{{ item }}</el-tag>
            </div>
            <span v-else>无</span>
          </el-descriptions-item>
          <el-descriptions-item label="制度/标准条款" :span="2">{{ activeIssue.regulationClause || '无' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">流程轨迹</el-divider>
        <el-timeline v-if="activeIssueTimeline.length">
          <el-timeline-item
            v-for="item in activeIssueTimeline"
            :key="`${item.title}-${item.time}`"
            :timestamp="item.time"
            :type="item.type"
          >
            <div class="timeline-title">{{ item.title }}</div>
            <div class="timeline-text">{{ item.text }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无流程记录" />

        <el-divider content-position="left">任务节点</el-divider>
        <el-collapse v-if="selectedIssueTasks.length" v-model="taskCollapseActive" accordion>
          <el-collapse-item v-for="task in selectedIssueTasks" :key="task.id" :name="task.id">
            <template #title>
              <div class="task-collapse-title">
                <span>{{ taskLabel(task) }}</span>
                <el-tag size="small" type="info">{{ task.status || '未知' }}</el-tag>
                <el-tag size="small" :type="task.reviewStatus === '待审核' ? 'warning' : task.reviewStatus === '通过' ? 'success' : 'info'">
                  {{ task.reviewStatus || '未审核' }}
                </el-tag>
              </div>
            </template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="任务标题">{{ task.title }}</el-descriptions-item>
              <el-descriptions-item label="责任单位">{{ task.unit || '无' }}</el-descriptions-item>
              <el-descriptions-item label="责任人">{{ task.assignee || '未分配' }}</el-descriptions-item>
              <el-descriptions-item label="进度">{{ Number(task.progress || 0) }}%</el-descriptions-item>
              <el-descriptions-item label="截止日期">{{ task.deadline || '无' }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ task.updatedAt || task.createdAt || '无' }}</el-descriptions-item>
              <el-descriptions-item label="整改措施" :span="2">{{ task.measure || '无' }}</el-descriptions-item>
              <el-descriptions-item label="执行反馈" :span="2">{{ task.feedback || '无' }}</el-descriptions-item>
              <el-descriptions-item label="证明材料" :span="2">
                <div v-if="(task.attachments || []).length" class="tag-wrap">
                  <el-tag v-for="item in task.attachments" :key="item" size="small">{{ item }}</el-tag>
                </div>
                <span v-else>无</span>
              </el-descriptions-item>
            </el-descriptions>
            <div class="drawer-actions">
              <el-button link type="primary" @click="openMaterialDrawer(task)">查看提交材料</el-button>
            </div>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else description="当前问题尚未形成任务节点" />
      </template>
    </el-drawer>

    <el-drawer v-model="reviewDrawerVisible" title="在线审核结果" size="50%" destroy-on-close>
      <template v-if="activeReviewIssue">
        <div class="drawer-summary">{{ activeReviewIssue.title }}</div>
        <AppDataTable
          :data="selectedReviewTasks"
          layout-storage-key="app:table-layout:auditor:issue-review"
          :show-pagination="false"
          :with-card="false"
          :empty-text="'暂无可审核的提交材料。'"
        >
          <template #default>
            <el-table-column prop="title" label="任务标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="assignee" label="责任人" width="140" />
            <el-table-column label="进度" width="140">
              <template #default="scope">{{ Number(scope.row.progress || 0) }}%</template>
            </el-table-column>
            <el-table-column prop="reviewStatus" label="审核状态" width="120" />
            <el-table-column label="材料" width="90" align="center">
              <template #default="scope">{{ (scope.row.attachments || []).length }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="scope">
                <el-button link type="primary" @click="openMaterialDrawer(scope.row)">查看材料</el-button>
                <el-button v-if="scope.row.reviewStatus === '待审核'" link type="success" @click="reviewTaskWithDecision(scope.row, true)">通过</el-button>
                <el-button v-if="scope.row.reviewStatus === '待审核'" link type="danger" @click="reviewTaskWithDecision(scope.row, false)">退回</el-button>
              </template>
            </el-table-column>
          </template>
        </AppDataTable>
      </template>
    </el-drawer>

    <el-drawer v-model="materialDrawerVisible" title="提交材料" size="42%" destroy-on-close>
      <el-descriptions v-if="activeMaterialTask" :column="1" border>
        <el-descriptions-item label="任务标题">{{ activeMaterialTask.title }}</el-descriptions-item>
        <el-descriptions-item label="责任单位">{{ activeMaterialTask.unit || '无' }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ activeMaterialTask.assignee || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="整改措施">{{ activeMaterialTask.measure || '无' }}</el-descriptions-item>
        <el-descriptions-item label="执行反馈">{{ activeMaterialTask.feedback || '无' }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">{{ activeMaterialTask.reviewStatus || '无' }}</el-descriptions-item>
        <el-descriptions-item label="证明材料">
          <div v-if="(activeMaterialTask.attachments || []).length" class="tag-wrap">
            <el-tag v-for="item in activeMaterialTask.attachments" :key="item" size="small">{{ item }}</el-tag>
          </div>
          <span v-else>无</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addIssueSupervision, createIssue, createRectificationTask, deleteIssue, getIssueSupervisions, reviewTask, splitIssueTasks } from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import { getCurrentUser } from '../utils/currentUser'
import AuditorIssueToolbar from '../components/auditor-issues/AuditorIssueToolbar.vue'
import AuditorIssueFormDialog from '../components/auditor-issues/AuditorIssueFormDialog.vue'
import AppDataTable from '../components/shared/AppDataTable.vue'

const user = getCurrentUser()
const { snapshot, refreshSnapshot } = useRectificationSnapshot()
const fallbackUnitOptions = ['城建集团', '交通投资公司', '审计一处', '审计二处', '审计三处', '审计四处']

const loading = ref(false)
const submitting = ref(false)
const taskSubmitting = ref(false)
const supervisionSubmitting = ref(false)
const issueDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const splitDialogVisible = ref(false)
const supervisionDialogVisible = ref(false)
const progressDrawerVisible = ref(false)
const reviewDrawerVisible = ref(false)
const materialDrawerVisible = ref(false)
const activeIssue = ref(null)
const activeReviewIssue = ref(null)
const activeMaterialTask = ref(null)
const taskCollapseActive = ref('')

const filters = reactive({
  keyword: '',
  level: ''
})

const taskForm = reactive({
  issueId: '',
  issueTitle: '',
  title: '',
  assignee: '',
  deadline: ''
})

const splitIssue = ref(null)
const splitRows = ref([])

const supervisionForm = reactive({
  issueId: '',
  issueTitle: '',
  note: ''
})

const myIssues = computed(() => {
  const username = user.username || 'auditor_demo'
  return snapshot.value.issues.filter((item) => item.createdBy === username)
})

const filteredIssues = computed(() => {
  const text = filters.keyword.trim()
  return myIssues.value.filter((item) => {
    const matchKeyword = !text || String(item.title).includes(text) || String(item.unit).includes(text)
    const matchLevel = !filters.level || item.level === filters.level
    return matchKeyword && matchLevel
  })
})

const displayIssues = computed(() => filteredIssues.value)

const unitOptions = computed(() => {
  const units = new Set()
  snapshot.value.departments.forEach((item) => {
    const name = String(item?.name || '').trim()
    if (name && name !== '未分配部门') {
      units.add(name)
    }
  })
  snapshot.value.issues.forEach((item) => {
    if (item.unit) units.add(item.unit)
  })
  snapshot.value.tasks.forEach((item) => {
    if (item.unit) units.add(item.unit)
  })
  snapshot.value.reports.forEach((item) => {
    if (item.unit) units.add(item.unit)
  })
  if (!units.size) {
    fallbackUnitOptions.forEach((item) => units.add(item))
  }
  return Array.from(units)
})

const assigneeOptions = computed(() => getAssigneeOptionsByUnit(activeIssue.value?.unit || ''))

const selectedIssueTasks = computed(() => {
  if (!activeIssue.value) return []
  return collectIssueTasks(activeIssue.value)
})

const selectedReviewTasks = computed(() => {
  if (!activeReviewIssue.value) return []
  return collectIssueTasks(activeReviewIssue.value)
})

const activeIssueTimeline = computed(() => {
  if (!activeIssue.value) return []
  return buildIssueTimeline(activeIssue.value)
})

function handleFilterChange(nextFilters) {
  filters.keyword = nextFilters.keyword || ''
  filters.level = nextFilters.level || ''
}

function handleSearch() {
  loading.value = true
  window.setTimeout(() => {
    loading.value = false
  }, 120)
}

function handleReset() {
  filters.keyword = ''
  filters.level = ''
  handleSearch()
}

async function createIssueRecord(payload) {
  submitting.value = true
  try {
    await createIssue({
      title: payload.title,
      level: payload.level,
      unit: payload.unit,
      description: payload.description,
      evidenceList: payload.evidenceList,
      regulationClause: payload.regulationClause
    })
    await refreshSnapshot()
    issueDialogVisible.value = false
    ElMessage.success('问题录入成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '问题录入失败')
  } finally {
    submitting.value = false
  }
}

function openIssueDialog() {
  issueDialogVisible.value = true
}

function getRootTask(issue) {
  if (!issue?.taskId) return null
  return snapshot.value.tasks.find((item) => item.id === issue.taskId) || null
}

function getAssigneeOptionsByUnit(unit) {
  return snapshot.value.users.filter(
    (item) => item.role === 'ORG_ADMIN' && item.status === 'ENABLED' && (!unit || item.unit === unit)
  )
}

function getDefaultAssignee(unit) {
  const options = getAssigneeOptionsByUnit(unit)
  if (options.length) return options[0].username
  return snapshot.value.users.find((item) => item.role === 'ORG_ADMIN' && item.status === 'ENABLED')?.username || 'org_admin_demo'
}

function issueTaskCount(issue) {
  return collectIssueTasks(issue).length
}

function issueProgress(issue) {
  return Number(getRootTask(issue)?.progress || 0)
}

function issueReviewStatus(issue) {
  return getRootTask(issue)?.reviewStatus || '未下达'
}

function issueLatestTime(issue) {
  const times = [issue?.updatedAt, issue?.createdAt]
  collectIssueTasks(issue).forEach((task) => {
    times.push(task.updatedAt || task.createdAt)
  })
  getIssueSupervisions(issue?.id).forEach((item) => times.push(item.createdAt))
  return latestTime(times)
}

function latestTime(values) {
  const candidates = values
    .map((item) => normalizeTime(item))
    .filter((item) => item)
    .sort((left, right) => right - left)
  if (!candidates.length) return '无'
  return new Date(candidates[0]).toLocaleString('zh-CN', { hour12: false })
}

function normalizeTime(value) {
  if (!value) return null
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed.getTime()
}

function collectIssueTasks(issue) {
  const rootTask = getRootTask(issue)
  if (!rootTask) return []

  const childrenMap = new Map()
  snapshot.value.tasks.forEach((task) => {
    if (!task.parentId) return
    if (!childrenMap.has(task.parentId)) {
      childrenMap.set(task.parentId, [])
    }
    childrenMap.get(task.parentId).push(task)
  })

  const ordered = []
  const visit = (task, depth = 0) => {
    ordered.push({ ...task, depth })
    const children = (childrenMap.get(task.id) || []).slice().sort((left, right) => {
      const leftTime = normalizeTime(left.updatedAt || left.createdAt) || 0
      const rightTime = normalizeTime(right.updatedAt || right.createdAt) || 0
      return leftTime - rightTime
    })
    children.forEach((child) => visit(child, depth + 1))
  }

  visit(rootTask)
  return ordered
}

function buildIssueTimeline(issue) {
  const items = []
  if (issue.createdAt) {
    items.push({
      time: issue.createdAt,
      title: '问题录入',
      text: `${issue.unit || '未填写单位'} · ${issue.level || '未标注等级'}`,
      type: 'primary'
    })
  }

  getIssueSupervisions(issue.id).forEach((item) => {
    items.push({
      time: item.createdAt || item.updatedAt || issue.createdAt,
      title: '督办',
      text: `${item.supervisor || '审计人员'}：${item.note || '无'}`,
      type: 'warning'
    })
  })

  collectIssueTasks(issue).forEach((task) => {
    const stage = task.parentId ? '子任务流转' : '主任务下达'
    const parts = [
      `责任人：${task.assignee || '未分配'}`,
      `进度：${Number(task.progress || 0)}%`,
      `审核状态：${task.reviewStatus || '未审核'}`
    ]
    if (task.measure) parts.push(`措施：${task.measure}`)
    if (task.feedback) parts.push(`反馈：${task.feedback}`)
    items.push({
      time: task.updatedAt || task.createdAt || issue.createdAt,
      title: `${stage} · ${task.title}`,
      text: parts.join('｜'),
      type: task.reviewStatus === '通过' ? 'success' : task.reviewStatus === '退回' ? 'danger' : 'primary'
    })
  })

  return items
    .filter((item) => item.time)
    .sort((left, right) => normalizeTime(left.time) - normalizeTime(right.time))
}

function taskLabel(task) {
  return `${task.depth > 0 ? '子任务' : '主任务'}：${task.title}`
}

function openAssignDialog(issue) {
  activeIssue.value = issue
  taskForm.issueId = issue.id
  taskForm.issueTitle = issue.title
  taskForm.title = `${issue.title}整改任务`
  taskForm.assignee = getDefaultAssignee(issue.unit)
  taskForm.deadline = ''
  assignDialogVisible.value = true
}

async function confirmAssign() {
  if (!taskForm.issueId) return
  if (!taskForm.title.trim() || !taskForm.assignee.trim() || !taskForm.deadline) {
    ElMessage.warning('请完整填写任务信息')
    return
  }
  taskSubmitting.value = true
  try {
    await createRectificationTask(taskForm.issueId, {
      title: taskForm.title,
      assignee: taskForm.assignee,
      deadline: taskForm.deadline
    })
    await refreshSnapshot()
    assignDialogVisible.value = false
    ElMessage.success('整改任务已下达')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '任务下达失败')
  } finally {
    taskSubmitting.value = false
  }
}

function buildSplitRow() {
  const defaultUnit = splitIssue.value?.unit || unitOptions.value[0] || ''
  const options = getAssigneeOptionsByUnit(defaultUnit)
  return {
    title: `${splitIssue.value?.title || '整改问题'}整改任务`,
    unit: defaultUnit,
    assignee: options[0]?.username || getDefaultAssignee(defaultUnit),
    deadline: ''
  }
}

function openSplitDialog(issue) {
  activeIssue.value = issue
  splitIssue.value = issue
  splitRows.value = [buildSplitRow()]
  splitDialogVisible.value = true
}

function addSplitRow() {
  splitRows.value.push(buildSplitRow())
}

function removeSplitRow(index) {
  if (splitRows.value.length <= 1) {
    ElMessage.warning('至少保留一条拆分任务')
    return
  }
  splitRows.value.splice(index, 1)
}

function onSplitUnitChange(row) {
  const options = getAssigneeOptionsByUnit(row.unit)
  row.assignee = options[0]?.username || getDefaultAssignee(row.unit)
}

async function confirmSplitAssign() {
  if (!splitIssue.value) return
  for (const item of splitRows.value) {
    if (!String(item.title || '').trim() || !String(item.unit || '').trim() || !String(item.assignee || '').trim() || !item.deadline) {
      ElMessage.warning('请完整填写每一条拆分任务信息')
      return
    }
  }
  taskSubmitting.value = true
  try {
    await splitIssueTasks(
      splitIssue.value.id,
      splitRows.value.map((item) => ({
        title: item.title,
        unit: item.unit,
        assignee: item.assignee,
        deadline: item.deadline
      }))
    )
    await refreshSnapshot()
    splitDialogVisible.value = false
    ElMessage.success('跨单位拆分任务已下达')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '跨单位拆分失败')
  } finally {
    taskSubmitting.value = false
  }
}

function openSupervisionDialog(issue) {
  activeIssue.value = issue
  supervisionForm.issueId = issue.id
  supervisionForm.issueTitle = issue.title
  supervisionForm.note = ''
  supervisionDialogVisible.value = true
}

async function submitSupervision() {
  if (!supervisionForm.issueId || !supervisionForm.note.trim()) {
    ElMessage.warning('请填写督办说明')
    return
  }
  supervisionSubmitting.value = true
  try {
    const currentName = user.username || user.nickname || 'auditor_demo'
    await addIssueSupervision(supervisionForm.issueId, {
      note: supervisionForm.note,
      supervisor: currentName
    })
    await refreshSnapshot()
    supervisionDialogVisible.value = false
    ElMessage.success('督办通知已发起')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '督办发起失败')
  } finally {
    supervisionSubmitting.value = false
  }
}

function openProgressDrawer(issue) {
  activeIssue.value = issue
  taskCollapseActive.value = selectedIssueTasks.value[0]?.id || ''
  progressDrawerVisible.value = true
}

function openReviewDrawer(issue) {
  activeReviewIssue.value = issue
  reviewDrawerVisible.value = true
}

function openMaterialDrawer(task) {
  activeMaterialTask.value = task
  materialDrawerVisible.value = true
}

async function reviewTaskWithDecision(task, passed) {
  const title = passed ? '审核通过' : '退回修改'
  let comment = ''
  try {
    const result = await ElMessageBox.prompt(`请输入${title}意见`, title, {
      inputPlaceholder: '请输入审核意见',
      confirmButtonText: '提交',
      cancelButtonText: '取消'
    })
    comment = result.value
  } catch {
    return
  }
  try {
    await reviewTask(task.id, { passed, comment })
    await refreshSnapshot()
    ElMessage.success(`已完成${title}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || `${title}失败`)
  }
}

async function removeIssue(issue) {
  try {
    await ElMessageBox.confirm(`确认删除问题“${issue.title}”吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteIssue(issue.id)
    await refreshSnapshot()
    ElMessage.success('问题已删除')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '问题删除失败')
  }
}
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
}

.page-subtitle {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
}

.table-card {
  min-height: 0;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}

.table-subtext {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.dialog-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.dialog-summary {
  font-weight: 600;
  color: #303133;
}

.drawer-descriptions {
  margin-bottom: 16px;
}

.drawer-summary {
  margin-bottom: 12px;
  font-weight: 600;
  color: #303133;
}

.drawer-actions {
  margin-top: 12px;
}

.timeline-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.timeline-text {
  color: #606266;
  white-space: pre-wrap;
}

.task-collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
