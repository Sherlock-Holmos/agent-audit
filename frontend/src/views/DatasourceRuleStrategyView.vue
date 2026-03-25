<template>
  <GovernancePageShell>

    <GovernanceCardSection title="清洗规则管理" card-style="margin-bottom: 16px">
      <template #actions>
          <el-button type="primary" @click="ruleUploadVisible = true">上传规则</el-button>
      </template>
      <GovernanceTable :data="rules" :loading="loading" layout-storage-key="governance-clean-rules-table">
        <template #default="{ resolveWidth, resolveMinWidth }">
          <el-table-column column-key="name" prop="name" label="规则名称" :width="resolveWidth('name', 180)" :min-width="resolveMinWidth('name', 180)" />
          <el-table-column column-key="category" label="类型" :width="resolveWidth('category', 100)" :min-width="resolveMinWidth('category', 100)" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.category === 'SYSTEM' ? 'info' : 'success'">
                {{ scope.row.category === 'SYSTEM' ? '系统' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column column-key="fileName" prop="fileName" label="文件" :width="resolveWidth('fileName', 160)" :min-width="resolveMinWidth('fileName', 160)" />
          <el-table-column column-key="updatedAt" prop="updatedAt" label="更新时间" :width="resolveWidth('updatedAt', 180)" :min-width="resolveMinWidth('updatedAt', 180)" />
          <el-table-column column-key="enabled" label="启用" :width="resolveWidth('enabled', 90)" :min-width="resolveMinWidth('enabled', 90)" align="center">
            <template #default="scope">
              <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggle(scope.row.id, val)" />
            </template>
          </el-table-column>
          <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions', 180)" :min-width="resolveMinWidth('actions', 180)" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openRuleEditor(scope.row)">在线查看</el-button>
              <el-popconfirm title="确认删除该规则？" @confirm="handleDelete(scope.row)">
                <template #reference>
                  <el-button type="danger" link :disabled="scope.row.category === 'SYSTEM'">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </template>
      </GovernanceTable>
    </GovernanceCardSection>

    <el-dialog v-model="ruleEditorVisible" width="760px" title="规则在线查看与编辑" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleEditorForm.name" :disabled="ruleEditorReadonly" />
        </el-form-item>
        <el-form-item label="规则文件">
          <el-input v-model="ruleEditorForm.fileName" :disabled="ruleEditorReadonly" />
        </el-form-item>
        <el-form-item label="规则内容">
          <el-input
            v-model="ruleEditorForm.content"
            type="textarea"
            :rows="10"
            :disabled="ruleEditorReadonly"
            placeholder="支持 DSL 行式规则或 JSON 规则内容"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleEditorForm.remark" :disabled="ruleEditorReadonly" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleEditorVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="updatingRule"
          :disabled="ruleEditorReadonly"
          @click="saveRuleEditor"
        >保存修改</el-button>
      </template>
    </el-dialog>

    <GovernanceCardSection title="清洗策略管理">
      <template #actions>
          <el-button type="primary" @click="strategyUploadVisible = true">新增策略</el-button>
      </template>
      <GovernanceTable :data="strategies" :loading="loadingStrategies" layout-storage-key="governance-clean-strategy-table">
        <template #default="{ resolveWidth, resolveMinWidth }">
          <el-table-column column-key="name" prop="name" label="策略名称" :width="resolveWidth('name', 180)" :min-width="resolveMinWidth('name', 180)" />
          <el-table-column column-key="code" prop="code" label="策略编码" :width="resolveWidth('code', 180)" :min-width="resolveMinWidth('code', 180)" />
          <el-table-column column-key="builtIn" label="类型" :width="resolveWidth('builtIn', 100)" :min-width="resolveMinWidth('builtIn', 100)" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.builtIn ? 'info' : 'success'">
                {{ scope.row.builtIn ? '系统' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column column-key="updatedAt" prop="updatedAt" label="更新时间" :width="resolveWidth('updatedAt', 180)" :min-width="resolveMinWidth('updatedAt', 180)" />
          <el-table-column column-key="enabled" label="启用" :width="resolveWidth('enabled', 90)" :min-width="resolveMinWidth('enabled', 90)" align="center">
            <template #default="scope">
              <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggleStrategy(scope.row.id, val)" />
            </template>
          </el-table-column>
          <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions', 180)" :min-width="resolveMinWidth('actions', 180)" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openStrategyEditor(scope.row)">在线查看</el-button>
              <el-popconfirm title="确认删除该策略？" @confirm="handleDeleteStrategy(scope.row)">
                <template #reference>
                  <el-button type="danger" link :disabled="scope.row.builtIn">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </template>
      </GovernanceTable>
    </GovernanceCardSection>

    <el-dialog v-model="strategyEditorVisible" width="760px" title="策略在线查看与编辑" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="策略名称">
          <el-input v-model="strategyEditorForm.name" :disabled="strategyEditorReadonly" />
        </el-form-item>
        <el-form-item label="策略编码">
          <el-input v-model="strategyEditorForm.code" :disabled="strategyEditorReadonly" />
        </el-form-item>
        <el-form-item label="策略内容">
          <el-input
            v-model="strategyEditorForm.content"
            type="textarea"
            :rows="10"
            :disabled="strategyEditorReadonly"
            placeholder="可写入策略执行说明或逻辑内容"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="strategyEditorForm.remark" :disabled="strategyEditorReadonly" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="strategyEditorVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="updatingStrategy"
          :disabled="strategyEditorReadonly"
          @click="saveStrategyEditor"
        >保存修改</el-button>
      </template>
    </el-dialog>

    <RuleUploadDialog
      v-model="ruleUploadVisible"
      :submitting="uploading"
      @submit="handleRuleUpload"
    />

    <StrategyUploadDialog
      v-model="strategyUploadVisible"
      :submitting="creatingStrategy"
      @submit="handleStrategyUpload"
    />
  </GovernancePageShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import GovernancePageShell from '../components/dataclean/GovernancePageShell.vue'
import GovernanceCardSection from '../components/dataclean/GovernanceCardSection.vue'
import GovernanceTable from '../components/dataclean/GovernanceTable.vue'
import RuleUploadDialog from '../components/dataclean/RuleUploadDialog.vue'
import StrategyUploadDialog from '../components/dataclean/StrategyUploadDialog.vue'
import { useAsyncTask } from '../composables/useAsyncTask'
import {
  deleteCleanRule,
  getCleanRuleDetail,
  listCleanRules,
  toggleCleanRule,
  updateCleanRule,
  uploadCleanRule
} from '../api/clean-rule'
import {
  createCleanStrategy,
  deleteCleanStrategy,
  getCleanStrategyDetail,
  listCleanStrategies,
  toggleCleanStrategy,
  updateCleanStrategy
} from '../api/clean-strategy'

const { loading, run: runLoadRules } = useAsyncTask()
const { loading: uploading, run: runUploadRule } = useAsyncTask()
const { loading: updatingRule, run: runUpdateRule } = useAsyncTask()
const { loading: loadingStrategies, run: runLoadStrategies } = useAsyncTask()
const { loading: creatingStrategy, run: runCreateStrategy } = useAsyncTask()
const { loading: updatingStrategy, run: runUpdateStrategy } = useAsyncTask()
const { run: runRuleOperation } = useAsyncTask()
const { run: runStrategyOperation } = useAsyncTask()

const rules = ref([])
const ruleUploadVisible = ref(false)
const ruleEditorVisible = ref(false)
const ruleEditorReadonly = ref(false)
const editingRuleId = ref('')

const strategyUploadVisible = ref(false)
const strategies = ref([])
const strategyEditorVisible = ref(false)
const strategyEditorReadonly = ref(false)
const editingStrategyId = ref('')

const ruleEditorForm = reactive({
  name: '',
  fileName: '',
  content: '',
  remark: ''
})

const strategyEditorForm = reactive({
  name: '',
  code: '',
  content: '',
  remark: ''
})

async function loadRules() {
  const result = await runLoadRules(() => listCleanRules(), {
    errorMessage: '加载清洗规则失败',
    onError: () => {
      rules.value = []
    }
  })
  if (result) {
    rules.value = result.data?.data || []
  }
}

async function loadStrategies() {
  const result = await runLoadStrategies(() => listCleanStrategies(), {
    errorMessage: '加载清洗策略失败',
    onError: () => {
      strategies.value = []
    }
  })
  if (result) {
    strategies.value = result.data?.data || []
  }
}

async function handleRuleUpload(payload) {
  const result = await runUploadRule(() => uploadCleanRule(payload), {
    errorMessage: '上传失败',
    successMessage: '规则上传成功'
  })
  if (result) {
    ruleUploadVisible.value = false
    await loadRules()
  }
}

async function handleToggle(id, enabled) {
  const result = await runRuleOperation(() => toggleCleanRule(id, enabled), {
    errorMessage: '更新失败',
    successMessage: '规则状态已更新'
  })
  if (result) {
    await loadRules()
  }
}

async function handleDelete(rule) {
  const result = await runRuleOperation(() => deleteCleanRule(rule.id), {
    errorMessage: '删除失败',
    successMessage: '删除成功'
  })
  if (result) {
    await loadRules()
  }
}

async function openRuleEditor(rule) {
  const result = await runRuleOperation(() => getCleanRuleDetail(rule.id), {
    errorMessage: '获取规则详情失败'
  })
  if (result) {
    const detail = result.data?.data || {}
    editingRuleId.value = String(detail.id || '')
    ruleEditorForm.name = detail.name || ''
    ruleEditorForm.fileName = detail.fileName || ''
    ruleEditorForm.content = detail.content || ''
    ruleEditorForm.remark = detail.remark || ''
    ruleEditorReadonly.value = detail.category === 'SYSTEM'
    ruleEditorVisible.value = true
  }
}

async function saveRuleEditor() {
  if (!editingRuleId.value) return
  if (!ruleEditorForm.name.trim() || !ruleEditorForm.fileName.trim() || !ruleEditorForm.content.trim()) {
    ElMessage.warning('规则名称、规则文件和规则内容不能为空')
    return
  }

  const result = await runUpdateRule(() => updateCleanRule(editingRuleId.value, {
      name: ruleEditorForm.name.trim(),
      fileName: ruleEditorForm.fileName.trim(),
      content: ruleEditorForm.content,
      remark: ruleEditorForm.remark.trim()
    }), {
    errorMessage: '规则更新失败',
    successMessage: '规则已更新'
  })
  if (result) {
    ruleEditorVisible.value = false
    await loadRules()
  }
}

async function handleStrategyUpload(payload) {
  const result = await runCreateStrategy(() => createCleanStrategy(payload), {
    errorMessage: '新增失败',
    successMessage: '策略新增成功'
  })
  if (result) {
    strategyUploadVisible.value = false
    await loadStrategies()
  }
}

async function handleToggleStrategy(id, enabled) {
  const result = await runStrategyOperation(() => toggleCleanStrategy(id, enabled), {
    errorMessage: '更新失败',
    successMessage: '策略状态已更新'
  })
  if (result) {
    await loadStrategies()
  }
}

async function handleDeleteStrategy(strategy) {
  const result = await runStrategyOperation(() => deleteCleanStrategy(strategy.id), {
    errorMessage: '删除失败',
    successMessage: '删除成功'
  })
  if (result) {
    await loadStrategies()
  }
}

async function openStrategyEditor(strategy) {
  const result = await runStrategyOperation(() => getCleanStrategyDetail(strategy.id), {
    errorMessage: '获取策略详情失败'
  })
  if (result) {
    const detail = result.data?.data || {}
    editingStrategyId.value = String(detail.id || '')
    strategyEditorForm.name = detail.name || ''
    strategyEditorForm.code = detail.code || ''
    strategyEditorForm.content = detail.content || ''
    strategyEditorForm.remark = detail.remark || ''
    strategyEditorReadonly.value = !!detail.builtIn
    strategyEditorVisible.value = true
  }
}

async function saveStrategyEditor() {
  if (!editingStrategyId.value) return
  if (!strategyEditorForm.name.trim() || !strategyEditorForm.code.trim()) {
    ElMessage.warning('策略名称和编码不能为空')
    return
  }

  const result = await runUpdateStrategy(() => updateCleanStrategy(editingStrategyId.value, {
      name: strategyEditorForm.name.trim(),
      code: strategyEditorForm.code.trim().toUpperCase(),
      content: strategyEditorForm.content,
      remark: strategyEditorForm.remark.trim()
    }), {
    errorMessage: '策略更新失败',
    successMessage: '策略已更新'
  })
  if (result) {
    strategyEditorVisible.value = false
    await loadStrategies()
  }
}

onMounted(async () => {
  await Promise.allSettled([loadRules(), loadStrategies()])
})
</script>
