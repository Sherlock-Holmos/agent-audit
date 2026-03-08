<template>
  <div>
    <FusionToolbar
      :filters="filters"
      @update:filters="handleFilterChange"
      @search="loadData"
      @reset="handleReset"
      @create="dialogVisible = true"
    />

    <FusionTable
      :data="tasks"
      :loading="loading"
      @run="handleRun"
      @delete="handleDelete"
    />

    <FusionFormDialog
      v-model="dialogVisible"
      :submitting="submitting"
      :clean-task-options="cleanTaskOptions"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listCleanTasks } from '../api/dataclean'
import { createFusionTask, deleteFusionTask, listFusionTasks, runFusionTask } from '../api/datafusion'
import FusionToolbar from '../components/datafusion/FusionToolbar.vue'
import FusionTable from '../components/datafusion/FusionTable.vue'
import FusionFormDialog from '../components/datafusion/FusionFormDialog.vue'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const tasks = ref([])
const cleanTaskOptions = ref([])

const filters = reactive({
  keyword: '',
  status: ''
})

async function loadCleanTaskOptions() {
  const { data } = await listCleanTasks({ keyword: '', sourceId: '', status: 'COMPLETED' })
  cleanTaskOptions.value = (data.data || []).map((item) => ({
    id: item.id,
    taskName: item.taskName,
    standardTable: item.standardTable
  }))
}

async function loadData() {
  loading.value = true
  try {
    const { data } = await listFusionTasks({ ...filters })
    tasks.value = data.data || []
  } finally {
    loading.value = false
  }
}

function handleReset() {
  loadData()
}

function handleFilterChange(nextFilters) {
  filters.keyword = nextFilters.keyword || ''
  filters.status = nextFilters.status || ''
}

async function handleSubmit(payload) {
  if (!payload.cleanTaskIds?.length) {
    ElMessage.warning('请至少选择一个清洗任务')
    return
  }

  submitting.value = true
  try {
    await createFusionTask(payload)
    ElMessage.success('融合任务创建成功')
    dialogVisible.value = false
    await loadCleanTaskOptions()
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

async function handleRun(id) {
  try {
    await runFusionTask(id)
    ElMessage.success('融合任务执行成功')
    await loadCleanTaskOptions()
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '执行失败')
  }
}

async function handleDelete(id) {
  try {
    await deleteFusionTask(id)
    ElMessage.success('删除成功')
    await loadCleanTaskOptions()
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '删除失败')
  }
}

onMounted(async () => {
  await loadCleanTaskOptions()
  await loadData()
})
</script>
