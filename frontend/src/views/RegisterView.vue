<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">注册账号</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @keyup.enter="handleRegister">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="auth-btn" @click="handleRegister">注册</el-button>
        <div class="auth-link-row">
          <span>已有账号？</span>
          <el-link type="primary" @click="goLogin">去登录</el-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { registerApi } from '../api/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请输入确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const handleRegister = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await registerApi({ username: form.username, password: form.password })
    ElMessage.success('注册成功，请登录')
    router.replace('/login')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}

const goLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.auth-card {
  width: 420px;
  max-width: calc(100vw - 32px);
}
.auth-title {
  text-align: center;
  margin: 0 0 20px;
}
.auth-btn {
  width: 100%;
}
.auth-link-row {
  margin-top: 12px;
  text-align: center;
}
</style>
