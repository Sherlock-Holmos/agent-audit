<template>
  <div class="governance-hub">
    <el-card shadow="never">
      <template #header>
        <div class="hub-header">
          <span>数据治理中心</span>
          <el-space>
            <el-button type="primary" @click="go('/datasource/clean-rules/rules')">规则与策略</el-button>
            <el-button @click="go('/datasource/clean-rules/synonyms')">主键同义词</el-button>
            <el-button @click="go('/datasource/clean-rules/nifi-templates')">NiFi 模板</el-button>
            <el-button @click="go('/datasource/clean-rules/layer-stats')">分层统计</el-button>
          </el-space>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="12" :lg="6">
          <el-card class="hub-module" shadow="hover" @click="go('/datasource/clean-rules/rules')">
            <div class="hub-module-title">规则与策略</div>
            <div class="hub-module-desc">管理清洗规则、策略启停与在线编辑。</div>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="12" :md="12" :lg="6">
          <el-card class="hub-module" shadow="hover" @click="go('/datasource/clean-rules/synonyms')">
            <div class="hub-module-title">融合主键同义词</div>
            <div class="hub-module-desc">维护主键字段映射及版本历史。</div>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="12" :md="12" :lg="6">
          <el-card class="hub-module" shadow="hover" @click="go('/datasource/clean-rules/nifi-templates')">
            <div class="hub-module-title">NiFi 模板</div>
            <div class="hub-module-desc">管理模板版本、参数校验与触发测试。</div>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="12" :md="12" :lg="6">
          <el-card class="hub-module" shadow="hover" @click="go('/datasource/clean-rules/layer-stats')">
            <div class="hub-module-title">分层统计</div>
            <div class="hub-module-desc">查看 Bronze/Silver/Gold 落表汇总与明细。</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function go(path) {
  if (router.currentRoute.value.path !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.governance-hub {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hub-module {
  min-height: 132px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.hub-module:hover {
  transform: translateY(-2px);
}

.hub-module-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.hub-module-desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
