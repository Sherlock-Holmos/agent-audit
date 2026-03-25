<template>
  <el-table
    :data="data"
    v-loading="loading"
    border
    fit
    show-overflow-tooltip
    style="width: 100%"
    :size="tableLayout.size"
    :row-style="rowStyle"
    :header-row-style="headerRowStyle"
    @header-dragend="handleHeaderDragEnd"
  >
    <slot :resolveWidth="resolveWidth" :resolveMinWidth="resolveMinWidth" />
  </el-table>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'

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

function resolveWidth(key) {
  return columnWidths.value[key] || undefined
}

function resolveMinWidth(key, fallback) {
  return columnWidths.value[key] ? undefined : fallback
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

function resolveHeaderMinWidth(column) {
  const label = String(column?.label || '').trim()
  if (!label) {
    return 80
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

  const width = Math.max(80, Math.ceil((measured || label.length * 14) + 40))
  minWidthCache.set(label, width)
  return width
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
