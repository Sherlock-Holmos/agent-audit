<template>
  <el-card ref="cardRef" shadow="never" class="table-wrap" :style="{ '--row-height': `${FIXED_ROW_HEIGHT}px` }">
    <TableLayoutActions @reset="resetColumnLayout" />

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
import { onMounted, ref } from 'vue'
import { useTableColumnLayout } from '../../composables/useTableColumnLayout'
import { useTableCardHeight } from '../../composables/useTableCardHeight'
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

const cardRef = ref()
const FIXED_ROW_HEIGHT = 44
const { tableHeight } = useTableCardHeight({
  cardRef,
  bottomOffset: () => props.bottomOffset
})

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
})

onMounted(() => {
  loadColumnWidths()
})
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
