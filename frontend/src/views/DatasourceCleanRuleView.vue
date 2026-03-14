<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>清洗规则管理</template>
      <el-form :inline="true" :model="uploadForm" label-width="90px">
        <el-form-item label="录入方式">
          <el-radio-group v-model="entryMode">
            <el-radio value="file">文件上传</el-radio>
            <el-radio value="online">在线编写</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <el-form :inline="true" :model="uploadForm" label-width="90px">
        <el-form-item label="规则名称">
          <el-input v-model="uploadForm.name" placeholder="例如：字段映射标准化" style="width: 220px" />
        </el-form-item>
        <el-form-item v-if="entryMode === 'file'" label="规则文件">
          <el-upload
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept=".json,.txt,.yaml,.yml"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-button>选择文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item v-else label="在线规则">
          <el-input
            v-model="uploadForm.onlineContent"
            type="textarea"
            :rows="5"
            placeholder="请输入规则内容，如字段映射、标准化表达式或简单规则定义"
            style="width: 520px"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" placeholder="可选" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传规则</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-table :data="rules" v-loading="loading" border style="width: 100%">
        <el-table-column prop="name" label="规则名称" min-width="180" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.category === 'SYSTEM' ? 'info' : 'success'">
              {{ scope.row.category === 'SYSTEM' ? '系统' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" min-width="160" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggle(scope.row.id, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button type="primary" link @click="openRuleEditor(scope.row)">在线查看</el-button>
            <el-popconfirm title="确认删除该规则？" @confirm="handleDelete(scope.row)">
              <template #reference>
                <el-button type="danger" link :disabled="scope.row.category === 'SYSTEM'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>清洗策略管理</template>
      <el-form :inline="true" :model="strategyForm" label-width="90px">
        <el-form-item label="策略名称">
          <el-input v-model="strategyForm.name" placeholder="例如：主键冲突优先级合并" style="width: 220px" />
        </el-form-item>
        <el-form-item label="策略编码">
          <el-input v-model="strategyForm.code" placeholder="例如：PK_PRIORITY_MERGE" style="width: 220px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="strategyForm.remark" placeholder="可选" style="width: 180px" />
        </el-form-item>
      </el-form>
      <el-form :model="strategyForm" label-width="90px">
        <el-form-item label="策略内容">
          <el-input
            v-model="strategyForm.content"
            type="textarea"
            :rows="4"
            placeholder="请输入策略内容说明或执行逻辑描述"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creatingStrategy" @click="submitStrategy">新增策略</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="strategies" v-loading="loadingStrategies" border style="width: 100%">
        <el-table-column prop="name" label="策略名称" min-width="180" />
        <el-table-column prop="code" label="策略编码" min-width="180" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.builtIn ? 'info' : 'success'">
              {{ scope.row.builtIn ? '系统' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggleStrategy(scope.row.id, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button type="primary" link @click="openStrategyEditor(scope.row)">在线查看</el-button>
            <el-popconfirm title="确认删除该策略？" @confirm="handleDeleteStrategy(scope.row)">
              <template #reference>
                <el-button type="danger" link :disabled="scope.row.builtIn">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCleanStrategy,
  deleteCleanRule,
  deleteCleanStrategy,
  getCleanRuleDetail,
  getCleanStrategyDetail,
  listCleanRules,
  listCleanStrategies,
  toggleCleanRule,
  toggleCleanStrategy,
  updateCleanRule,
  updateCleanStrategy,
  uploadCleanRule
} from '../api/cleanrule'

const loading = ref(false)
const uploading = ref(false)
const rules = ref([])
const rawFile = ref(null)
const entryMode = ref('file')
const ruleEditorVisible = ref(false)
const updatingRule = ref(false)
const ruleEditorReadonly = ref(false)
const editingRuleId = ref('')
const loadingStrategies = ref(false)
const creatingStrategy = ref(false)
const strategies = ref([])
const strategyEditorVisible = ref(false)
const strategyEditorReadonly = ref(false)
const updatingStrategy = ref(false)
const editingStrategyId = ref('')

const uploadForm = reactive({
  name: '',
  remark: '',
  onlineContent: ''
})

const strategyForm = reactive({
  name: '',
  code: '',
  content: '',
  remark: ''
})

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

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function resetUploadForm() {
  uploadForm.name = ''
  uploadForm.remark = ''
  uploadForm.onlineContent = ''
  rawFile.value = null
}

async function loadRules() {
  loading.value = true
  try {
    const { data } = await listCleanRules()
    rules.value = data.data || []
  } finally {
    loading.value = false
  }
}

async function loadStrategies() {
  loadingStrategies.value = true
  try {
    const { data } = await listCleanStrategies()
    strategies.value = data.data || []
  } finally {
    loadingStrategies.value = false
  }
}

function onFileChange(file) {
  rawFile.value = file.raw || null
  if (!uploadForm.name && file?.name) {
    uploadForm.name = String(file.name).replace(/\.[^.]+$/, '')
  }
}

function onFileRemove() {
  rawFile.value = null
}

async function submitUpload() {
  if (!uploadForm.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }

  uploading.value = true
  try {
    let fileName = ''
    let content = ''

    if (entryMode.value === 'file') {
      if (!rawFile.value) {
        ElMessage.warning('请选择规则文件')
        uploading.value = false
        return
      }
      fileName = rawFile.value.name
      content = await readFileText(rawFile.value)
    } else {
      if (!uploadForm.onlineContent.trim()) {
        ElMessage.warning('请输入在线规则内容')
        uploading.value = false
        return
      }
      fileName = `${uploadForm.name.trim().replace(/\s+/g, '_')}.txt`
      content = uploadForm.onlineContent.trim()
    }

    await uploadCleanRule({
      name: uploadForm.name.trim(),
      fileName,
      content,
      remark: uploadForm.remark.trim()
    })
    ElMessage.success('规则上传成功')
    resetUploadForm()
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '上传失败'))
  } finally {
    uploading.value = false
  }
}

async function handleToggle(id, enabled) {
  try {
    await toggleCleanRule(id, enabled)
    ElMessage.success('规则状态已更新')
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '更新失败'))
  }
}

async function handleDelete(rule) {
  try {
    await deleteCleanRule(rule.id)
    ElMessage.success('删除成功')
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

async function openRuleEditor(rule) {
  try {
    const { data } = await getCleanRuleDetail(rule.id)
    const detail = data.data || {}
    editingRuleId.value = String(detail.id || '')
    ruleEditorForm.name = detail.name || ''
    ruleEditorForm.fileName = detail.fileName || ''
    ruleEditorForm.content = detail.content || ''
    ruleEditorForm.remark = detail.remark || ''
    ruleEditorReadonly.value = detail.category === 'SYSTEM'
    ruleEditorVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '获取规则详情失败'))
  }
}

async function saveRuleEditor() {
  if (!editingRuleId.value) return
  if (!ruleEditorForm.name.trim() || !ruleEditorForm.fileName.trim() || !ruleEditorForm.content.trim()) {
    ElMessage.warning('规则名称、规则文件和规则内容不能为空')
    return
  }

  updatingRule.value = true
  try {
    await updateCleanRule(editingRuleId.value, {
      name: ruleEditorForm.name.trim(),
      fileName: ruleEditorForm.fileName.trim(),
      content: ruleEditorForm.content,
      remark: ruleEditorForm.remark.trim()
    })
    ElMessage.success('规则已更新')
    ruleEditorVisible.value = false
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '规则更新失败'))
  } finally {
    updatingRule.value = false
  }
}

async function submitStrategy() {
  if (!strategyForm.name.trim() || !strategyForm.code.trim()) {
    ElMessage.warning('请输入策略名称和编码')
    return
  }

  creatingStrategy.value = true
  try {
    await createCleanStrategy({
      name: strategyForm.name.trim(),
      code: strategyForm.code.trim().toUpperCase(),
      content: strategyForm.content,
      remark: strategyForm.remark.trim()
    })
    ElMessage.success('策略新增成功')
    strategyForm.name = ''
    strategyForm.code = ''
    strategyForm.content = ''
    strategyForm.remark = ''
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '新增失败'))
  } finally {
    creatingStrategy.value = false
  }
}

async function handleToggleStrategy(id, enabled) {
  try {
    await toggleCleanStrategy(id, enabled)
    ElMessage.success('策略状态已更新')
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '更新失败'))
  }
}

async function handleDeleteStrategy(strategy) {
  try {
    await deleteCleanStrategy(strategy.id)
    ElMessage.success('删除成功')
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

async function openStrategyEditor(strategy) {
  try {
    const { data } = await getCleanStrategyDetail(strategy.id)
    const detail = data.data || {}
    editingStrategyId.value = String(detail.id || '')
    strategyEditorForm.name = detail.name || ''
    strategyEditorForm.code = detail.code || ''
    strategyEditorForm.content = detail.content || ''
    strategyEditorForm.remark = detail.remark || ''
    strategyEditorReadonly.value = !!detail.builtIn
    strategyEditorVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '获取策略详情失败'))
  }
}

async function saveStrategyEditor() {
  if (!editingStrategyId.value) return
  if (!strategyEditorForm.name.trim() || !strategyEditorForm.code.trim()) {
    ElMessage.warning('策略名称和编码不能为空')
    return
  }

  updatingStrategy.value = true
  try {
    await updateCleanStrategy(editingStrategyId.value, {
      name: strategyEditorForm.name.trim(),
      code: strategyEditorForm.code.trim().toUpperCase(),
      content: strategyEditorForm.content,
      remark: strategyEditorForm.remark.trim()
    })
    ElMessage.success('策略已更新')
    strategyEditorVisible.value = false
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '策略更新失败'))
  } finally {
    updatingStrategy.value = false
  }
}

function readFileText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('读取规则文件失败'))
    reader.readAsText(file, 'utf-8')
  })
}

onMounted(async () => {
  await loadRules()
  await loadStrategies()
})
</script>
