<template>
  <div class="governance-table-wrap">
    <TableLayoutActions @reset="resetColumnLayout" />

    <el-table
      :data="data"
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
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { useTableColumnLayout } from '../../composables/useTableColumnLayout'
import TableLayoutActions from '../shared/TableLayoutActions.vue'

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
  fixedRowHeight: FIXED_ROW_HEIGHT
})

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
</style>
