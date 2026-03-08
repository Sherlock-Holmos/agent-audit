<template>
  <el-dialog
    :model-value="modelValue"
    width="760px"
    title="清洗规则管理"
    destroy-on-close
    @close="handleClose"
  >
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :inline="true" :model="uploadForm" label-width="90px">
        <el-form-item label="规则名称">
          <el-input v-model="uploadForm.name" placeholder="例如：字段映射标准化" style="width: 220px" />
        </el-form-item>
        <el-form-item label="规则文件">
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
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" placeholder="可选" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传规则</el-button>
        </el-form-item>
      </el-form>
    </el-card>

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
      <el-table-column label="操作" width="90" align="center">
        <template #default="scope">
          <el-popconfirm title="确认删除该规则？" @confirm="handleDelete(scope.row)">
            <template #reference>
              <el-button type="danger" link :disabled="scope.row.category === 'SYSTEM'">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-divider />

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>清洗策略管理</template>
      <el-form :inline="true" :model="strategyForm" label-width="90px">
        <el-form-item label="策略名称">
          <el-input v-model="strategyForm.name" placeholder="例如：主键冲突优先级合并" style="width: 220px" />
        </el-form-item>
        <el-form-item label="策略编码">
          <el-input v-model="strategyForm.code" placeholder="例如：PK_PRIORITY_MERGE" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creatingStrategy" @click="submitStrategy">新增策略</el-button>
        </el-form-item>
      </el-form>
    </el-card>

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
      <el-table-column label="操作" width="90" align="center">
        <template #default="scope">
          <el-popconfirm title="确认删除该策略？" @confirm="handleDeleteStrategy(scope.row)">
            <template #reference>
              <el-button type="danger" link :disabled="scope.row.builtIn">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCleanStrategy,
  deleteCleanRule,
  deleteCleanStrategy,
  listCleanRules,
  listCleanStrategies,
  toggleCleanRule,
  toggleCleanStrategy,
  uploadCleanRule
} from '../../api/cleanrule'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'updated'])

const loading = ref(false)
const uploading = ref(false)
const rules = ref([])
const rawFile = ref(null)
const loadingStrategies = ref(false)
const creatingStrategy = ref(false)
const strategies = ref([])

const uploadForm = reactive({
  name: '',
  remark: ''
})

const strategyForm = reactive({
  name: '',
  code: ''
})

function resetUploadForm() {
  uploadForm.name = ''
  uploadForm.remark = ''
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
  if (!rawFile.value) {
    ElMessage.warning('请选择规则文件')
    return
  }

  uploading.value = true
  try {
    const content = await readFileText(rawFile.value)
    await uploadCleanRule({
      name: uploadForm.name.trim(),
      fileName: rawFile.value.name,
      content,
      remark: uploadForm.remark.trim()
    })
    ElMessage.success('规则上传成功')
    resetUploadForm()
    await loadRules()
    emit('updated')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleToggle(id, enabled) {
  try {
    await toggleCleanRule(id, enabled)
    ElMessage.success('规则状态已更新')
    await loadRules()
    emit('updated')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '更新失败')
  }
}

async function handleDelete(rule) {
  try {
    await deleteCleanRule(rule.id)
    ElMessage.success('删除成功')
    await loadRules()
    emit('updated')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '删除失败')
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
      code: strategyForm.code.trim().toUpperCase()
    })
    ElMessage.success('策略新增成功')
    strategyForm.name = ''
    strategyForm.code = ''
    await loadStrategies()
    emit('updated')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '新增失败')
  } finally {
    creatingStrategy.value = false
  }
}

async function handleToggleStrategy(id, enabled) {
  try {
    await toggleCleanStrategy(id, enabled)
    ElMessage.success('策略状态已更新')
    await loadStrategies()
    emit('updated')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '更新失败')
  }
}

async function handleDeleteStrategy(strategy) {
  try {
    await deleteCleanStrategy(strategy.id)
    ElMessage.success('删除成功')
    await loadStrategies()
    emit('updated')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '删除失败')
  }
}

function handleClose() {
  emit('update:modelValue', false)
}

function readFileText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('读取规则文件失败'))
    reader.readAsText(file, 'utf-8')
  })
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (visible) {
      await loadRules()
      await loadStrategies()
      return
    }
    resetUploadForm()
    strategyForm.name = ''
    strategyForm.code = ''
  }
)
</script>
