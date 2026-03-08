<template>
  <el-dialog
    :model-value="modelValue"
    width="620px"
    title="个人信息"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="头像">
        <el-avatar :size="64" :src="avatarPreview" style="cursor: pointer" @click="openFilePicker" />
        <input
          ref="fileInputRef"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          style="display: none"
          @change="onAvatarChange"
        />
      </el-form-item>
      <el-form-item label="用户名">
        <el-input :model-value="user?.username || ''" disabled />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="form.nickname" placeholder="请输入昵称" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" placeholder="请输入手机号" />
      </el-form-item>
      <el-form-item label="部门">
        <el-input v-model="form.department" placeholder="请输入部门" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  user: {
    type: Object,
    default: () => ({})
  },
  submitting: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref()
const fileInputRef = ref()
const form = reactive({
  nickname: '',
  avatarUrl: '',
  email: '',
  phone: '',
  department: ''
})

const rules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [
    {
      type: 'email',
      message: '邮箱格式不正确',
      trigger: 'blur'
    }
  ]
}

const avatarPreview = computed(() => {
  if (form.avatarUrl) return form.avatarUrl
  const seed = encodeURIComponent(props.user?.username || form.nickname || 'user')
  return `https://api.dicebear.com/7.x/identicon/svg?seed=${seed}`
})

watch(
  () => [props.modelValue, props.user],
  ([visible]) => {
    if (!visible) return
    form.nickname = props.user?.nickname || props.user?.username || ''
    form.avatarUrl = props.user?.avatarUrl || ''
    form.email = props.user?.email || ''
    form.phone = props.user?.phone || ''
    form.department = props.user?.department || ''
  },
  { immediate: true, deep: true }
)

function handleClose() {
  emit('update:modelValue', false)
}

function openFilePicker() {
  fileInputRef.value?.click()
}

function onAvatarChange(file) {
  const raw = file?.target?.files?.[0]
  if (!raw) return
  if (raw.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过2MB')
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    form.avatarUrl = String(reader.result || '')
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
  reader.readAsDataURL(raw)
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form })
}
</script>
