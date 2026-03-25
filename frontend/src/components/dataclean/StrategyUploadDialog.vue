<template>
  <el-dialog
    :model-value="modelValue"
    width="760px"
    title="新增清洗策略"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="策略名称" prop="name">
        <el-input v-model="form.name" placeholder="例如：主键冲突优先级合并" />
      </el-form-item>
      <el-form-item label="策略编码" prop="code">
        <el-input v-model="form.code" placeholder="例如：PK_PRIORITY_MERGE" />
      </el-form-item>
      <el-form-item label="策略内容">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="6"
          placeholder="请输入策略内容说明或执行逻辑描述"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="可选" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">新增策略</el-button>
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
const form = reactive({
  name: '',
  code: '',
  content: '',
  remark: ''
})

const rules = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入策略编码', trigger: 'blur' }]
}

function resetForm() {
  form.name = ''
  form.code = ''
  form.content = ''
  form.remark = ''
  formRef.value?.clearValidate()
}

function handleClose() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  emit('submit', {
    name: form.name.trim(),
    code: form.code.trim().toUpperCase(),
    content: form.content,
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
