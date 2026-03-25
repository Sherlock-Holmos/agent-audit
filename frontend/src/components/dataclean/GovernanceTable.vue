<template>
  <el-table
    :data="data"
    v-loading="loading"
    border
    fit
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
  rowHeight: 44,
  size: 'default'
})
const columnWidths = ref({})

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
  if (typeof next?.rowHeight === 'number') {
    tableLayout.rowHeight = next.rowHeight
  }
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
  columnWidths.value = {
    ...columnWidths.value,
    [key]: Math.max(80, Math.round(newWidth || 0))
  }
  localStorage.setItem(`${props.layoutStorageKey}:columns`, JSON.stringify(columnWidths.value))
}

function rowStyle() {
  return {
    height: `${tableLayout.rowHeight}px`
  }
}

function headerRowStyle() {
  return {
    height: `${tableLayout.rowHeight}px`
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
