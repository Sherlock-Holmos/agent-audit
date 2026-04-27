<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header><span>执行反馈中心</span></template>

      <AppDataTable
        :data="mySubTasks"
        :loading="loading"
        layout-storage-key="app:table-layout:org-operator:execution-center"
        :show-pagination="false"
        :with-card="false"
        :table-props="tableProps"
      >
        <template #default>
          <el-table-column prop="title" label="任务标题" min-width="220" show-overflow-tooltip />
          <el-table-column label="上级任务" min-width="220" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ resolveParentTitle(scope.row.parentId) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="statusTagType(scope.row.status)" effect="light">{{ scope.row.status || '未知' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核状态" width="120">
            <template #default="scope">
              <el-tag :type="reviewTagType(scope.row.reviewStatus)" effect="light">{{ scope.row.reviewStatus || '待提交' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deadline" label="截止日期" width="120" />
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openDetailDialog(scope.row)">查看详情</el-button>
            </template>
          </el-table-column>
        </template>
      </AppDataTable>

      <el-dialog
        v-model="dialogVisible"
        title="执行反馈详情"
        width="920px"
        destroy-on-close
        align-center
        :lock-scroll="true"
        :close-on-click-modal="false"
      >
        <template v-if="selectedTask">
          <TaskProgressDetailPanel :task="selectedTask" :timeline="detailTimeline" />

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
                <div class="upload-actions">
                  <el-upload
                    :show-file-list="false"
                    :http-request="handleAttachmentUpload"
                    :before-upload="beforeAttachmentUpload"
                    :disabled="uploading"
                    multiple
                  >
                    <el-button type="primary" :loading="uploading">上传证明材料</el-button>
                  </el-upload>
                  <div class="upload-hint">支持 pdf/doc/docx/xls/xlsx/csv/txt/png/jpg/jpeg/zip，单个文件不超过 20MB。</div>
                </div>

                <div v-if="form.attachments.length" class="attachment-list">
                  <el-tag
                    v-for="(item, index) in form.attachments"
                    :key="`${item.storedName || item.fileName || index}-${index}`"
                    closable
                    effect="plain"
                    @close="removeAttachment(index)"
                  >
                    {{ item.fileName || item.originalName || item.storedName || `附件${index + 1}` }}
                  </el-tag>
                </div>
                <el-empty v-else description="暂无已上传的证明材料" />
              </div>
            </el-form-item>
          </el-form>
        </template>

        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitExecution">提交反馈</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppDataTable from '../components/shared/AppDataTable.vue'
import { submitTaskExecution, uploadTaskAttachment } from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import { getCurrentUser } from '../utils/currentUser'
import TaskProgressDetailPanel from '../components/rectification/TaskProgressDetailPanel.vue'

const user = getCurrentUser()
const username = user.username || 'org_operator_demo'
const { snapshot, refreshSnapshot, loading } = useRectificationSnapshot()

const dialogVisible = ref(false)
const selectedTaskId = ref(null)
const uploading = ref(false)
const submitting = ref(false)
const MAIN_SCROLL_LOCK_CLASS = 'execution-dialog-main-lock'

const form = reactive({
  measure: '',
  feedback: '',
  attachments: []
})

const tableProps = {
  rowKey: 'id',
  highlightCurrentRow: false,
  tableLayout: 'fixed'
}

const mySubTasks = computed(() => {
  const tasks = snapshot.value.tasks || []
  return tasks
    .filter((item) => item.parentId && (item.claimedBy === username || item.assignee === username))
    .slice()
    .sort((left, right) => String(right.updatedAt || right.updated_at || '').localeCompare(String(left.updatedAt || left.updated_at || '')))
})

const selectedTask = computed(() => mySubTasks.value.find((item) => item.id === selectedTaskId.value) || null)
const detailTimeline = computed(() => {
  if (!selectedTask.value) return []
  const task = selectedTask.value
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
  () => dialogVisible.value,
  (visible) => {
    setMainScrollLocked(visible)
    if (!visible) {
      selectedTaskId.value = null
      form.measure = ''
      form.feedback = ''
      form.attachments = []
    }
  }
)

watch(
  () => [dialogVisible.value, selectedTask.value],
  ([visible, task]) => {
    if (!visible || !task) {
      return
    }
    form.measure = task.measure || ''
    form.feedback = task.feedback || ''
    form.attachments = normalizeAttachments(task.attachments)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  setMainScrollLocked(false)
})

function openDetailDialog(task) {
  selectedTaskId.value = task.id
  dialogVisible.value = true
}

function resolveParentTitle(parentId) {
  return parentId ? '已派发主任务' : '无'
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

function statusTagType(status) {
  if (status === '待审核') return 'warning'
  if (status === '已完成') return 'success'
  if (status === '执行中') return 'primary'
  return 'info'
}

function reviewTagType(status) {
  if (status === '通过') return 'success'
  if (status === '退回') return 'danger'
  if (status === '待审核') return 'warning'
  return 'info'
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
      attachments: form.attachments
    })
    await refreshSnapshot()
    dialogVisible.value = false
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

.execution-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.upload-panel {
  width: 100%;
  padding: 16px;
  border: 1px solid #e6ebf2;
  border-radius: 14px;
  background: linear-gradient(180deg, #fbfcfe 0%, #f7f9fc 100%);
}

.upload-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.upload-hint {
  color: #7a8596;
  font-size: 12px;
}

.attachment-list {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

:global(body.execution-dialog-main-lock .main-scroll) {
  overflow-y: hidden !important;
}
</style>
