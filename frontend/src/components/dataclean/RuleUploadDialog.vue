<template>
  <el-dialog
    :model-value="modelValue"
    width="760px"
    title="上传清洗规则"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="录入方式" prop="entryMode">
        <el-radio-group v-model="form.entryMode">
          <el-radio value="file">文件上传</el-radio>
          <el-radio value="online">在线编写</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="规则名称" prop="name">
        <el-input v-model="form.name" placeholder="例如：字段映射标准化" />
      </el-form-item>

      <el-form-item v-if="form.entryMode === 'file'" label="规则文件" prop="file">
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

      <el-form-item v-else label="在线规则" prop="onlineContent">
        <el-input
          v-model="form.onlineContent"
          type="textarea"
          :rows="8"
          placeholder="请输入规则内容，如字段映射、标准化表达式或简单规则定义"
        />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="可选" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">上传规则</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  submitting: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref()
const selectedFile = ref(null)

const form = reactive({
  entryMode: 'file',
  name: '',
  onlineContent: '',
  remark: ''
})

const rules = {
  entryMode: [{ required: true, message: '请选择录入方式', trigger: 'change' }],
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  file: [{ validator: validateFile, trigger: 'change' }],
  onlineContent: [{ validator: validateOnlineContent, trigger: 'blur' }]
}

function validateFile(_rule, _value, callback) {
  if (form.entryMode !== 'file') {
    callback()
    return
  }
  if (!selectedFile.value) {
    callback(new Error('请选择规则文件'))
    return
  }
  callback()
}

function validateOnlineContent(_rule, value, callback) {
  if (form.entryMode !== 'online') {
    callback()
    return
  }
  if (!String(value || '').trim()) {
    callback(new Error('请输入在线规则内容'))
    return
  }
  callback()
}

function onFileChange(file) {
  selectedFile.value = file.raw || null
  if (!form.name && file?.name) {
    form.name = String(file.name).replace(/\.[^.]+$/, '')
  }
}

function onFileRemove() {
  selectedFile.value = null
}

function resetForm() {
  form.entryMode = 'file'
  form.name = ''
  form.onlineContent = ''
  form.remark = ''
  selectedFile.value = null
  formRef.value?.clearValidate()
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

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  let fileName = ''
  let content = ''

  if (form.entryMode === 'file') {
    fileName = selectedFile.value?.name || ''
    content = await readFileText(selectedFile.value)
  } else {
    fileName = `${form.name.trim().replace(/\s+/g, '_')}.txt`
    content = form.onlineContent.trim()
  }

  emit('submit', {
    name: form.name.trim(),
    fileName,
    content,
    remark: form.remark.trim()
  })
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      resetForm()
    }
  }
)
</script>
