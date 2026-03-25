<template>
  <div class="governance-table-wrap">
    <div class="table-layout-actions">
      <el-tooltip content="重置列宽" placement="left">
        <el-button class="table-layout-reset-btn" size="small" @click="resetColumnLayout">
          <el-icon><RefreshRight /></el-icon>
          <span>重置列宽</span>
        </el-button>
      </el-tooltip>
    </div>

    <el-table
      :data="data"
      v-loading="loading"
      border
      :fit="false"
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
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'

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
const columnWidths = ref({})
const minWidthCache = new Map()
const FIXED_ROW_HEIGHT = 44

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

function loadColumnWidths() {
  try {
    const cached = localStorage.getItem(`${props.layoutStorageKey}:columns`)
    columnWidths.value = cached ? JSON.parse(cached) : {}
  } catch {
    columnWidths.value = {}
  }
}

function handleTableLayoutChanged(event) {
  const next = event?.detail || {}
  if (typeof next?.size === 'string') {
    tableLayout.size = next.size
  }
}

function resolveWidth(key, fallback = 80) {
  const width = toWidthNumber(columnWidths.value[key])
  if (width == null) {
    return fallback
  }
  return Math.max(width, fallback)
}

function resolveMinWidth(key, fallback) {
  return undefined
}

function handleHeaderDragEnd(newWidth, _oldWidth, column) {
  const key = String(column?.columnKey || column?.property || column?.label || '').trim()
  if (!key) return
  const minWidth = resolveHeaderMinWidth(column)
  columnWidths.value = {
    ...columnWidths.value,
    [key]: Math.max(minWidth, Math.round(newWidth || 0))
  }
  localStorage.setItem(`${props.layoutStorageKey}:columns`, JSON.stringify(columnWidths.value))
}

function resetColumnLayout() {
  columnWidths.value = {}
  localStorage.removeItem(`${props.layoutStorageKey}:columns`)
}

function resolveHeaderMinWidth(column) {
  const label = String(column?.label || '').trim()
  const domMin = resolveHeaderDomMinWidth(column)
  if (!label) {
    return Math.max(100, domMin)
  }

  if (minWidthCache.has(label)) {
    return minWidthCache.get(label)
  }

  let measured = 0
  if (typeof document !== 'undefined') {
    const canvas = resolveHeaderMinWidth._canvas || (resolveHeaderMinWidth._canvas = document.createElement('canvas'))
    const context = canvas.getContext('2d')
    if (context) {
      context.font = '14px sans-serif'
      measured = context.measureText(label).width
    }
  }

  const width = Math.max(100, Math.ceil((measured || label.length * 14) + 56), domMin)
  minWidthCache.set(label, width)
  return width
}

function resolveHeaderDomMinWidth(column) {
  if (typeof document === 'undefined') {
    return 0
  }
  const columnId = String(column?.id || '').trim()
  if (!columnId) {
    return 0
  }
  const cell = document.querySelector(`th.${columnId} .cell`)
  if (!cell) {
    return 0
  }
  return Math.ceil(cell.scrollWidth + 24)
}

function toWidthNumber(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) {
      return null
    }
    const parsed = Number.parseFloat(trimmed)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function rowStyle() {
  return {
    height: `${FIXED_ROW_HEIGHT}px`
  }
}

function headerRowStyle() {
  return {
    height: `${FIXED_ROW_HEIGHT}px`
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

.table-layout-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.table-layout-reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

:deep(.el-table th.el-table__cell .cell) {
  white-space: nowrap;
  overflow: visible !important;
  text-overflow: clip !important;
}
</style>
