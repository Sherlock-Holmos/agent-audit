<template>
  <el-dialog
    :model-value="modelValue"
    title="新增审计问题"
    width="700px"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="问题标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入问题标题" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="严重等级" prop="level">
            <el-select v-model="form.level" style="width: 100%">
              <el-option label="低" value="低" />
              <el-option label="中" value="中" />
              <el-option label="高" value="高" />
              <el-option label="重大" value="重大" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="被审单位" prop="unit">
            <el-select v-model="form.unit" filterable placeholder="请选择被审单位" style="width: 100%">
              <el-option v-for="item in unitOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="问题描述">
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="相关证据">
        <el-input
          v-model="form.evidenceText"
          type="textarea"
          :rows="2"
          placeholder="可填写文件名/链接，多个请用逗号分隔"
        />
      </el-form-item>
      <el-form-item label="制度/标准条款">
        <el-input
          v-model="form.regulationClause"
          type="textarea"
          :rows="3"
          placeholder="请填写涉及的制度或标准条款"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交问题</el-button>
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
  unitOptions: {
    type: Array,
    default: () => []
  },
  submitting: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref()

const form = reactive({
  title: '',
  level: '中',
  unit: '',
  description: '',
  evidenceText: '',
  regulationClause: ''
})

const rules = {
  title: [{ required: true, message: '请输入问题标题', trigger: 'blur' }],
  unit: [{ required: true, message: '请选择被审单位', trigger: 'change' }],
  level: [{ required: true, message: '请选择严重等级', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      resetForm()
      return
    }
    form.unit = props.unitOptions[0] || ''
  }
)

watch(
  () => props.unitOptions,
  (nextOptions) => {
    if (!props.modelValue || form.unit) {
      return
    }
    form.unit = nextOptions[0] || ''
  }
)

function handleClose() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const evidenceList = form.evidenceText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

  emit('submit', {
    title: form.title.trim(),
    level: form.level,
    unit: form.unit,
    description: form.description,
    evidenceList,
    regulationClause: form.regulationClause
  })
}

function resetForm() {
  form.title = ''
  form.level = '中'
  form.unit = ''
  form.description = ''
  form.evidenceText = ''
  form.regulationClause = ''
  formRef.value?.clearValidate()
}
</script>
