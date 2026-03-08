<template>
  <el-card shadow="never">
    <el-table :data="data" v-loading="loading" border style="width: 100%">
      <el-table-column label="数据源名称" min-width="180">
        <template #default="scope">
          {{ formatText(scope.row.name, scope.row, 'name') }}
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.type === 'DATABASE' ? 'primary' : 'success'">
            {{ scope.row.type === 'DATABASE' ? '数据库' : '本地文件' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="连接信息" min-width="280">
        <template #default="scope">
          <span v-if="scope.row.type === 'DATABASE'">
            {{ scope.row.dbType }} / {{ scope.row.host }}:{{ scope.row.port }} / {{ scope.row.databaseName }}
          </span>
          <span v-else>
            {{ formatText(scope.row.fileName, scope.row, 'fileName') }} ({{ formatSize(scope.row.fileSize || 0) }})
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="scope">
          <el-switch
            :model-value="scope.row.status === 'ENABLED'"
            @change="(val) => $emit('status-change', { id: scope.row.id, status: val ? 'ENABLED' : 'DISABLED' })"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="scope">
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

defineEmits(['status-change', 'delete'])

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
.empty-tip {
  margin-top: 14px;
  text-align: center;
  color: #909399;
}
</style>
