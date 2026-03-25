<template>
  <el-card shadow="never" class="governance-subnav-card">
    <div class="governance-subnav">
      <div class="governance-subnav-title">数据治理</div>
      <el-space wrap>
        <el-button
          v-for="item in items"
          :key="item.path"
          :type="isActive(item.path) ? 'primary' : 'default'"
          @click="go(item.path)"
        >
          {{ item.label }}
        </el-button>
      </el-space>
    </div>
  </el-card>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const items = [
  { label: '规则与策略', path: '/datasource/clean-rules/rules' },
  { label: '主键同义词', path: '/datasource/clean-rules/synonyms' },
  { label: 'NiFi 模板', path: '/datasource/clean-rules/nifi-templates' },
  { label: '分层统计', path: '/datasource/clean-rules/layer-stats' }
]

function isActive(path) {
  return route.path === path
}

function go(path) {
  if (route.path !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.governance-subnav-card {
  margin-bottom: 12px;
}

.governance-subnav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.governance-subnav-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

@media (max-width: 768px) {
  .governance-subnav {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
