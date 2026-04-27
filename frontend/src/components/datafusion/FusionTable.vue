<template>
  <AppDataTable
    :data="data"
    :loading="loading"
    :table-size="tableSize"
    :layout-storage-key="layoutStorageKey"
    :min-by-key="minByKey"
    :empty-text="emptyText"
  >
    <template #default="{ resolveWidth, resolveMinWidth }">
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
          <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : (scope.row.status === 'FAILED' ? 'danger' : (scope.row.status === 'RUNNING' ? 'warning' : 'info'))">
            {{ scope.row.status === 'COMPLETED' ? '已完成' : (scope.row.status === 'FAILED' ? '失败' : (scope.row.status === 'RUNNING' ? '运行中' : '待执行')) }}
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
          <el-button type="primary" link :disabled="scope.row.status === 'COMPLETED' || scope.row.status === 'RUNNING'" @click="$emit('run', scope.row.id)">
            执行
          </el-button>
          <el-popconfirm title="确认删除该融合任务？" @confirm="$emit('delete', scope.row.id)">
            <template #reference>
              <el-button type="danger" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </template>
  </AppDataTable>
</template>

<script setup>
import AppDataTable from '../shared/AppDataTable.vue'

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
    default: 'app:table-layout:datafusion:table'
  },
  bottomOffset: {
    type: Number,
    default: 8
  },
  emptyText: {
    type: String,
    default: ''
  }
})

defineEmits(['preview', 'run', 'delete', 'edit'])

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
</script>
