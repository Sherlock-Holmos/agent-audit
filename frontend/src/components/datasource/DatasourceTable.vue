<template>
  <el-card ref="cardRef" shadow="never" class="table-wrap" :style="{ '--row-height': `${FIXED_ROW_HEIGHT}px` }">
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
      style="width: 100%"
      show-overflow-tooltip
      :show-header-overflow-tooltip="false"
      :height="tableHeight"
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

    <div v-if="!data.length && !loading" class="empty-tip">暂无数据源，请点击“新增数据源”进行配置。</div>
  </el-card>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import { useTableColumnLayout } from '../../composables/useTableColumnLayout'

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
  }
})
defineEmits(['status-change', 'delete', 'edit'])

const cardRef = ref()
const tableHeight = ref(420)
const FIXED_ROW_HEIGHT = 44
let resizeObserver

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

  const next = Math.max(260, Math.floor(available - chromeHeight))
  tableHeight.value = next
}

onMounted(() => {
  loadColumnWidths()

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

.table-wrap {
  height: 100%;
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

.empty-tip {
  margin-top: 14px;
  text-align: center;
  color: #909399;
}
</style>
