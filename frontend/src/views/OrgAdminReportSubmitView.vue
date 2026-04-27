<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header><span>整改总报告审核与提交</span></template>
      <el-form :model="reportForm" label-width="120px">
        <el-form-item label="单位名称">
          <el-input v-model="reportForm.unit" disabled />
        </el-form-item>
        <el-form-item label="报告标题">
          <el-input v-model="reportForm.title" placeholder="例如：2026年第一季度整改总报告" />
        </el-form-item>
        <el-form-item label="整改总结">
          <el-input v-model="reportForm.summary" type="textarea" :rows="6" placeholder="请填写本单位整改总体进展、问题闭环情况及后续计划" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitReport">提交总报告</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header><span>已提交报告</span></template>
      <AppDataTable :data="myReports" layout-storage-key="app:table-layout:org-admin:report-list" :show-pagination="false" :with-card="false">
        <template #default>
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column prop="submitter" label="提交人" width="140" />
        <el-table-column prop="createdAt" label="提交时间" width="180" />
        </template>
      </AppDataTable>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { submitOrgReport } from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import { getCurrentUnit, getCurrentUser } from '../utils/currentUser'
import AppDataTable from '../components/shared/AppDataTable.vue'

const fallbackUnitName = getCurrentUnit()
const user = getCurrentUser()
const username = user.username || 'org_admin_demo'
const { snapshot, refreshSnapshot } = useRectificationSnapshot()

const unitName = computed(() => {
  const matchedUser = snapshot.value.users.find(
    (item) => item.username === username && item.status === 'ENABLED'
  )
  const resolved = matchedUser?.unit || matchedUser?.department || fallbackUnitName
  return String(resolved || '').trim()
})

const reportForm = reactive({
  unit: unitName.value,
  title: '',
  summary: ''
})

watch(
  () => unitName.value,
  (value) => {
    reportForm.unit = value
  },
  { immediate: true }
)

const myReports = computed(() => snapshot.value.reports.filter((item) => item.unit === unitName.value))

async function submitReport() {
  if (!reportForm.title.trim() || !reportForm.summary.trim()) {
    ElMessage.warning('请填写报告标题和整改总结')
    return
  }
  try {
    await submitOrgReport({
      unit: reportForm.unit,
      title: reportForm.title,
      summary: reportForm.summary,
      submitter: user.username || 'org_admin_demo'
    })
    await refreshSnapshot()
    reportForm.title = ''
    reportForm.summary = ''
    ElMessage.success('整改总报告已提交')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '报告提交失败')
  }
}
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
