<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header><span>录入审计问题</span></template>
      <el-form :model="issueForm" inline>
        <el-form-item label="问题标题">
          <el-input v-model="issueForm.title" placeholder="请输入问题标题" style="width: 280px" />
        </el-form-item>
        <el-form-item label="严重等级">
          <el-select v-model="issueForm.level" style="width: 120px">
            <el-option label="低" value="低" />
            <el-option label="中" value="中" />
            <el-option label="高" value="高" />
            <el-option label="重大" value="重大" />
          </el-select>
        </el-form-item>
        <el-form-item label="被审单位">
          <el-input v-model="issueForm.unit" placeholder="请输入单位名称" style="width: 220px" />
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input v-model="issueForm.description" placeholder="问题描述" style="width: 320px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onCreateIssue">保存问题</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header><span>整改任务下达与进度跟踪</span></template>
      <el-table :data="issueRows" border>
        <el-table-column prop="code" label="问题编号" width="130" />
        <el-table-column prop="title" label="问题标题" min-width="220" />
        <el-table-column prop="unit" label="被审单位" width="130" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="140">
          <template #default="scope">
            <el-button link type="primary" :disabled="!!scope.row.taskId" @click="openTaskDialog(scope.row)">下达任务</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span>在线审核整改结果</span></template>
      <el-table :data="taskRows" border>
        <el-table-column prop="title" label="任务标题" min-width="220" />
        <el-table-column prop="unit" label="被审单位" width="130" />
        <el-table-column prop="progress" label="进度" width="100" />
        <el-table-column prop="reviewStatus" label="审核状态" width="120" />
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button link type="success" @click="onReview(scope.row, true)">审核通过</el-button>
            <el-button link type="danger" @click="onReview(scope.row, false)">退回修改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="taskDialogVisible" title="下达整改任务" width="520px">
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="任务标题">
          <el-input v-model="taskForm.title" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="taskForm.assignee" placeholder="单位管理员账号" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="taskForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onCreateTask">确认下达</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createIssue,
  createRectificationTask,
  getRectificationSnapshot,
  reviewTask
} from '../utils/rectificationStore'
import { getCurrentUser } from '../utils/currentUser'

const issueForm = reactive({
  title: '',
  level: '中',
  unit: '',
  description: ''
})

const taskForm = reactive({
  title: '',
  assignee: '',
  deadline: ''
})

const taskDialogVisible = ref(false)
const currentIssue = ref(null)
const stamp = ref(0)

const snapshot = computed(() => {
  stamp.value
  return getRectificationSnapshot()
})

const issueRows = computed(() => snapshot.value.issues)
const taskRows = computed(() => snapshot.value.tasks.filter((item) => !item.parentId))

function refresh() {
  stamp.value += 1
}

function onCreateIssue() {
  if (!issueForm.title.trim() || !issueForm.unit.trim()) {
    ElMessage.warning('请填写问题标题和被审单位')
    return
  }
  const user = getCurrentUser()
  createIssue({ ...issueForm }, user.username || 'auditor')
  issueForm.title = ''
  issueForm.level = '中'
  issueForm.unit = ''
  issueForm.description = ''
  refresh()
  ElMessage.success('审计问题已录入')
}

function openTaskDialog(issue) {
  currentIssue.value = issue
  taskForm.title = `${issue.title}整改任务`
  taskForm.assignee = 'org_admin_demo'
  taskForm.deadline = ''
  taskDialogVisible.value = true
}

function onCreateTask() {
  if (!currentIssue.value) return
  if (!taskForm.title.trim() || !taskForm.assignee.trim() || !taskForm.deadline) {
    ElMessage.warning('请完整填写任务信息')
    return
  }
  const user = getCurrentUser()
  createRectificationTask(currentIssue.value.id, { ...taskForm }, user.username || 'auditor')
  taskDialogVisible.value = false
  refresh()
  ElMessage.success('整改任务已下达')
}

async function onReview(task, passed) {
  const actionText = passed ? '通过' : '退回'
  let comment = ''
  try {
    const result = await ElMessageBox.prompt(`请输入${actionText}意见`, '审核整改结果', {
      inputPlaceholder: '请输入审核意见',
      confirmButtonText: '提交',
      cancelButtonText: '取消'
    })
    comment = result.value
  } catch {
    return
  }

  reviewTask(task.id, { passed, comment })
  refresh()
  ElMessage.success(`已${actionText}整改任务`)
}
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
