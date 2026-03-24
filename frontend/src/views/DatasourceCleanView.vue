<template>
  <div class="clean-view">
    <div class="toolbar-host">
      <CleanToolbar
        :filters="filters"
        :source-options="sourceOptions"
        @update:filters="handleFilterChange"
        @search="loadData"
        @reset="handleReset"
        @manage-rules="openRuleManagement"
        @create="openCreateDialog"
      />
    </div>

    <div class="table-host">
      <CleanTable
        :data="tasks"
        :loading="loading"
        :table-size="tableLayout.size"
        :row-height="tableLayout.rowHeight"
        layout-storage-key="clean-view-layout"
        @edit="handleEdit"
        @run="handleRun"
        @delete="handleDelete"
      />
    </div>

    <CleanFormDialog
      v-model="dialogVisible"
      :submitting="submitting"
      :object-options="objectOptions"
      :rule-options="ruleOptions"
      :strategy-options="strategyOptions"
      :mode="dialogMode"
      :initial-data="editingTask"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCleanRules, listCleanStrategies } from '../api/cleanrule'
import { listDataSourceObjects, listDataSources } from '../api/datasource'
import { createCleanTask, deleteCleanTask, listCleanTasks, runCleanTask, updateCleanTask } from '../api/dataclean'
import CleanToolbar from '../components/dataclean/CleanToolbar.vue'
import CleanTable from '../components/dataclean/CleanTable.vue'
import CleanFormDialog from '../components/dataclean/CleanFormDialog.vue'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const tasks = ref([])
const dialogMode = ref('create')
const editingTask = ref(null)
const sourceOptions = ref([])
const objectOptions = ref([])
const ruleOptions = ref([])
const strategyOptions = ref([])
const tableLayout = reactive({
  rowHeight: 44,
  size: 'default'
})
const TABLE_LAYOUT_KEY = 'app:table-layout:global'

const filters = reactive({
  keyword: '',
  sourceId: '',
  status: ''
})

async function loadSources() {
  const { data } = await listDataSources({})
  const sources = (data.data || []).map((item) => ({
    id: item.id,
    name: repairName(item.name, item)
  }))

  sourceOptions.value = sources

  const objectRequests = await Promise.all(
    sources.map(async (source) => {
      try {
        const res = await listDataSourceObjects(source.id)
        return (res.data.data || []).map((obj) => ({
          key: `${obj.sourceId}::${obj.objectName}`,
          sourceId: obj.sourceId,
          sourceName: source.name,
          sourceType: obj.sourceType,
          objectType: obj.objectType,
          objectName: obj.objectName,
          label: `${source.name} / ${obj.objectName}`
        }))
      } catch {
        return []
      }
    })
  )

  objectOptions.value = objectRequests.flat()
}

async function loadData() {
  loading.value = true
  try {
    const { data } = await listCleanTasks({ ...filters })
    tasks.value = data.data || []
  } finally {
    loading.value = false
  }
}

async function loadRules() {
  const [rulesRes, strategiesRes] = await Promise.all([
    listCleanRules(),
    listCleanStrategies()
  ])

  ruleOptions.value = (rulesRes.data.data || [])
    .filter((item) => item.enabled)
    .map((item) => ({ id: item.id, name: item.name }))

  strategyOptions.value = (strategiesRes.data.data || [])
    .filter((item) => item.enabled)
    .map((item) => ({ code: item.code, name: item.name }))
}

function handleReset() {
  loadData()
}

function handleFilterChange(nextFilters) {
  filters.keyword = nextFilters.keyword || ''
  filters.sourceId = nextFilters.sourceId || ''
  filters.status = nextFilters.status || ''
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingTask.value = null
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogMode.value = 'edit'
  editingTask.value = { ...row }
  dialogVisible.value = true
}

async function handleSubmit(payload) {
  if (!payload.cleanObjects?.length) {
    ElMessage.warning('请至少选择一个清洗对象')
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'edit' && editingTask.value?.id) {
      await updateCleanTask(editingTask.value.id, payload)
      ElMessage.success('清洗任务更新成功')
    } else {
      await createCleanTask(payload)
      ElMessage.success('清洗任务创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

async function handleRun(id) {
  try {
    await runCleanTask(id)
    ElMessage.success('清洗任务执行成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '执行失败')
  }
}

async function handleDelete(id) {
  try {
    await deleteCleanTask(id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '删除失败')
  }
}

function openRuleManagement() {
  router.push('/datasource/clean-rules')
}

function repairName(name, item) {
  if (!name) return ''
  if (name.includes('�') && item?.fileName) {
    return String(item.fileName).replace(/\.[^.]+$/, '')
  }
  return name
}

function loadTableLayout() {
  try {
    const cached = localStorage.getItem(TABLE_LAYOUT_KEY)
    if (!cached) return
    const parsed = JSON.parse(cached)
    if (typeof parsed?.rowHeight === 'number') {
      tableLayout.rowHeight = parsed.rowHeight
    }
    if (typeof parsed?.size === 'string') {
      tableLayout.size = parsed.size
    }
  } catch {
    // ignore invalid cache
  }
}

function handleTableLayoutChanged(event) {
  const next = event?.detail || {}
  if (typeof next?.rowHeight === 'number') {
    tableLayout.rowHeight = next.rowHeight
  }
  if (typeof next?.size === 'string') {
    tableLayout.size = next.size
  }
}

onMounted(async () => {
  loadTableLayout()
  window.addEventListener('table-layout-config-changed', handleTableLayoutChanged)
  await loadSources()
  await loadRules()
  await loadData()
})

onBeforeUnmount(() => {
  window.removeEventListener('table-layout-config-changed', handleTableLayoutChanged)
})
</script>

<style scoped>
.clean-view {
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
