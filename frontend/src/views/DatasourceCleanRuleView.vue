<template>
  <GovernancePageShell>

    <el-card shadow="never">
      <template #header>
        <div class="hub-header">
          <span>数据治理中心</span>
          <span class="hub-subtitle">统一入口，按能力域展开配置与运维</span>
        </div>
      </template>

      <el-collapse v-model="activeNames" class="hub-collapse">
        <el-collapse-item name="rules">
          <template #title>
            <div class="collapse-title-wrap">
              <div class="collapse-title">规则与策略</div>
              <div class="collapse-desc">管理清洗规则、策略启停、在线编辑与内容维护。</div>
            </div>
          </template>
          <div class="collapse-body">
            <el-space wrap>
              <el-tag type="success">规则上传</el-tag>
              <el-tag type="warning">启停控制</el-tag>
              <el-tag>策略编排</el-tag>
            </el-space>
            <el-button type="primary" size="large" @click="go('/datasource/clean-rules/rules')">进入规则与策略</el-button>
          </div>
        </el-collapse-item>

        <el-collapse-item name="synonyms">
          <template #title>
            <div class="collapse-title-wrap">
              <div class="collapse-title">融合主键同义词</div>
              <div class="collapse-desc">维护标准主键、同义字段及历史版本追溯。</div>
            </div>
          </template>
          <div class="collapse-body">
            <el-space wrap>
              <el-tag type="info">映射维护</el-tag>
              <el-tag type="warning">历史查询</el-tag>
              <el-tag>版本对比</el-tag>
            </el-space>
            <el-button type="primary" size="large" @click="go('/datasource/clean-rules/synonyms')">进入主键同义词</el-button>
          </div>
        </el-collapse-item>

        <el-collapse-item name="nifi">
          <template #title>
            <div class="collapse-title-wrap">
              <div class="collapse-title">NiFi 模板</div>
              <div class="collapse-desc">模板版本管理、必填参数校验与触发测试。</div>
            </div>
          </template>
          <div class="collapse-body">
            <el-space wrap>
              <el-tag type="success">模板管理</el-tag>
              <el-tag type="warning">参数规则</el-tag>
              <el-tag>触发验证</el-tag>
            </el-space>
            <el-button type="primary" size="large" @click="go('/datasource/clean-rules/nifi-templates')">进入 NiFi 模板</el-button>
          </div>
        </el-collapse-item>

        <el-collapse-item name="layer-stats">
          <template #title>
            <div class="collapse-title-wrap">
              <div class="collapse-title">分层统计</div>
              <div class="collapse-desc">查看 Bronze/Silver/Gold 数据落表汇总与任务明细。</div>
            </div>
          </template>
          <div class="collapse-body">
            <el-space wrap>
              <el-tag type="success">任务汇总</el-tag>
              <el-tag type="warning">分层行数</el-tag>
              <el-tag>筛选查询</el-tag>
            </el-space>
            <el-button type="primary" size="large" @click="go('/datasource/clean-rules/layer-stats')">进入分层统计</el-button>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </GovernancePageShell>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import GovernancePageShell from '../components/dataclean/GovernancePageShell.vue'

const router = useRouter()
const activeNames = ref(['rules'])

function go(path) {
  if (router.currentRoute.value.path !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.hub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hub-subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hub-collapse {
  border-top: none;
}

.collapse-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 0;
}

.collapse-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.2;
}

.collapse-desc {
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.collapse-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0 10px;
}

@media (max-width: 768px) {
  .hub-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .collapse-body {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
