<template>
  <div class="governance-table-wrap">
    <TableLayoutActions @reset="resetColumnLayout" />

    <el-table
      :data="pagedData"
      v-loading="loading"
      border
      :fit="shouldAutoFit()"
      show-overflow-tooltip
      :show-header-overflow-tooltip="false"
      style="width: 100%"
      :size="tableLayout.size"
      :row-style="rowStyle"
      :header-row-style="headerRowStyle"
      @header-dragend="handleHeaderDragEnd"
    >
      <slot :resolveWidth="resolveWidth" :resolveMinWidth="resolveMinWidth" />
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

    <TableEmptyTip :show="!data.length && !loading && !!emptyText" :text="emptyText" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive } from 'vue'
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
  layoutStorageKey: {
    type: String,
    required: true
  },
  columnKeys: {
    type: Array,
    default: () => []
  },
  emptyText: {
    type: String,
    default: ''
  }
})

const TABLE_LAYOUT_KEY = 'app:table-layout:global'
const tableLayout = reactive({
  size: 'default'
})
const FIXED_ROW_HEIGHT = 44

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
  columnKeys: props.columnKeys,
  fixedRowHeight: FIXED_ROW_HEIGHT
})

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
  loadColumnWidths()
  window.addEventListener('table-layout-config-changed', handleTableLayoutChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('table-layout-config-changed', handleTableLayoutChanged)
})
</script>

<style scoped>
:deep(.governance-table-wrap) {
  width: 100%;
}

:deep(.el-table th.el-table__cell .cell) {
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
