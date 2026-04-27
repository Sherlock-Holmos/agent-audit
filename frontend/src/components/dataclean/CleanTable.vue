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
      <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions', 260)" align="center" fixed="right">
        <template #default="scope">
          <el-button type="primary" link :disabled="scope.row.status !== 'COMPLETED'" @click="$emit('preview', scope.row)">
            预览
          </el-button>
          <el-button type="primary" link :disabled="scope.row.status === 'COMPLETED' || scope.row.status === 'RUNNING'" @click="$emit('edit', scope.row)">
            编辑
          </el-button>
          <el-button type="primary" link :disabled="scope.row.status === 'COMPLETED' || scope.row.status === 'RUNNING'" @click="$emit('run', scope.row.id)">
            执行
          </el-button>
          <el-popconfirm title="确认删除该清洗任务？" @confirm="$emit('delete', scope.row.id)">
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
    default: 'app:table-layout:dataclean:table'
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

defineEmits(['run', 'delete', 'edit', 'preview'])

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
</script>
