<template>
  <div class="page-wrap">
    <el-card shadow="never" class="workbench-card">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">任务协同中心</div>
            <div class="page-subtitle">签收、派发、督办与进度审核收敛到同一张表中处理</div>
          </div>
          <div class="header-actions">
            <el-select v-model="statusFilter" placeholder="按状态筛选" style="width: 160px">
              <el-option label="全部状态" value="ALL" />
              <el-option label="待接收" value="待接收" />
              <el-option label="执行中" value="执行中" />
              <el-option label="待审核" value="待审核" />
              <el-option label="已完成" value="已完成" />
            </el-select>
          </div>
        </div>
      </template>

      <AppDataTable
        :data="filteredTasks"
        layout-storage-key="app:table-layout:org-admin:task-collaboration"
        :show-pagination="false"
        :with-card="false"
        :empty-text="'暂无任务，请先由审计人员下达整改任务。'"
      >
        <template #default>
          <el-table-column label="层级" width="90" align="center">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.parentId ? 'info' : 'success'">{{ scope.row.parentId ? '子任务' : '主任务' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="任务标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="unit" label="责任单位" width="150" />
          <el-table-column prop="assignee" label="责任人" width="140" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="进度" width="170">
            <template #default="scope">
              <el-progress :percentage="Number(scope.row.progress || 0)" :stroke-width="14" />
            </template>
          </el-table-column>
          <el-table-column prop="reviewStatus" label="审核状态" width="120" />
          <el-table-column prop="deadline" label="截止日期" width="120" />
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" min-width="420">
            <template #default="scope">
              <div class="row-actions">
                <el-button link type="primary" @click="openTaskDrawer(scope.row)">进度详情</el-button>
                <el-button v-if="!scope.row.parentId && scope.row.status === '待接收'" link type="primary" @click="accept(scope.row)">签收任务</el-button>
                <el-button v-if="!scope.row.parentId" link type="success" @click="openDispatchDialog(scope.row)">派发子任务</el-button>
                <el-button
                  v-if="scope.row.parentId && scope.row.createdBy === username && scope.row.status !== '已完成'"
                  link
                  type="danger"
                  @click="confirmDeleteSubTask(scope.row)"
                >
                  删除子任务
                </el-button>
                <el-button v-if="!scope.row.parentId" link type="warning" @click="openSummaryDialog(scope.row)">提交汇总</el-button>
                <el-button v-if="scope.row.parentId" link type="warning" @click="openSupervisionDialog(scope.row)">督办</el-button>
                <el-button v-if="scope.row.parentId && scope.row.reviewStatus === '待审核'" link type="success" @click="openOperatorReviewDialog(scope.row)">审核提交</el-button>
              </div>
            </template>
          </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <el-dialog v-model="dispatchDialogVisible" title="派发子任务" width="520px" destroy-on-close>
      <el-form :model="dispatchForm" label-width="95px">
        <el-form-item label="任务标题">
          <el-input v-model="dispatchForm.title" />
        </el-form-item>
        <el-form-item label="经办人账号">
          <el-select v-model="dispatchForm.assignee" filterable placeholder="请选择经办人" style="width: 100%">
            <el-option
              v-for="user in operatorOptions"
              :key="user.username"
              :label="`${user.nickname || user.username} (${user.username})`"
              :value="user.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="dispatchForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="confirmDispatch">确认派发</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="summaryDialogVisible" title="提交单位汇总整改" width="620px" destroy-on-close>
      <el-form :model="summaryForm" label-width="110px">
        <el-form-item label="汇总措施">
          <el-input v-model="summaryForm.measure" type="textarea" :rows="4" placeholder="请填写单位层面的整改措施汇总" />
        </el-form-item>
        <el-form-item label="证明材料">
          <el-input v-model="summaryForm.attachmentsText" placeholder="多个材料请用逗号分隔" />
        </el-form-item>
        <el-form-item label="执行反馈">
          <el-input v-model="summaryForm.feedback" type="textarea" :rows="3" placeholder="请填写整改结果与闭环说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="summaryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitSummary">提交审核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="operatorReviewDialogVisible" title="审核经办人提交内容" width="560px" destroy-on-close>
      <el-form :model="operatorReviewForm" label-width="90px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="operatorReviewForm.passed">
            <el-radio :label="true">通过</el-radio>
            <el-radio :label="false">退回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="operatorReviewForm.comment" type="textarea" :rows="3" placeholder="请填写审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="operatorReviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitOperatorReview">提交审核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="supervisionDialogVisible" title="发起任务督办" width="560px" destroy-on-close>
      <el-form :model="supervisionForm" label-width="90px">
        <el-form-item label="任务标题">
          <el-input v-model="supervisionForm.taskTitle" disabled />
        </el-form-item>
        <el-form-item label="督办说明">
          <el-input v-model="supervisionForm.note" type="textarea" :rows="4" placeholder="请输入本次督办要求与时限" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supervisionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitSupervision">确认发起</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="taskDrawerVisible" title="整改进度详情" size="48%" destroy-on-close>
      <TaskProgressDetailPanel v-if="activeTask" :task="activeTask" :timeline="taskTimeline" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { addIssueSupervision, acceptTask, deleteTask, dispatchSubTask, getIssueSupervisions, reviewTask, submitTaskExecution } from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import { getCurrentUnit, getCurrentUser } from '../utils/currentUser'
import AppDataTable from '../components/shared/AppDataTable.vue'
import TaskProgressDetailPanel from '../components/rectification/TaskProgressDetailPanel.vue'

const { snapshot, refreshSnapshot } = useRectificationSnapshot()
const user = getCurrentUser()
const username = user.username || 'org_admin_demo'
const fallbackUnitName = getCurrentUnit()

const statusFilter = ref('ALL')
const actionLoading = ref(false)
const dispatchDialogVisible = ref(false)
const summaryDialogVisible = ref(false)
const operatorReviewDialogVisible = ref(false)
const supervisionDialogVisible = ref(false)
const taskDrawerVisible = ref(false)
const selectedTask = ref(null)
const summaryTask = ref(null)
const reviewingTask = ref(null)
const supervisionTask = ref(null)
const activeTask = ref(null)

const dispatchForm = reactive({
  title: '',
  assignee: 'org_operator_demo',
  deadline: ''
})

const summaryForm = reactive({
  measure: '',
  attachmentsText: '',
  feedback: ''
})

const operatorReviewForm = reactive({
  passed: true,
  comment: ''
})

const supervisionForm = reactive({
  issueId: null,
  taskId: null,
  taskTitle: '',
  note: ''
})

const unitName = computed(() => {
  const matchedUser = snapshot.value.users.find((item) => item.username === username && item.status === 'ENABLED')
  const resolved = matchedUser?.unit || matchedUser?.department || fallbackUnitName
  return String(resolved || '').trim()
})

const unitTasks = computed(() =>
  snapshot.value.tasks.filter((item) => item.unit === unitName.value || item.assignee === username)
)

const filteredTasks = computed(() => {
  if (statusFilter.value === 'ALL') return unitTasks.value
  return unitTasks.value.filter((item) => item.status === statusFilter.value)
})

const operatorOptions = computed(() => {
  const unit = selectedTask.value?.unit || ''
  return snapshot.value.users.filter(
    (item) => item.role === 'ORG_OPERATOR' && item.status === 'ENABLED' && item.unit === unit
  )
})

const taskTimeline = computed(() => {
  if (!activeTask.value) return []
  return buildTaskTimeline(activeTask.value)
})

function buildTaskTimeline(current) {
  const items = []
  if (current.createdAt) {
    items.push({
      label: current.parentId ? '子任务派发' : '任务创建',
      time: current.createdAt,
      text: current.parentId
        ? `子任务已派发，责任单位：${current.unit || '无'}，责任人：${current.assignee || '未分配'}`
        : `主任务已生成，责任单位：${current.unit || '无'}`,
      type: 'primary'
    })
  }
  if (!current.parentId) {
    const childTasks = (snapshot.value.tasks || []).filter((item) => item.parentId === current.id)
    if (childTasks.length) {
      const completedCount = childTasks.filter((item) => item.status === '已完成').length
      const activeCount = childTasks.filter((item) => item.status === '执行中' || item.status === '待审核').length
      const pendingCount = childTasks.filter((item) => item.status === '待认领' || item.status === '待接收').length
      const averageProgress = Math.round(childTasks.reduce((sum, item) => sum + Number(item.progress || 0), 0) / childTasks.length)
      items.push({
        label: '包含子任务进度',
        time: current.updatedAt || current.createdAt,
        text: `共 ${childTasks.length} 个子任务，已完成 ${completedCount} 个，执行中 ${activeCount} 个，待认领 ${pendingCount} 个，平均进度 ${averageProgress}%`,
        type: 'info'
      })
    }
  }
  if (current.updatedAt && current.updatedAt !== current.createdAt) {
    items.push({
      label: '最近更新',
      time: current.updatedAt,
      text: `状态：${current.status || '无'}，审核状态：${current.reviewStatus || '无'}，进度：${Number(current.progress || 0)}%`,
      type: current.reviewStatus === '待审核' ? 'warning' : 'success'
    })
  }
  if (current.measure || current.feedback) {
    items.push({
      label: '提交内容',
      time: current.updatedAt || current.createdAt,
      text: `整改措施：${current.measure || '无'}；执行反馈：${current.feedback || '无'}`,
      type: 'info'
    })
  }
  getIssueSupervisions(current.issueId).forEach((item) => {
    items.push({
      label: '督办',
      time: item.createdAt,
      text: `${item.supervisor || '系统'}：${item.note || '无'}`,
      type: 'warning'
    })
  })
  return items.sort((left, right) => new Date(left.time).getTime() - new Date(right.time).getTime())
}

async function confirmDeleteSubTask(task) {
  try {
    await ElMessageBox.confirm(
      `确定删除子任务「${task.title}」吗？删除后无法恢复。`,
      '删除子任务',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  actionLoading.value = true
  try {
    await deleteTask(task.id)
    await refreshSnapshot()
    ElMessage.success('子任务已删除')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '删除失败')
  } finally {
    actionLoading.value = false
  }
}

async function accept(task) {
  try {
    await acceptTask(task.id)
    await refreshSnapshot()
    ElMessage.success('任务已签收')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '签收失败')
  }
}

function openDispatchDialog(task) {
  selectedTask.value = task
  dispatchForm.title = `${task.title}-子任务`
  dispatchForm.assignee = operatorOptions.value[0]?.username || ''
  dispatchForm.deadline = task.deadline || ''
  dispatchDialogVisible.value = true
}

async function confirmDispatch() {
  if (!selectedTask.value) return
  if (!dispatchForm.title.trim() || !dispatchForm.assignee.trim()) {
    ElMessage.warning('请填写子任务标题和经办人')
    return
  }
  actionLoading.value = true
  try {
    await dispatchSubTask(selectedTask.value.id, { ...dispatchForm })
    await refreshSnapshot()
    dispatchDialogVisible.value = false
    ElMessage.success('子任务派发成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '子任务派发失败')
  } finally {
    actionLoading.value = false
  }
}

function openSummaryDialog(task) {
  summaryTask.value = task
  summaryForm.measure = task.measure || ''
  summaryForm.attachmentsText = formatAttachmentNames(task.attachments).join(', ')
  summaryForm.feedback = task.feedback || ''
  summaryDialogVisible.value = true
}

function openTaskDrawer(task) {
  activeTask.value = task
  taskDrawerVisible.value = true
}

function openSupervisionDialog(task) {
  if (!task?.parentId) {
    ElMessage.warning('主任务不能直接督办，请对已派发的子任务进行督办')
    return
  }
  supervisionTask.value = task
  supervisionForm.issueId = task.issueId
  supervisionForm.taskId = task.id
  supervisionForm.taskTitle = task.title
  supervisionForm.note = ''
  supervisionDialogVisible.value = true
}

function openOperatorReviewDialog(task) {
  reviewingTask.value = task
  operatorReviewForm.passed = true
  operatorReviewForm.comment = ''
  operatorReviewDialogVisible.value = true
}

function formatAttachmentNames(value) {
  const list = Array.isArray(value) ? value : []
  return list
    .map((item) => {
      if (typeof item === 'string') return item
      if (!item || typeof item !== 'object') return ''
      return item.fileName || item.originalName || item.name || item.storedName || ''
    })
    .map((item) => String(item || '').trim())
    .filter(Boolean)
}

async function submitSummary() {
  if (!summaryTask.value) return
  if (!summaryForm.measure.trim() || !summaryForm.feedback.trim()) {
    ElMessage.warning('请填写汇总措施与执行反馈')
    return
  }

  const attachments = summaryForm.attachmentsText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

  actionLoading.value = true
  try {
    await submitTaskExecution(summaryTask.value.id, {
      measure: summaryForm.measure,
      attachments,
      feedback: summaryForm.feedback,
      progress: 100
    })
    await refreshSnapshot()
    summaryDialogVisible.value = false
    ElMessage.success('单位汇总已提交，等待审计审核')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '汇总提交失败')
  } finally {
    actionLoading.value = false
  }
}

async function submitOperatorReview() {
  if (!reviewingTask.value) return
  actionLoading.value = true
  try {
    await reviewTask(reviewingTask.value.id, {
      passed: operatorReviewForm.passed,
      comment: operatorReviewForm.comment
    })
    await refreshSnapshot()
    operatorReviewDialogVisible.value = false
    ElMessage.success('经办人提交内容已审核')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '审核失败')
  } finally {
    actionLoading.value = false
  }
}

async function submitSupervision() {
  if (!supervisionTask.value || !supervisionForm.issueId || !supervisionForm.note.trim()) {
    ElMessage.warning('请填写督办说明')
    return
  }
  actionLoading.value = true
  try {
    const currentName = user.username || user.nickname || username
    await addIssueSupervision(supervisionForm.issueId, {
      taskId: supervisionForm.taskId,
      note: supervisionForm.note,
      supervisor: currentName
    })
    await refreshSnapshot()
    supervisionDialogVisible.value = false
    ElMessage.success('督办通知已发起')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '督办发起失败')
  } finally {
    actionLoading.value = false
  }
}

</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workbench-card {
  min-height: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
}

.page-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}

</style>
