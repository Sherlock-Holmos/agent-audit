<template>
  <component
    :is="withCard ? 'el-card' : 'div'"
    :class="containerClass"
    :shadow="withCard ? 'never' : undefined"
    :style="{ '--row-height': `${fixedRowHeight}px` }"
  >
    <TableLayoutActions @reset="resetColumnLayout" />

    <el-table
      ref="tableRef"
      :data="pagedData"
      v-loading="loading"
      border
      style="width: 100%"
      show-overflow-tooltip
      :show-header-overflow-tooltip="false"
      :fit="shouldAutoFit()"
      :size="resolvedTableSize"
      :row-style="rowStyle"
      :header-row-style="headerRowStyle"
      v-bind="tableProps"
      @header-dragend="handleHeaderDragEnd"
    >
      <slot :resolveWidth="resolveWidth" :resolveMinWidth="resolveMinWidth" />
    </el-table>

    <div v-if="showPagination && innerShowPagination" class="table-pagination">
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

    <TableEmptyTip :show="showEmpty && !data.length && !loading && !!emptyText" :text="emptyText" />
  </component>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useTableColumnLayout } from '../../composables/useTableColumnLayout'
import { useTablePagination } from '../../composables/useTablePagination'
import TableLayoutActions from './TableLayoutActions.vue'
import TableEmptyTip from './TableEmptyTip.vue'

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
  minByKey: {
    type: Object,
    default: () => ({})
  },
  tableSize: {
    type: String,
    default: 'default'
  },
  followGlobalTableSize: {
    type: Boolean,
    default: false
  },
  withCard: {
    type: Boolean,
    default: true
  },
  cardClass: {
    type: String,
    default: 'table-wrap'
  },
  fixedRowHeight: {
    type: Number,
    default: 44
  },
  showPagination: {
    type: Boolean,
    default: true
  },
  showEmpty: {
    type: Boolean,
    default: true
  },
  emptyText: {
    type: String,
    default: ''
  },
  tableProps: {
    type: Object,
    default: () => ({})
  },
  paginationOptions: {
    type: Object,
    default: () => ({})
  }
})

const TABLE_LAYOUT_KEY = 'app:table-layout:global'
const tableLayout = reactive({
  size: props.tableSize
})

const tableRef = ref()

const {
  currentPage,
  pageSize,
  pageSizes,
  total,
  pagedData,
  showPagination: innerShowPagination,
  handleCurrentChange,
  handleSizeChange
} = useTablePagination(() => props.data, props.paginationOptions)

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
  minByKey: props.minByKey,
  tableRef,
  fixedRowHeight: props.fixedRowHeight
})

const resolvedTableSize = computed(() => {
  if (props.followGlobalTableSize) {
    return tableLayout.size
  }
  return props.tableSize
})

const containerClass = computed(() => {
  if (props.withCard) {
    return props.cardClass
  }
  return props.cardClass ? [props.cardClass, 'plain-wrap'] : 'plain-wrap'
})

function loadTableLayout() {
  if (!props.followGlobalTableSize) {
    tableLayout.size = props.tableSize
    return
  }
  try {
    const cached = localStorage.getItem(TABLE_LAYOUT_KEY)
    if (!cached) {
      tableLayout.size = props.tableSize
      return
    }
    const parsed = JSON.parse(cached)
    if (typeof parsed?.size === 'string') {
      tableLayout.size = parsed.size
    }
  } catch {
    tableLayout.size = props.tableSize
  }
}

function handleTableLayoutChanged(event) {
  if (!props.followGlobalTableSize) {
    return
  }
  const next = event?.detail || {}
  if (typeof next?.size === 'string') {
    tableLayout.size = next.size
  }
}

onMounted(() => {
  loadColumnWidths()
  loadTableLayout()
  if (props.followGlobalTableSize) {
    window.addEventListener('table-layout-config-changed', handleTableLayoutChanged)
  }
})

onBeforeUnmount(() => {
  if (props.followGlobalTableSize) {
    window.removeEventListener('table-layout-config-changed', handleTableLayoutChanged)
  }
})
</script>

<style scoped>
.table-wrap :deep(.el-table .cell) {
  line-height: calc(var(--row-height) - 12px);
}

.table-wrap :deep(.el-table th.el-table__cell .cell),
.plain-wrap :deep(.el-table th.el-table__cell .cell) {
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
