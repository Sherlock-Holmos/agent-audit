<template>
  <el-row :gutter="16" class="settings-page">
    <el-col :span="6">
      <el-card shadow="never" class="settings-nav-card">
        <template #header>
          <span>设置导航</span>
        </template>
        <el-tree
          :data="settingTree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :current-node-key="currentNodeId"
          @node-click="handleNodeClick"
        />
      </el-card>
    </el-col>

    <el-col :span="18">
      <el-card shadow="never">
        <template #header>
          <div class="settings-header">
            <span>{{ currentTitle }}</span>
            <el-button type="primary" :loading="saving" @click="saveCurrent">保存设置</el-button>
          </div>
        </template>

        <el-form v-if="currentNodeId === 'basic-audit'" label-width="140px">
          <el-form-item label="系统名称">
            <el-input v-model="forms.basic.systemName" />
          </el-form-item>
          <el-form-item label="默认部门编码">
            <el-input v-model="forms.basic.defaultDeptCode" />
          </el-form-item>
          <el-form-item label="时区">
            <el-select v-model="forms.basic.timezone" style="width: 220px">
              <el-option label="Asia/Shanghai" value="Asia/Shanghai" />
              <el-option label="UTC" value="UTC" />
            </el-select>
          </el-form-item>
        </el-form>

        <el-form v-else-if="currentNodeId === 'basic-appearance'" label-width="140px">
          <el-form-item label="夜间模式">
            <el-radio-group v-model="forms.appearance.themeMode">
              <el-radio value="light">浅色模式</el-radio>
              <el-radio value="dark">深色模式</el-radio>
              <el-radio value="system">跟随系统</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="说明">
            <span class="hint-text">选择“跟随系统”后，会根据操作系统深浅色设置自动切换。</span>
          </el-form-item>
        </el-form>

        <el-form v-else-if="currentNodeId === 'security-auth'" label-width="140px">
          <el-form-item label="登录失败阈值">
            <el-input-number v-model="forms.security.loginFailLimit" :min="3" :max="20" />
          </el-form-item>
          <el-form-item label="会话有效期(分钟)">
            <el-input-number v-model="forms.security.tokenExpireMinutes" :min="10" :max="1440" />
          </el-form-item>
          <el-form-item label="启用单点登录">
            <el-switch v-model="forms.security.enableSso" />
          </el-form-item>
        </el-form>

        <el-form v-else-if="currentNodeId === 'data-source'" label-width="140px">
          <el-form-item label="最大导入文件(MB)">
            <el-input-number v-model="forms.datasource.maxUploadMb" :min="1" :max="500" />
          </el-form-item>
          <el-form-item label="允许文件后缀">
            <el-select v-model="forms.datasource.allowExt" multiple collapse-tags style="width: 360px">
              <el-option label="csv" value="csv" />
              <el-option label="xls" value="xls" />
              <el-option label="xlsx" value="xlsx" />
              <el-option label="json" value="json" />
              <el-option label="txt" value="txt" />
            </el-select>
          </el-form-item>
          <el-form-item label="自动连通性检测">
            <el-switch v-model="forms.datasource.autoHealthCheck" />
          </el-form-item>
        </el-form>

        <el-form v-else-if="currentNodeId === 'data-clean'" label-width="140px">
          <el-form-item label="默认去重策略">
            <el-select v-model="forms.clean.dedupStrategy" style="width: 220px">
              <el-option label="主键优先" value="PRIMARY_KEY" />
              <el-option label="规则优先" value="RULE" />
            </el-select>
          </el-form-item>
          <el-form-item label="异常数据保留天数">
            <el-input-number v-model="forms.clean.errorRetentionDays" :min="1" :max="365" />
          </el-form-item>
          <el-form-item label="清洗任务并发数">
            <el-input-number v-model="forms.clean.maxConcurrentJob" :min="1" :max="20" />
          </el-form-item>
        </el-form>

        <el-empty v-else description="请选择左侧设置项" />
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../store/app'
import { storeToRefs } from 'pinia'

const saving = ref(false)
const currentNodeId = ref('basic-audit')
const appStore = useAppStore()
const { themeMode } = storeToRefs(appStore)

const settingTree = [
  {
    id: 'basic',
    label: '基础设置',
    children: [
      { id: 'basic-audit', label: '系统与审计基础信息' },
      { id: 'basic-appearance', label: '主题与显示' }
    ]
  },
  {
    id: 'security',
    label: '安全设置',
    children: [{ id: 'security-auth', label: '认证与会话策略' }]
  },
  {
    id: 'data',
    label: '数据设置',
    children: [
      { id: 'data-source', label: '数据源接入策略' },
      { id: 'data-clean', label: '数据清洗策略' }
    ]
  }
]

const titleMap = {
  'basic-audit': '系统与审计基础信息',
  'basic-appearance': '主题与显示',
  'security-auth': '认证与会话策略',
  'data-source': '数据源接入策略',
  'data-clean': '数据清洗策略'
}

const forms = reactive({
  basic: {
    systemName: '审计整改智能驾驶舱',
    defaultDeptCode: 'audit-dept-001',
    timezone: 'Asia/Shanghai'
  },
  appearance: {
    themeMode: themeMode.value
  },
  security: {
    loginFailLimit: 5,
    tokenExpireMinutes: 120,
    enableSso: false
  },
  datasource: {
    maxUploadMb: 20,
    allowExt: ['csv', 'xls', 'xlsx', 'json', 'txt'],
    autoHealthCheck: true
  },
  clean: {
    dedupStrategy: 'PRIMARY_KEY',
    errorRetentionDays: 30,
    maxConcurrentJob: 3
  }
})

const currentTitle = computed(() => titleMap[currentNodeId.value] || '设置')

function handleNodeClick(node) {
  if (node.children?.length) return
  currentNodeId.value = node.id
}

async function saveCurrent() {
  saving.value = true
  try {
    if (currentNodeId.value === 'basic-appearance') {
      appStore.setThemeMode(forms.appearance.themeMode)
    }
    await new Promise((resolve) => setTimeout(resolve, 400))
    ElMessage.success(`${currentTitle.value}保存成功`)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.settings-page {
  min-height: calc(100vh - 140px);
}

.settings-nav-card {
  min-height: calc(100vh - 140px);
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hint-text {
  color: #909399;
  font-size: 13px;
}
</style>
