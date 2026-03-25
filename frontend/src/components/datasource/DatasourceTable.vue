<template>
  <el-card shadow="never" class="table-wrap" :style="{ '--row-height': `${FIXED_ROW_HEIGHT}px` }">
    <TableLayoutActions @reset="resetColumnLayout" />

    <el-table
      ref="tableRef"
      :data="pagedData"
      v-loading="loading"
      border
      style="width: 100%"
      show-overflow-tooltip
      :show-header-overflow-tooltip="false"
      :size="tableSize"
      :row-style="rowStyle"
      :header-row-style="headerRowStyle"
      :fit="shouldAutoFit()"
      @header-dragend="handleHeaderDragEnd"
    >
      <el-table-column
        column-key="name"
        label="数据源名称"
        :width="resolveWidth('name', 180)"
        :min-width="resolveMinWidth('name', 180)"
      >
        <template #default="scope">
          {{ formatText(scope.row.name, scope.row, 'name') }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="type"
        label="类型"
        :width="resolveWidth('type', 120)"
        :min-width="resolveMinWidth('type', 120)"
      >
        <template #default="scope">
          <el-tag :type="scope.row.type === 'DATABASE' ? 'primary' : 'success'">
            {{ scope.row.type === 'DATABASE' ? '数据库' : '本地文件' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        column-key="connection"
        label="连接信息"
        :width="resolveWidth('connection', 280)"
        :min-width="resolveMinWidth('connection', 280)"
      >
        <template #default="scope">
          <span v-if="scope.row.type === 'DATABASE'">
            {{ scope.row.dbType }} / {{ scope.row.host }}:{{ scope.row.port }} / {{ scope.row.databaseName }}
          </span>
          <span v-else>
            {{ formatText(scope.row.fileName, scope.row, 'fileName') }} ({{ formatSize(scope.row.fileSize || 0) }})
          </span>
        </template>
      </el-table-column>
      <el-table-column
        column-key="status"
        label="状态"
        :width="resolveWidth('status', 120)"
        :min-width="resolveMinWidth('status', 120)"
        align="center"
      >
        <template #default="scope">
          <el-switch
            :model-value="scope.row.status === 'ENABLED'"
            @change="(val) => $emit('status-change', { id: scope.row.id, status: val ? 'ENABLED' : 'DISABLED' })"
          />
        </template>
      </el-table-column>
      <el-table-column
        column-key="createdAt"
        prop="createdAt"
        label="创建时间"
        :width="resolveWidth('createdAt', 180)"
        :min-width="resolveMinWidth('createdAt', 180)"
      />
      <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions', 170)" align="center" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="$emit('edit', scope.row)">编辑</el-button>
          <el-popconfirm title="确认删除该数据源？" @confirm="$emit('delete', scope.row.id)">
            <template #reference>
              <el-button type="danger" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="showPagination" class="table-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="pageSizes"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>

    <TableEmptyTip :show="!data.length && !loading" :text="emptyText" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useTableColumnLayout } from '../../composables/useTableColumnLayout'
import { useTablePagination } from '../../composables/useTablePagination'
import TableLayoutActions from '../shared/TableLayoutActions.vue'
import TableEmptyTip from '../shared/TableEmptyTip.vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  tableSize: {
    type: String,
    default: 'default'
  },
  layoutStorageKey: {
    type: String,
    default: 'datasource-table-layout'
  },
  bottomOffset: {
    type: Number,
    default: 8
  },
  emptyText: {
    type: String,
    default: '暂无数据源，请点击“新增数据源”进行配置。'
  }
})
defineEmits(['status-change', 'delete', 'edit'])

const FIXED_ROW_HEIGHT = 44
const tableRef = ref()
const {
  currentPage,
  pageSize,
  pageSizes,
  total,
  pagedData,
  showPagination,
  handleCurrentChange,
  handleSizeChange
} = useTablePagination(() => props.data)

const {
  loadColumnWidths,
  resolveWidth,
  resolveMinWidth,
  shouldAutoFit,
  handleHeaderDragEnd,
  resetColumnLayout,
  rowStyle,
  headerRowStyle
} = useTableColumnLayout({
  layoutStorageKey: () => props.layoutStorageKey,
  tableRef,
  fixedRowHeight: FIXED_ROW_HEIGHT,
  minByKey: {
    name: 180,
    type: 120,
    connection: 280,
    status: 120,
    createdAt: 180,
    actions: 170
  }
})

onMounted(() => {
  loadColumnWidths()
})

function formatSize(size) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function formatText(value, row, field) {
  const raw = typeof value === 'string' ? value : ''
  if (!raw) return ''

  if (raw.includes('�') && field === 'name' && row?.type === 'FILE' && row?.fileName) {
    const fallback = String(row.fileName).replace(/\.[^.]+$/, '')
    return fallback || raw
  }

  const repaired = repairMojibake(raw)
  return repaired || raw
}

function repairMojibake(value) {
  try {
    const decoded = decodeURIComponent(escape(value))
    const sourceScore = chineseScore(value)
    const decodedScore = chineseScore(decoded)
    return decodedScore > sourceScore ? decoded : value
  } catch {
    return value
  }
}

function chineseScore(text) {
  const matches = String(text).match(/[\u4e00-\u9fa5]/g)
  return matches ? matches.length : 0
}
</script>

<style scoped>
.table-wrap :deep(.el-table .cell) {
  line-height: calc(var(--row-height) - 12px);
}

.table-wrap :deep(.el-table th.el-table__cell .cell) {
  white-space: nowrap;
  overflow: visible !important;
  text-overflow: clip !important;
}

.table-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

</style>
