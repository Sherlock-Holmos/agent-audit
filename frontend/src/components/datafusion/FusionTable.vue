<template>
  <el-card ref="cardRef" shadow="never" class="table-wrap" :style="{ '--row-height': `${FIXED_ROW_HEIGHT}px` }">
    <el-table
      :data="data"
      v-loading="loading"
      border
      style="width: 100%"
      show-overflow-tooltip
      :show-header-overflow-tooltip="false"
      :height="tableHeight"
      :size="tableSize"
      :row-style="rowStyle"
      :header-row-style="headerRowStyle"
      fit
      @header-dragend="handleHeaderDragEnd"
    >
      <el-table-column
        column-key="taskName"
        prop="taskName"
        label="任务名称"
        :width="resolveWidth('taskName', 180)"
        :min-width="resolveMinWidth('taskName', 180)"
      />
      <el-table-column
        column-key="targetTable"
        prop="targetTable"
        label="目标整合表"
        :width="resolveWidth('targetTable', 180)"
        :min-width="resolveMinWidth('targetTable', 180)"
      />
      <el-table-column
        column-key="cleanTaskNames"
        label="清洗任务"
        :width="resolveWidth('cleanTaskNames', 220)"
        :min-width="resolveMinWidth('cleanTaskNames', 220)"
      >
        <template #default="scope">
          {{ (scope.row.cleanTaskNames || []).join('、') || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="standardTables"
        label="标准化表"
        :width="resolveWidth('standardTables', 220)"
        :min-width="resolveMinWidth('standardTables', 220)"
      >
        <template #default="scope">
          {{ (scope.row.standardTables || []).join('、') || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="strategy"
        prop="strategy"
        label="融合策略"
        :width="resolveWidth('strategy', 150)"
        :min-width="resolveMinWidth('strategy', 150)"
      />
      <el-table-column
        column-key="fusionRows"
        prop="fusionRows"
        label="融合数据量"
        :width="resolveWidth('fusionRows', 120)"
        :min-width="resolveMinWidth('fusionRows', 120)"
        align="right"
      />
      <el-table-column
        column-key="status"
        label="状态"
        :width="resolveWidth('status', 120)"
        :min-width="resolveMinWidth('status', 120)"
        align="center"
      >
        <template #default="scope">
          <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : (scope.row.status === 'FAILED' ? 'danger' : 'info')">
            {{ scope.row.status === 'COMPLETED' ? '已完成' : (scope.row.status === 'FAILED' ? '失败' : '待执行') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        column-key="updatedAt"
        prop="updatedAt"
        label="更新时间"
        :width="resolveWidth('updatedAt', 180)"
        :min-width="resolveMinWidth('updatedAt', 180)"
      />
      <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions', 270)" align="center" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="$emit('preview', scope.row)">
            结果解释
          </el-button>
          <el-button type="primary" link :disabled="scope.row.status === 'COMPLETED' || scope.row.status === 'RUNNING'" @click="$emit('edit', scope.row)">
            编辑
          </el-button>
          <el-button type="primary" link :disabled="scope.row.status === 'COMPLETED'" @click="$emit('run', scope.row.id)">
            执行
          </el-button>
          <el-popconfirm title="确认删除该融合任务？" @confirm="$emit('delete', scope.row.id)">
            <template #reference>
              <el-button type="danger" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

  </el-card>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

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
    default: 'fusion-table-layout'
  },
  bottomOffset: {
    type: Number,
    default: 8
  }
})

defineEmits(['preview', 'run', 'delete', 'edit'])

const columnWidths = ref({})
const minWidthCache = new Map()
const cardRef = ref()
const tableHeight = ref(420)
const FIXED_ROW_HEIGHT = 44
let resizeObserver

function updateTableHeight() {
  const cardEl = cardRef.value?.$el || cardRef.value
  if (!cardEl) return
  const cardStyle = window.getComputedStyle(cardEl)
  const borderTop = Number.parseFloat(cardStyle.borderTopWidth || '0') || 0
  const borderBottom = Number.parseFloat(cardStyle.borderBottomWidth || '0') || 0
  const bodyEl = cardEl.querySelector('.el-card__body')
  let bodyPadding = 0
  if (bodyEl) {
    const bodyStyle = window.getComputedStyle(bodyEl)
    bodyPadding += Number.parseFloat(bodyStyle.paddingTop || '0') || 0
    bodyPadding += Number.parseFloat(bodyStyle.paddingBottom || '0') || 0
  }
  const chromeHeight = borderTop + borderBottom + bodyPadding
  const parentHeight = cardEl.parentElement?.clientHeight || 0

  let available = 0
  if (parentHeight > 0) {
    available = parentHeight - props.bottomOffset
  } else {
    const top = cardEl.getBoundingClientRect().top
    const viewportHeight = document.documentElement.clientHeight || window.innerHeight
    available = viewportHeight - top - props.bottomOffset
  }

  tableHeight.value = Math.max(260, Math.floor(available - chromeHeight))
}

onMounted(() => {
  try {
    const cached = localStorage.getItem(`${props.layoutStorageKey}:columns`)
    columnWidths.value = cached ? JSON.parse(cached) : {}
  } catch {
    columnWidths.value = {}
  }

  nextTick(() => {
    normalizeLoadedColumnWidths()
    updateTableHeight()
    window.addEventListener('resize', updateTableHeight)
    const cardEl = cardRef.value?.$el || cardRef.value
    if (cardEl && window.ResizeObserver) {
      resizeObserver = new ResizeObserver(() => updateTableHeight())
      resizeObserver.observe(cardEl)
    }
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableHeight)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})

function resolveWidth(key, fallback = 80) {
  const width = toWidthNumber(columnWidths.value[key])
  if (width == null) {
    return undefined
  }
  return Math.max(width, fallback)
}

function resolveMinWidth(key, fallback) {
  const width = toWidthNumber(columnWidths.value[key])
  return width == null ? fallback : undefined
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

function normalizeLoadedColumnWidths() {
  const minByKey = {
    taskName: 180,
    targetTable: 180,
    cleanTaskNames: 220,
    standardTables: 220,
    strategy: 150,
    fusionRows: 120,
    status: 120,
    updatedAt: 180,
    actions: 270
  }
  const next = { ...columnWidths.value }
  let changed = false
  Object.entries(minByKey).forEach(([key, min]) => {
    const current = toWidthNumber(next[key])
    if (current == null) {
      if (next[key] !== undefined) {
        delete next[key]
        changed = true
      }
      return
    }
    if (current < min) {
      next[key] = min
      changed = true
    } else if (next[key] !== current) {
      next[key] = current
      changed = true
    }
  })
  if (changed) {
    columnWidths.value = next
    localStorage.setItem(`${props.layoutStorageKey}:columns`, JSON.stringify(next))
  }
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

.table-wrap {
  height: 100%;
}

</style>
