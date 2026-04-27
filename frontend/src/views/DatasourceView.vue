<template>
  <div class="datasource-view">
    <div class="toolbar-host">
      <DatasourceToolbar
        :filters="filters"
        @update:filters="handleFilterChange"
        @search="loadData"
        @reset="handleReset"
        @create="openCreateDialog"
      />
    </div>

    <div class="table-host">
      <DatasourceTable
        :data="sources"
        :loading="loading"
        :table-size="tableLayout.size"
        layout-storage-key="app:table-layout:datasource:view"
        @status-change="handleStatusChange"
        @edit="handleEdit"
        @delete="handleDelete"
      />
    </div>

    <DatasourceFormDialog
      v-model="dialogVisible"
      :submitting="submitting"
      :mode="dialogMode"
      :initial-data="editingSource"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DatasourceToolbar from '../components/datasource/DatasourceToolbar.vue'
import DatasourceTable from '../components/datasource/DatasourceTable.vue'
import DatasourceFormDialog from '../components/datasource/DatasourceFormDialog.vue'
import {
  createDatabaseSource,
  createFileSource,
  deleteDataSource,
  listDataSources,
  updateDataSource,
  updateDataSourceStatus
} from '../api/datasource'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const sources = ref([])
const dialogMode = ref('create')
const editingSource = ref(null)
const tableLayout = reactive({
  size: 'default'
})
const TABLE_LAYOUT_KEY = 'app:table-layout:global'

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

function openCreateDialog() {
  dialogMode.value = 'create'
  editingSource.value = null
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogMode.value = 'edit'
  editingSource.value = { ...row }
  dialogVisible.value = true
}

async function handleSubmit({ type, payload }) {
  submitting.value = true
  try {
    if (dialogMode.value === 'edit' && editingSource.value?.id) {
      const updatePayload = type === 'DATABASE'
        ? payload
        : { name: payload.name, remark: payload.remark }
      await updateDataSource(editingSource.value.id, updatePayload)
      ElMessage.success('数据源更新成功')
    } else {
      if (type === 'DATABASE') {
        await createDatabaseSource(payload)
        ElMessage.success('数据库数据源创建成功')
      } else {
        await createFileSource(payload)
        ElMessage.success('本地文件数据源导入成功')
      }
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

function loadTableLayout() {
  try {
    const cached = localStorage.getItem(TABLE_LAYOUT_KEY)
    if (!cached) return
    const parsed = JSON.parse(cached)
    if (typeof parsed?.size === 'string') {
      tableLayout.size = parsed.size
    }
  } catch {
    // ignore invalid cache
  }
}

function handleTableLayoutChanged(event) {
  const next = event?.detail || {}
  if (typeof next?.size === 'string') {
    tableLayout.size = next.size
  }
}

onMounted(() => {
  loadTableLayout()
  window.addEventListener('table-layout-config-changed', handleTableLayoutChanged)
  loadData()
})

onBeforeUnmount(() => {
  window.removeEventListener('table-layout-config-changed', handleTableLayoutChanged)
})
</script>

<style scoped>
.datasource-view {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.toolbar-host {
  flex-shrink: 0;
}

.table-host {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
