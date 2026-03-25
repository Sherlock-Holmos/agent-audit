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
        @preview="handlePreview"
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

    <el-drawer v-model="previewVisible" title="清洗结果预览" size="68%">
      <div v-loading="previewLoading" class="preview-wrap">
        <el-alert
          v-if="previewTask"
          type="info"
          :closable="false"
          show-icon
          :title="`任务：${previewTask.taskName} / 标准表：${previewTask.standardTable}`"
          class="preview-alert"
        />

        <el-row v-if="previewTask" :gutter="12" class="preview-stats">
          <el-col :span="8">
            <el-statistic title="预览行数" :value="previewRows.length" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="清洗总行数" :value="previewStats.totalRows || 0" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="预览上限" :value="previewStats.previewLimit || 0" />
          </el-col>
        </el-row>

        <el-table :data="previewRows" stripe border max-height="520">
          <el-table-column prop="rowNo" label="行号" width="80" />
          <el-table-column prop="objectName" label="来源对象" min-width="180" />
          <el-table-column prop="sourceId" label="来源ID" width="120" />
          <el-table-column label="标准字段数" width="120" align="right">
            <template #default="scope">
              {{ Object.keys(scope.row.normalizedData || {}).length }}
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="220">
            <template #default="scope">
              <el-popover placement="left" width="560" trigger="click">
                <template #reference>
                  <el-button link type="primary">查看JSON</el-button>
                </template>
                <el-tabs>
                  <el-tab-pane label="原始记录">
                    <pre class="json-pre">{{ toPrettyJson(scope.row.rawData) }}</pre>
                  </el-tab-pane>
                  <el-tab-pane label="清洗结果">
                    <pre class="json-pre">{{ toPrettyJson(scope.row.normalizedData) }}</pre>
                  </el-tab-pane>
                </el-tabs>
              </el-popover>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCleanRules } from '../api/clean-rule'
import { listCleanStrategies } from '../api/clean-strategy'
import { listDataSourceObjects, listDataSources } from '../api/datasource'
import { createCleanTask, deleteCleanTask, getCleanTaskPreview, listCleanTasks, runCleanTask, updateCleanTask } from '../api/dataclean'
import { getErrorMessage } from '../utils/error'
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
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTask = ref(null)
const previewRows = ref([])
const previewStats = reactive({
  totalRows: 0,
  previewLimit: 0
})
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
  const [rulesRes, strategiesRes] = await Promise.allSettled([
    listCleanRules(),
    listCleanStrategies()
  ])

  if (rulesRes.status === 'fulfilled') {
    ruleOptions.value = (rulesRes.value.data.data || [])
      .filter((item) => item.enabled)
      .map((item) => ({ id: item.id, name: item.name }))
  } else {
    ruleOptions.value = []
    ElMessage.error(getErrorMessage(rulesRes.reason, '加载清洗规则失败'))
  }

  if (strategiesRes.status === 'fulfilled') {
    strategyOptions.value = (strategiesRes.value.data.data || [])
      .filter((item) => item.enabled)
      .map((item) => ({ code: item.code, name: item.name }))
  } else {
    strategyOptions.value = []
    ElMessage.error(getErrorMessage(strategiesRes.reason, '加载清洗策略失败'))
  }
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
    ElMessage.error(getErrorMessage(error, '创建失败'))
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
    ElMessage.error(getErrorMessage(error, '执行失败'))
  }
}

async function handleDelete(id) {
  try {
    await deleteCleanTask(id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除失败'))
  }
}

async function handlePreview(row) {
  previewVisible.value = true
  previewLoading.value = true
  try {
    const { data } = await getCleanTaskPreview(row.id, { limit: 50 })
    const payload = data.data || {}
    previewTask.value = payload.task || row
    previewStats.totalRows = Number(payload.totalRows) || 0
    previewStats.previewLimit = Number(payload.previewLimit) || 0
    const sourceRows = Array.isArray(payload.rows) ? payload.rows : []
    previewRows.value = sourceRows.map((item, index) => {
      const normalizedData = parseJsonMaybe(item.normalized_json, item.normalizedJson)
      const rawData = parseJsonMaybe(item.raw_json, item.rawJson)
      return {
        rowNo: Number(item.row_no) || Number(item.rowNo) || index + 1,
        objectName: item.object_name || item.objectName || '-',
        sourceId: item.source_id || item.sourceId || '-',
        rawData,
        normalizedData
      }
    })
  } catch (error) {
    previewVisible.value = false
    ElMessage.error(getErrorMessage(error, '加载清洗结果失败'))
  } finally {
    previewLoading.value = false
  }
}

function openRuleManagement() {
  router.push('/datasource/clean-rules/rules')
}

function repairName(name, item) {
  if (!name) return ''
  if (name.includes('�') && item?.fileName) {
    return String(item.fileName).replace(/\.[^.]+$/, '')
  }
  return name
}

function parseJsonMaybe(value, fallback = {}) {
  if (value && typeof value === 'object') {
    return value
  }
  const text = typeof value === 'string' && value.trim() ? value : (typeof fallback === 'string' ? fallback : '')
  if (!text) {
    return {}
  }
  try {
    return JSON.parse(text)
  } catch {
    return {}
  }
}

function toPrettyJson(value) {
  try {
    return JSON.stringify(value ?? {}, null, 2)
  } catch {
    return String(value ?? '')
  }
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

.preview-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-alert {
  margin-bottom: 4px;
}

.preview-stats {
  margin-bottom: 6px;
}

.json-pre {
  margin: 0;
  max-height: 360px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
