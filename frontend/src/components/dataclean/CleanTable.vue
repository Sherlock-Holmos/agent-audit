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
        column-key="cleanObjectNames"
        label="清洗对象"
        :width="resolveWidth('cleanObjectNames', 260)"
        :min-width="resolveMinWidth('cleanObjectNames', 260)"
      >
        <template #default="scope">
          {{ (scope.row.cleanObjectNames || []).join('；') || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="cleanRuleNames"
        label="应用规则"
        :width="resolveWidth('cleanRuleNames', 180)"
        :min-width="resolveMinWidth('cleanRuleNames', 180)"
      >
        <template #default="scope">
          {{ (scope.row.cleanRuleNames || []).join('、') || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="strategy"
        label="清洗策略"
        :width="resolveWidth('strategy', 160)"
        :min-width="resolveMinWidth('strategy', 160)"
      >
        <template #default="scope">
          {{ scope.row.strategyName || scope.row.strategy || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="standardTable"
        prop="standardTable"
        label="标准化表"
        :width="resolveWidth('standardTable', 180)"
        :min-width="resolveMinWidth('standardTable', 180)"
      />
      <el-table-column
        column-key="cleanedRows"
        prop="cleanedRows"
        label="清洗数据量"
        :width="resolveWidth('cleanedRows', 120)"
        :min-width="resolveMinWidth('cleanedRows', 120)"
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
      <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions', 260)" align="center" fixed="right">
        <template #default="scope">
          <el-button type="primary" link :disabled="scope.row.status !== 'COMPLETED'" @click="$emit('preview', scope.row)">
            预览
          </el-button>
          <el-button type="primary" link :disabled="scope.row.status === 'COMPLETED' || scope.row.status === 'RUNNING'" @click="$emit('edit', scope.row)">
            编辑
          </el-button>
          <el-button type="primary" link :disabled="scope.row.status === 'COMPLETED'" @click="$emit('run', scope.row.id)">
            执行
          </el-button>
          <el-popconfirm title="确认删除该清洗任务？" @confirm="$emit('delete', scope.row.id)">
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
    default: 'clean-table-layout'
  },
  bottomOffset: {
    type: Number,
    default: 8
  }
})

defineEmits(['run', 'delete', 'edit', 'preview'])

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

function normalizeLoadedColumnWidths() {
  const minByKey = {
    taskName: 180,
    cleanObjectNames: 260,
    cleanRuleNames: 180,
    strategy: 160,
    standardTable: 180,
    cleanedRows: 120,
    status: 120,
    updatedAt: 180,
    actions: 260
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

  const width = Math.max(100, Math.ceil((measured || label.length * 14) + 56))
  minWidthCache.set(label, width)
  return width
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
  text-overflow: clip;
}

.table-wrap {
  height: 100%;
}

</style>
