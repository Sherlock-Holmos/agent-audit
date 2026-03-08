<template>
  <el-card shadow="never">
    <el-table :data="data" v-loading="loading" border style="width: 100%">
      <el-table-column prop="taskName" label="任务名称" min-width="180" />
      <el-table-column label="清洗对象" min-width="260">
        <template #default="scope">
          {{ (scope.row.cleanObjectNames || []).join('；') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="应用规则" min-width="180">
        <template #default="scope">
          {{ (scope.row.cleanRuleNames || []).join('、') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="清洗策略" width="160">
        <template #default="scope">
          {{ scope.row.strategyName || scope.row.strategy || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="standardTable" label="标准化表" width="180" />
      <el-table-column prop="cleanedRows" label="清洗数据量" width="120" align="right" />
      <el-table-column label="状态" width="120" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : 'info'">
            {{ scope.row.status === 'COMPLETED' ? '已完成' : '待执行' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="scope">
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

    <div v-if="!data.length && !loading" class="empty-tip">暂无清洗任务，请点击“新建清洗任务”。</div>
  </el-card>
</template>

<script setup>
defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['run', 'delete'])
</script>

<style scoped>
.empty-tip {
  margin-top: 14px;
  text-align: center;
  color: #909399;
}
</style>
