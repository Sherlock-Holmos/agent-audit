<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>重点问题督办台账</span>
          <div class="toolbar-right">
            <el-input v-model="keyword" placeholder="按问题标题/单位筛选" clearable style="width: 260px" />
            <el-button type="primary" @click="openIssueDialog">新增问题</el-button>
          </div>
        </div>
      </template>
      <AppDataTable :data="filteredRows" layout-storage-key="app:table-layout:audit-admin:focus-issues" :show-pagination="false" :with-card="false">
        <template #default>
        <el-table-column prop="code" label="问题编号" width="130" />
        <el-table-column prop="title" label="问题标题" min-width="220" />
        <el-table-column prop="unit" label="被审单位" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="level" label="等级" width="90" />
        <el-table-column label="督办记录" width="100">
          <template #default="scope">
            {{ supervisionCount(scope.row.id) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button link type="primary" @click="openSupervisionDialog(scope.row)">发起督办</el-button>
            <el-button link type="primary" @click="openDetailDrawer(scope.row)">详情</el-button>
            <el-button link @click="viewLogs(scope.row)">查看记录</el-button>
            <el-button link type="danger" @click="removeIssue(scope.row)">删除问题</el-button>
          </template>
        </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <el-dialog v-model="issueDialogVisible" title="新增重点问题" width="620px">
      <el-form :model="issueForm" label-width="100px">
        <el-form-item label="问题标题">
          <el-input v-model="issueForm.title" />
        </el-form-item>
        <el-form-item label="被审单位">
          <el-select v-model="issueForm.unit" filterable placeholder="请选择被审单位" style="width: 100%">
            <el-option v-for="item in unitOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题等级">
          <el-select v-model="issueForm.level" style="width: 100%">
            <el-option label="高" value="高" />
            <el-option label="重大" value="重大" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input v-model="issueForm.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitIssue">创建问题</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="supervisionDialogVisible" title="发起重点问题督办" width="560px">
      <el-form :model="supervisionForm" label-width="90px">
        <el-form-item label="问题标题">
          <el-input v-model="supervisionForm.title" disabled />
        </el-form-item>
        <el-form-item label="督办说明">
          <el-input v-model="supervisionForm.note" type="textarea" :rows="4" placeholder="请输入本次督办要求与时限" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supervisionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSupervision">确认发起</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logDrawerVisible" title="督办历史记录" size="40%">
      <el-empty v-if="!activeLogs.length" description="暂无督办记录" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="log in activeLogs"
          :key="log.id"
          :timestamp="log.createdAt"
          type="warning"
        >
          <div class="log-row">
            <div>
              <div class="log-title">{{ log.supervisor }} 发起督办</div>
              <div class="log-note">{{ log.note }}</div>
            </div>
            <el-button link type="danger" @click="removeSupervision(log.id)">删除</el-button>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>

    <el-drawer v-model="detailDrawerVisible" title="问题与任务详情" size="45%">
      <el-descriptions v-if="activeIssueDetail" :column="1" border>
        <el-descriptions-item label="问题编号">{{ activeIssueDetail.code }}</el-descriptions-item>
        <el-descriptions-item label="问题标题">{{ activeIssueDetail.title }}</el-descriptions-item>
        <el-descriptions-item label="被审单位">{{ activeIssueDetail.unit }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ activeIssueDetail.level }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ activeIssueDetail.status }}</el-descriptions-item>
        <el-descriptions-item label="问题描述">{{ activeIssueDetail.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="相关证据">
          <div v-if="(activeIssueDetail.evidenceList || []).length">
            <el-tag v-for="item in activeIssueDetail.evidenceList" :key="item" size="small" style="margin-right: 6px; margin-bottom: 6px">{{ item }}</el-tag>
          </div>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item label="制度/标准条款">{{ activeIssueDetail.regulationClause || '无' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">关联主任务</el-divider>
      <el-descriptions v-if="activeTaskDetail" :column="1" border>
        <el-descriptions-item label="任务标题">{{ activeTaskDetail.title }}</el-descriptions-item>
        <el-descriptions-item label="责任单位">{{ activeTaskDetail.unit }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ activeTaskDetail.assignee || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ activeTaskDetail.progress }}%</el-descriptions-item>
        <el-descriptions-item label="审核状态">{{ activeTaskDetail.reviewStatus || '无' }}</el-descriptions-item>
        <el-descriptions-item label="整改措施">{{ activeTaskDetail.measure || '无' }}</el-descriptions-item>
        <el-descriptions-item label="执行反馈">{{ activeTaskDetail.feedback || '无' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="当前问题尚未关联主任务" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addIssueSupervision, createIssue, deleteIssue, deleteIssueSupervision, getIssueSupervisions } from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import { getCurrentUser } from '../utils/currentUser'
import AppDataTable from '../components/shared/AppDataTable.vue'

const { snapshot, refreshSnapshot } = useRectificationSnapshot()
const keyword = ref('')
const issueDialogVisible = ref(false)
const supervisionDialogVisible = ref(false)
const logDrawerVisible = ref(false)
const detailDrawerVisible = ref(false)
const activeIssueId = ref('')
const activeLogs = ref([])
const activeIssueDetail = ref(null)
const activeTaskDetail = ref(null)
const fallbackUnitOptions = ['城建集团', '交通投资公司', '审计一处', '审计二处', '审计三处', '审计四处']

const issueForm = reactive({
  title: '',
  unit: '',
  level: '高',
  description: ''
})

const supervisionForm = reactive({
  issueId: '',
  title: '',
  note: ''
})

const filteredRows = computed(() => {
  const rows = snapshot.value.issues.filter((item) => ['重大', '高'].includes(item.level))
  const text = keyword.value.trim()
  if (!text) return rows
  return rows.filter((item) => String(item.title).includes(text) || String(item.unit).includes(text))
})

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

function supervisionCount(issueId) {
  return getIssueSupervisions(issueId).length
}

function openSupervisionDialog(issue) {
  supervisionForm.issueId = issue.id
  supervisionForm.title = issue.title
  supervisionForm.note = ''
  supervisionDialogVisible.value = true
}

function openIssueDialog() {
  issueForm.title = ''
  issueForm.unit = unitOptions.value[0] || ''
  issueForm.level = '高'
  issueForm.description = ''
  issueDialogVisible.value = true
}

async function submitIssue() {
  if (!issueForm.title.trim() || !issueForm.unit.trim()) {
    ElMessage.warning('问题标题和被审单位不能为空')
    return
  }
  try {
    await createIssue({ ...issueForm })
    await refreshSnapshot()
    issueDialogVisible.value = false
    ElMessage.success('重点问题已新增')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '重点问题新增失败')
  }
}

async function submitSupervision() {
  if (!supervisionForm.note.trim()) {
    ElMessage.warning('请填写督办说明')
    return
  }
  const user = getCurrentUser()
  try {
    await addIssueSupervision(supervisionForm.issueId, {
      note: supervisionForm.note,
      supervisor: user.nickname || user.username || '审计管理员'
    })
    supervisionDialogVisible.value = false
    await refreshSnapshot()
    ElMessage.success('督办通知已发起')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '督办发起失败')
  }
}

function viewLogs(issue) {
  activeIssueId.value = issue.id
  activeLogs.value = getIssueSupervisions(issue.id)
  logDrawerVisible.value = true
}

async function removeIssue(issue) {
  try {
    await ElMessageBox.confirm(`确认删除问题“${issue.title}”及关联任务吗？`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteIssue(issue.id)
    await refreshSnapshot()
    if (activeIssueId.value === issue.id) {
      activeLogs.value = []
      logDrawerVisible.value = false
    }
    ElMessage.success('问题已删除')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '问题删除失败')
  }
}

async function removeSupervision(supervisionId) {
  if (!activeIssueId.value) return
  try {
    await ElMessageBox.confirm('确认删除该条督办记录吗？', '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteIssueSupervision(activeIssueId.value, supervisionId)
    await refreshSnapshot()
    activeLogs.value = getIssueSupervisions(activeIssueId.value)
    ElMessage.success('督办记录已删除')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '督办记录删除失败')
  }
}

function openDetailDrawer(issue) {
  activeIssueDetail.value = issue
  activeTaskDetail.value = snapshot.value.tasks.find((item) => item.id === issue.taskId) || null
  detailDrawerVisible.value = true
}
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.log-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.log-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.log-note {
  color: #606266;
}
</style>
