<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header><span>接收整改任务与内部派发</span></template>
      <el-table :data="mainTasks" border>
        <el-table-column prop="title" label="主任务" min-width="220" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="progress" label="进度" width="90" />
        <el-table-column prop="deadline" label="截止日期" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="openDispatchDialog(scope.row)">派发子任务</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span>本单位整改进度督办</span></template>
      <el-table :data="allUnitTasks" border>
        <el-table-column prop="title" label="任务" min-width="220" />
        <el-table-column prop="assignee" label="责任人" width="130" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="progress" label="进度" width="90" />
        <el-table-column prop="reviewStatus" label="审核状态" width="120" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span>审核并提交总报告</span></template>
      <el-form :model="reportForm" label-width="120px">
        <el-form-item label="报告标题">
          <el-input v-model="reportForm.title" placeholder="请输入报告标题" />
        </el-form-item>
        <el-form-item label="整改总结">
          <el-input v-model="reportForm.summary" type="textarea" :rows="4" placeholder="请输入整改总结" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitReport">提交总报告</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="dispatchDialogVisible" title="派发子任务" width="520px">
      <el-form :model="dispatchForm" label-width="100px">
        <el-form-item label="任务标题">
          <el-input v-model="dispatchForm.title" />
        </el-form-item>
        <el-form-item label="经办人账号">
          <el-input v-model="dispatchForm.assignee" placeholder="例如 org_operator_demo" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="dispatchForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDispatch">确认派发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  dispatchSubTask,
  getRectificationSnapshot,
  submitOrgReport
} from '../utils/rectificationStore'
import { getCurrentUnit, getCurrentUser } from '../utils/currentUser'

const dispatchDialogVisible = ref(false)
const selectedTask = ref(null)
const stamp = ref(0)

const dispatchForm = reactive({
  title: '',
  assignee: 'org_operator_demo',
  deadline: ''
})

const reportForm = reactive({
  title: '',
  summary: ''
})

const snapshot = computed(() => {
  stamp.value
  return getRectificationSnapshot()
})

const unitName = computed(() => getCurrentUnit())

const allUnitTasks = computed(() => snapshot.value.tasks.filter((item) => item.unit === unitName.value))
const mainTasks = computed(() => allUnitTasks.value.filter((item) => !item.parentId))

function refresh() {
  stamp.value += 1
}

function openDispatchDialog(task) {
  selectedTask.value = task
  dispatchForm.title = `${task.title}-子任务`
  dispatchForm.assignee = 'org_operator_demo'
  dispatchForm.deadline = task.deadline || ''
  dispatchDialogVisible.value = true
}

function confirmDispatch() {
  if (!selectedTask.value) return
  if (!dispatchForm.title.trim() || !dispatchForm.assignee.trim()) {
    ElMessage.warning('请填写子任务标题和经办人')
    return
  }
  const user = getCurrentUser()
  dispatchSubTask(selectedTask.value.id, { ...dispatchForm }, user.username || 'org_admin')
  dispatchDialogVisible.value = false
  refresh()
  ElMessage.success('子任务已派发')
}

function submitReport() {
  if (!reportForm.title.trim() || !reportForm.summary.trim()) {
    ElMessage.warning('请填写报告标题和整改总结')
    return
  }
  const user = getCurrentUser()
  submitOrgReport({
    unit: unitName.value,
    title: reportForm.title,
    summary: reportForm.summary,
    submitter: user.username || 'org_admin'
  })
  reportForm.title = ''
  reportForm.summary = ''
  ElMessage.success('总报告已提交')
}
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
