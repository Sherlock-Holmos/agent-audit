<template>
  <div class="fusion-view">
    <div class="toolbar-host">
      <FusionToolbar
        :filters="filters"
        @update:filters="handleFilterChange"
        @search="loadData"
        @reset="handleReset"
        @create="openCreateDialog"
      />
    </div>

    <div class="table-host">
      <FusionTable
        :data="tasks"
        :loading="loading"
        :table-size="tableLayout.size"
        :row-height="tableLayout.rowHeight"
        layout-storage-key="fusion-view-layout"
        @preview="handlePreview"
        @edit="handleEdit"
        @run="handleRun"
        @delete="handleDelete"
      />
    </div>

    <FusionFormDialog
      v-model="dialogVisible"
      :submitting="submitting"
      :clean-task-options="cleanTaskOptions"
      :mode="dialogMode"
      :initial-data="editingTask"
      @submit="handleSubmit"
    />

    <el-drawer v-model="previewVisible" title="融合结果解释" size="68%">
      <div v-loading="previewLoading" class="preview-wrap">
        <el-alert
          v-if="previewTask"
          type="info"
          :closable="false"
          show-icon
          :title="`任务：${previewTask.taskName} / 目标表：${previewTask.targetTable}`"
          class="preview-alert"
        />

        <el-row v-if="previewTask" :gutter="12" class="preview-stats">
          <el-col :span="8">
            <el-statistic title="结果总行数" :value="previewStats.totalRows || 0" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="跨表合并行数" :value="previewStats.mergedRows || 0" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="联合命中率" :value="previewStats.mergeRate || 0" suffix="%" />
          </el-col>
        </el-row>

        <el-table :data="previewRows" stripe border max-height="520">
          <el-table-column prop="rowNo" label="行号" width="80" />
          <el-table-column label="来源标准表" min-width="220">
            <template #default="scope">
              {{ (scope.row.sourceTables || []).join('、') || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="匹配说明" width="130">
            <template #default="scope">
              <el-tag :type="(scope.row.sourceTables || []).length > 1 ? 'success' : 'info'">
                {{ (scope.row.sourceTables || []).length > 1 ? '跨表联合' : '单源记录' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="合并字段数" width="120" align="right">
            <template #default="scope">
              {{ Object.keys(scope.row.normalizedData || {}).length }}
            </template>
          </el-table-column>
          <el-table-column label="原始记录条数" width="120" align="right">
            <template #default="scope">
              {{ Array.isArray(scope.row.rawData) ? scope.row.rawData.length : 1 }}
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="220">
            <template #default="scope">
              <el-popover placement="left" width="560" trigger="click">
                <template #reference>
                  <el-button link type="primary">查看JSON</el-button>
                </template>
                <el-tabs>
                  <el-tab-pane label="合并结果">
                    <pre class="json-pre">{{ toPrettyJson(scope.row.normalizedData) }}</pre>
                  </el-tab-pane>
                  <el-tab-pane label="来源记录">
                    <pre class="json-pre">{{ toPrettyJson(scope.row.rawData) }}</pre>
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
import { ElMessage } from 'element-plus'
import { listCleanTasks } from '../api/dataclean'
import { createFusionTask, deleteFusionTask, getFusionTaskPreview, listFusionTasks, runFusionTask, updateFusionTask } from '../api/datafusion'
import FusionToolbar from '../components/datafusion/FusionToolbar.vue'
import FusionTable from '../components/datafusion/FusionTable.vue'
import FusionFormDialog from '../components/datafusion/FusionFormDialog.vue'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const tasks = ref([])
const dialogMode = ref('create')
const editingTask = ref(null)
const cleanTaskOptions = ref([])
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTask = ref(null)
const previewStats = reactive({
  totalRows: 0,
  mergedRows: 0,
  mergeRate: 0,
  previewLimit: 0
})
const previewRows = ref([])
const tableLayout = reactive({
  rowHeight: 44,
  size: 'default'
})
const TABLE_LAYOUT_KEY = 'app:table-layout:global'

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
  if (!payload.cleanTaskIds?.length) {
    ElMessage.warning('请至少选择一个清洗任务')
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'edit' && editingTask.value?.id) {
      await updateFusionTask(editingTask.value.id, payload)
      ElMessage.success('融合任务更新成功')
    } else {
      await createFusionTask(payload)
      ElMessage.success('融合任务创建成功')
    }
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

async function handlePreview(row) {
  previewVisible.value = true
  previewLoading.value = true
  try {
    const { data } = await getFusionTaskPreview(row.id, { limit: 50 })
    const payload = data.data || {}
    previewTask.value = payload.task || row
    Object.assign(previewStats, payload.stats || {
      totalRows: 0,
      mergedRows: 0,
      mergeRate: 0,
      previewLimit: 0
    })
    previewRows.value = payload.rows || []
  } catch (error) {
    previewVisible.value = false
    ElMessage.error(error?.response?.data?.message || error?.message || '加载融合结果失败')
  } finally {
    previewLoading.value = false
  }
}

function toPrettyJson(value) {
  try {
    return JSON.stringify(value ?? {}, null, 2)
  } catch (error) {
    return String(value ?? '')
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
  await loadCleanTaskOptions()
  await loadData()
})

onBeforeUnmount(() => {
  window.removeEventListener('table-layout-config-changed', handleTableLayoutChanged)
})
</script>

<style scoped>
.fusion-view {
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
