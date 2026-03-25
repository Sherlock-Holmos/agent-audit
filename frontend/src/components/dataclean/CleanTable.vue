<template>
  <el-card ref="cardRef" shadow="never" class="table-wrap" :style="{ '--row-height': `${rowHeight}px` }">
    <el-table
      :data="data"
      v-loading="loading"
      border
      style="width: 100%"
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
        :width="resolveWidth('taskName')"
        :min-width="resolveMinWidth('taskName', 180)"
      />
      <el-table-column
        column-key="cleanObjectNames"
        label="清洗对象"
        :width="resolveWidth('cleanObjectNames')"
        :min-width="resolveMinWidth('cleanObjectNames', 260)"
      >
        <template #default="scope">
          {{ (scope.row.cleanObjectNames || []).join('；') || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="cleanRuleNames"
        label="应用规则"
        :width="resolveWidth('cleanRuleNames')"
        :min-width="resolveMinWidth('cleanRuleNames', 180)"
      >
        <template #default="scope">
          {{ (scope.row.cleanRuleNames || []).join('、') || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        column-key="strategy"
        label="清洗策略"
        :width="resolveWidth('strategy')"
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
        :width="resolveWidth('standardTable')"
        :min-width="resolveMinWidth('standardTable', 180)"
      />
      <el-table-column
        column-key="cleanedRows"
        prop="cleanedRows"
        label="清洗数据量"
        :width="resolveWidth('cleanedRows')"
        :min-width="resolveMinWidth('cleanedRows', 120)"
        align="right"
      />
      <el-table-column
        column-key="status"
        label="状态"
        :width="resolveWidth('status')"
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
        :width="resolveWidth('updatedAt')"
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
  rowHeight: {
    type: Number,
    default: 44
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
const cardRef = ref()
const tableHeight = ref(420)
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
    height: `${props.rowHeight}px`
  }
}

function headerRowStyle() {
  return {
    height: `${props.rowHeight}px`
  }
}
</script>

<style scoped>
.table-wrap :deep(.el-table .cell) {
  line-height: calc(var(--row-height) - 12px);
}

.table-wrap {
  height: 100%;
}

</style>
