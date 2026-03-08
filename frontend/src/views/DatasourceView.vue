<template>
  <div>
    <DatasourceToolbar
      :filters="filters"
      @update:filters="handleFilterChange"
      @search="loadData"
      @reset="handleReset"
      @create="dialogVisible = true"
    />

    <DatasourceTable
      :data="sources"
      :loading="loading"
      @status-change="handleStatusChange"
      @delete="handleDelete"
    />

    <DatasourceFormDialog
      v-model="dialogVisible"
      :submitting="submitting"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DatasourceToolbar from '../components/datasource/DatasourceToolbar.vue'
import DatasourceTable from '../components/datasource/DatasourceTable.vue'
import DatasourceFormDialog from '../components/datasource/DatasourceFormDialog.vue'
import {
  createDatabaseSource,
  createFileSource,
  deleteDataSource,
  listDataSources,
  updateDataSourceStatus
} from '../api/datasource'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const sources = ref([])

const filters = reactive({
  keyword: '',
  type: '',
  status: ''
})

async function loadData() {
  loading.value = true
  try {
    const { data } = await listDataSources({ ...filters })
    sources.value = data.data || []
  } finally {
    loading.value = false
  }
}

function handleReset() {
  loadData()
}

function handleFilterChange(nextFilters) {
  filters.keyword = nextFilters.keyword || ''
  filters.type = nextFilters.type || ''
  filters.status = nextFilters.status || ''
}

async function handleSubmit({ type, payload }) {
  submitting.value = true
  try {
    if (type === 'DATABASE') {
      await createDatabaseSource(payload)
      ElMessage.success('数据库数据源创建成功')
    } else {
      await createFileSource(payload)
      ElMessage.success('本地文件数据源导入成功')
    }

    dialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleStatusChange({ id, status }) {
  try {
    await updateDataSourceStatus(id, status)
    ElMessage.success('状态更新成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '状态更新失败')
  }
}

async function handleDelete(id) {
  try {
    await deleteDataSource(id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '删除失败')
  }
}

onMounted(() => {
  loadData()
})
</script>
