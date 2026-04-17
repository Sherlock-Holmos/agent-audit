<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header><span>认领子任务与执行反馈</span></template>
      <el-table :data="taskRows" border>
        <el-table-column prop="title" label="子任务" min-width="220" />
        <el-table-column prop="assignee" label="指派账号" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="progress" label="进度" width="90" />
        <el-table-column label="操作" width="210">
          <template #default="scope">
            <el-button link type="primary" :disabled="!!scope.row.claimedBy && scope.row.claimedBy !== username" @click="claim(scope.row)">认领</el-button>
            <el-button link type="success" @click="openSubmitDialog(scope.row)">填报执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="submitDialogVisible" title="填报整改执行情况" width="560px">
      <el-form :model="submitForm" label-width="120px">
        <el-form-item label="整改措施">
          <el-input v-model="submitForm.measure" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="证明材料">
          <el-input v-model="submitForm.attachmentsText" placeholder="多个材料请使用逗号分隔" />
        </el-form-item>
        <el-form-item label="执行反馈">
          <el-input v-model="submitForm.feedback" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="完成进度">
          <el-slider v-model="submitForm.progress" :min="0" :max="100" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitExecution">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { claimTask, getRectificationSnapshot, submitTaskExecution } from '../utils/rectificationStore'
import { getCurrentUnit, getCurrentUser } from '../utils/currentUser'

const user = getCurrentUser()
const username = user.username || 'org_operator_demo'
const unitName = getCurrentUnit()

const stamp = ref(0)
const submitDialogVisible = ref(false)
const selectedTask = ref(null)

const submitForm = reactive({
  measure: '',
  attachmentsText: '',
  feedback: '',
  progress: 0
})

const snapshot = computed(() => {
  stamp.value
  return getRectificationSnapshot()
})

const taskRows = computed(() => snapshot.value.tasks.filter((item) => item.parentId && item.unit === unitName && (item.assignee === username || !item.assignee || item.assignee === 'org_operator_demo')))

function refresh() {
  stamp.value += 1
}

function claim(task) {
  claimTask(task.id, username)
  refresh()
  ElMessage.success('已认领任务')
}

function openSubmitDialog(task) {
  selectedTask.value = task
  submitForm.measure = task.measure || ''
  submitForm.feedback = task.feedback || ''
  submitForm.attachmentsText = Array.isArray(task.attachments) ? task.attachments.join(', ') : ''
  submitForm.progress = task.progress || 0
  submitDialogVisible.value = true
}

function submitExecution() {
  if (!selectedTask.value) return
  if (!submitForm.measure.trim()) {
    ElMessage.warning('请填写整改措施')
    return
  }

  const attachments = submitForm.attachmentsText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

  submitTaskExecution(selectedTask.value.id, {
    measure: submitForm.measure,
    feedback: submitForm.feedback,
    attachments,
    progress: submitForm.progress
  })

  submitDialogVisible.value = false
  refresh()
  ElMessage.success('执行情况已反馈')
}
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
