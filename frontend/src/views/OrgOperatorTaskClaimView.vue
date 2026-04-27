<template>
  <div class="page-wrap">
    <el-card shadow="never" class="workbench-card">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">经办任务工作台</div>
            <div class="page-subtitle">统一查看待认领与已认领子任务，认领、反馈、详情分离操作</div>
          </div>
          <div class="header-actions">
            <el-select v-model="statusFilter" placeholder="按状态筛选" style="width: 170px">
              <el-option label="全部状态" value="ALL" />
              <el-option label="待认领" value="待认领" />
              <el-option label="执行中" value="执行中" />
              <el-option label="待审核" value="待审核" />
              <el-option label="已完成" value="已完成" />
            </el-select>
          </div>
        </div>
      </template>

      <AppDataTable
        :data="filteredTasks"
        :loading="loading"
        layout-storage-key="app:table-layout:org-operator:task-workbench"
        :show-pagination="false"
        :with-card="false"
        :follow-global-table-size="true"
        :table-props="tableProps"
        :empty-text="emptyText"
      >
        <template #default>
          <el-table-column prop="title" label="子任务" min-width="220" show-overflow-tooltip />
          <el-table-column prop="assignee" label="指派账号" width="140" />
          <el-table-column label="归属状态" width="110">
            <template #default="scope">
              <el-tag :type="scope.row.status === '待认领' ? 'warning' : 'success'" effect="light">
                {{ scope.row.status === '待认领' ? '待认领' : '已认领' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="任务状态" width="100" />
          <el-table-column prop="claimedBy" label="认领人" width="140" />
          <el-table-column prop="reviewStatus" label="审核状态" width="120" />
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" min-width="220">
            <template #default="scope">
              <div class="row-actions">
                <el-button v-if="canClaim(scope.row)" link type="primary" @click="claim(scope.row)">认领</el-button>
                <el-button v-if="canSubmitFeedback(scope.row)" link type="success" @click="openFeedbackDialog(scope.row)">填写反馈</el-button>
                <el-button link type="info" @click="openDetailDrawer(scope.row)">查看详情</el-button>
                <el-button link type="warning" @click="openTimelineDrawer(scope.row)">流程轨迹</el-button>
              </div>
            </template>
          </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <el-drawer v-model="detailDrawerVisible" title="子任务详情" size="46%" destroy-on-close>
      <TaskProgressDetailPanel
        v-if="activeTask"
        :task="activeTask"
        :timeline="detailTimeline"
        :show-timeline="false"
      />
    </el-drawer>

    <el-drawer v-model="timelineDrawerVisible" title="子任务流程轨迹" size="46%" destroy-on-close>
      <TaskProgressDetailPanel
        v-if="activeTask"
        :task="activeTask"
        :timeline="detailTimeline"
        :show-descriptions="false"
      />
    </el-drawer>

    <el-dialog
      v-model="feedbackDialogVisible"
      title="提交执行反馈"
      width="920px"
      destroy-on-close
      align-center
      :lock-scroll="true"
      :close-on-click-modal="false"
    >
      <template v-if="selectedTask">
        <div class="feedback-layout">
          <div class="feedback-summary">
            <TaskProgressDetailPanel
              :task="selectedTask"
              :timeline="detailTimeline"
              :show-timeline="false"
            />
          </div>
          <div class="feedback-form-wrap">
            <el-form :model="form" label-position="top" class="execution-form">
              <el-form-item label="整改措施">
                <el-input
                  v-model="form.measure"
                  type="textarea"
                  :rows="4"
                  maxlength="2000"
                  show-word-limit
                  placeholder="请填写整改措施、落实动作和整改结果"
                />
              </el-form-item>

              <el-form-item label="执行反馈">
                <el-input
                  v-model="form.feedback"
                  type="textarea"
                  :rows="4"
                  maxlength="2000"
                  show-word-limit
                  placeholder="请描述执行过程、整改进展以及仍需协调的问题"
                />
              </el-form-item>

              <el-form-item label="证明材料">
                <div class="upload-panel">
                  <div class="upload-toolbar">
                    <el-upload
                      :show-file-list="false"
                      :http-request="handleAttachmentUpload"
                      :before-upload="beforeAttachmentUpload"
                      :disabled="uploading"
                      multiple
                    >
                      <el-button type="primary" :loading="uploading">上传证明材料</el-button>
                    </el-upload>
                    <div class="upload-hint">支持格式：pdf/doc/docx/xls/xlsx/csv/txt/png/jpg/jpeg/zip，单个文件不超过 20MB</div>
                  </div>

                  <div v-if="form.attachments.length" class="attachment-grid">
                    <div
                      v-for="(item, index) in form.attachments"
                      :key="`${item.storedName || item.fileName || index}-${index}`"
                      class="attachment-row"
                    >
                      <div class="attachment-name">{{ item.fileName || item.originalName || item.storedName || `附件${index + 1}` }}</div>
                      <div class="attachment-meta">
                        <span>大小：{{ formatFileSize(item.size) }}</span>
                        <span v-if="item.uploadedAt">上传：{{ item.uploadedAt }}</span>
                      </div>
                      <el-button link type="danger" @click="removeAttachment(index)">移除</el-button>
                    </div>
                  </div>
                  <el-empty v-else description="暂无已上传的证明材料" :image-size="70" />
                </div>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </template>

      <template #footer>
        <el-button @click="feedbackDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitExecution">提交反馈</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppDataTable from '../components/shared/AppDataTable.vue'
import TaskProgressDetailPanel from '../components/rectification/TaskProgressDetailPanel.vue'
import { claimTask, submitTaskExecution, uploadTaskAttachment } from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import { getCurrentUser } from '../utils/currentUser'

const user = getCurrentUser()
const username = user.username || 'org_operator_demo'
const { snapshot, refreshSnapshot, loading } = useRectificationSnapshot()

const statusFilter = ref('ALL')
const detailDrawerVisible = ref(false)
const timelineDrawerVisible = ref(false)
const feedbackDialogVisible = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const MAIN_SCROLL_LOCK_CLASS = 'execution-dialog-main-lock'

const activeTask = ref(null)
const selectedTask = ref(null)
const tableViewportHeight = ref(520)

const form = reactive({
  measure: '',
  feedback: '',
  attachments: []
})

const tableProps = {
  rowKey: 'id',
  highlightCurrentRow: false,
  tableLayout: 'fixed',
  height: tableViewportHeight
}

const allTasks = computed(() => {
  const tasks = snapshot.value.tasks || []
  return tasks
    .filter((item) => item.parentId && (item.assignee === username || item.claimedBy === username))
    .slice()
    .sort((left, right) => String(right.updatedAt || right.updated_at || '').localeCompare(String(left.updatedAt || left.updated_at || '')))
})

const filteredTasks = computed(() => {
  if (statusFilter.value === 'ALL') {
    return allTasks.value
  }
  return allTasks.value.filter((item) => String(item.status || '') === statusFilter.value)
})

const emptyText = computed(() => {
  if (statusFilter.value === 'ALL') return '暂无子任务'
  return `暂无${statusFilter.value}任务`
})

const detailTimeline = computed(() => {
  if (!activeTask.value && !selectedTask.value) return []
  const task = activeTask.value || selectedTask.value
  const timeline = []
  if (task.createdAt) {
    timeline.push({
      label: '子任务派发',
      time: task.createdAt,
      text: `子任务已派发，责任单位：${task.unit || '无'}，责任人：${task.assignee || '未分配'}`,
      type: 'primary'
    })
  }
  if (task.updatedAt && task.updatedAt !== task.createdAt) {
    timeline.push({
      label: '最近更新',
      time: task.updatedAt,
      text: `状态：${task.status || '无'}，审核状态：${task.reviewStatus || '无'}，进度：${Number(task.progress || 0)}%`,
      type: task.reviewStatus === '待审核' ? 'warning' : 'success'
    })
  }
  if (task.measure || task.feedback) {
    timeline.push({
      label: '提交内容',
      time: task.updatedAt || task.createdAt,
      text: `整改措施：${task.measure || '无'}；执行反馈：${task.feedback || '无'}`,
      type: 'info'
    })
  }
  const issue = (snapshot.value.issues || []).find((item) => item.id === task.issueId)
  const supervisions = Array.isArray(issue?.supervisions) ? issue.supervisions : []
  supervisions.forEach((item) => {
    timeline.push({
      label: '督办',
      time: item.createdAt,
      text: `${item.supervisor || '系统'}：${item.note || '无'}`,
      type: 'warning'
    })
  })
  return timeline.sort((left, right) => new Date(left.time).getTime() - new Date(right.time).getTime())
})

watch(
  () => feedbackDialogVisible.value,
  (visible) => {
    setMainScrollLocked(visible)
    if (!visible) {
      selectedTask.value = null
      form.measure = ''
      form.feedback = ''
      form.attachments = []
    }
  }
)

watch(
  () => [feedbackDialogVisible.value, selectedTask.value],
  ([visible, task]) => {
    if (!visible || !task) return
    form.measure = task.measure || ''
    form.feedback = task.feedback || ''
    form.attachments = normalizeAttachments(task.attachments)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  setMainScrollLocked(false)
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncTableViewportHeight)
  }
})

onMounted(() => {
  syncTableViewportHeight()
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', syncTableViewportHeight)
  }
})

function syncTableViewportHeight() {
  if (typeof window === 'undefined') {
    return
  }
  tableViewportHeight.value = Math.max(window.innerHeight - 330, 340)
}

function canClaim(task) {
  return task?.parentId && task.assignee === username && task.status === '待认领'
}

function canSubmitFeedback(task) {
  return task?.parentId && (task.claimedBy === username || task.assignee === username) && task.status !== '待认领'
}

function openDetailDrawer(task) {
  activeTask.value = task
  detailDrawerVisible.value = true
}

function openTimelineDrawer(task) {
  activeTask.value = task
  timelineDrawerVisible.value = true
}

function openFeedbackDialog(task) {
  selectedTask.value = task
  feedbackDialogVisible.value = true
}

async function claim(task) {
  try {
    await claimTask(task.id)
    await refreshSnapshot()
    ElMessage.success('任务认领成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '任务认领失败')
  }
}

function normalizeAttachments(value) {
  if (!value) {
    return []
  }
  const list = Array.isArray(value) ? value : []
  return list
    .map((item) => {
      if (typeof item === 'string') {
        return {
          fileName: item,
          storedName: '',
          filePath: '',
          size: 0,
          uploadedAt: ''
        }
      }
      if (item && typeof item === 'object') {
        return {
          fileName: item.fileName || item.originalName || item.name || item.storedName || '',
          storedName: item.storedName || '',
          filePath: item.filePath || '',
          size: item.size || 0,
          uploadedAt: item.uploadedAt || ''
        }
      }
      return null
    })
    .filter(Boolean)
}

function beforeAttachmentUpload(file) {
  const maxSize = 20 * 1024 * 1024
  const allowedExts = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'csv', 'txt', 'png', 'jpg', 'jpeg', 'zip']
  const fileName = file.name || ''
  const suffix = fileName.includes('.') ? fileName.split('.').pop().toLowerCase() : ''

  if (!allowedExts.includes(suffix)) {
    ElMessage.warning('仅支持 pdf/doc/docx/xls/xlsx/csv/txt/png/jpg/jpeg/zip 文件')
    return false
  }
  if (file.size > maxSize) {
    ElMessage.warning('单个文件不能超过 20MB')
    return false
  }
  return true
}

async function handleAttachmentUpload(options) {
  if (!selectedTask.value) {
    options?.onError?.(new Error('请先选择任务'))
    return
  }
  uploading.value = true
  try {
    const uploaded = await uploadTaskAttachment(selectedTask.value.id, options.file)
    form.attachments.push(uploaded)
    options?.onSuccess?.(uploaded)
    ElMessage.success('证明材料上传成功')
  } catch (error) {
    options?.onError?.(error)
    ElMessage.error(error?.response?.data?.message || error?.message || '证明材料上传失败')
  } finally {
    uploading.value = false
  }
}

function removeAttachment(index) {
  form.attachments.splice(index, 1)
}

function formatFileSize(size) {
  const value = Number(size || 0)
  if (!Number.isFinite(value) || value <= 0) return '0 B'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

function setMainScrollLocked(locked) {
  if (typeof document === 'undefined') {
    return
  }
  document.body.classList.toggle(MAIN_SCROLL_LOCK_CLASS, Boolean(locked))
}

async function submitExecution() {
  if (!selectedTask.value) {
    ElMessage.warning('请先选择任务')
    return
  }
  if (!form.measure.trim()) {
    ElMessage.warning('请先填写整改措施')
    return
  }
  if (!form.attachments.length) {
    ElMessage.warning('请先上传证明材料')
    return
  }

  submitting.value = true
  try {
    await submitTaskExecution(selectedTask.value.id, {
      measure: form.measure,
      feedback: form.feedback,
      attachments: form.attachments,
      progress: 100
    })
    await refreshSnapshot()
    feedbackDialogVisible.value = false
    ElMessage.success('执行反馈已提交')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '执行反馈提交失败')
  } finally {
    submitting.value = false
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
  min-height: calc(100vh - 120px);
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
}

.page-subtitle {
  margin-top: 4px;
  color: #7a8596;
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.execution-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.feedback-layout {
  display: grid;
  grid-template-columns: minmax(250px, 36%) 1fr;
  gap: 16px;
  align-items: start;
}

.feedback-summary,
.feedback-form-wrap {
  min-width: 0;
}

.upload-panel {
  width: 100%;
  padding: 14px;
  border: 1px solid #e6ebf2;
  border-radius: 14px;
  background: linear-gradient(180deg, #fbfcfe 0%, #f7f9fc 100%);
}

.upload-toolbar {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.upload-hint {
  color: #7a8596;
  font-size: 12px;
}

.attachment-grid {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.attachment-row {
  border: 1px solid #e3e8f0;
  border-radius: 10px;
  background: #fff;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.attachment-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #1f2d3d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attachment-meta {
  display: flex;
  gap: 10px;
  color: #7a8596;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .feedback-layout {
    grid-template-columns: 1fr;
  }
}

:global(body.execution-dialog-main-lock .main-scroll) {
  overflow-y: hidden !important;
}
</style>
