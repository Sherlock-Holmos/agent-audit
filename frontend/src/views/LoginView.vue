<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">登录审计智能驾驶舱</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="auth-btn" @click="handleLogin">登录</el-button>
        <div class="auth-link-row">
          <span>还没有账号？</span>
          <el-link type="primary" @click="goRegister">去注册</el-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { loginApi } from '../api/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const { data } = await loginApi(form)
    localStorage.setItem('token', data.data.token)
    localStorage.setItem('user', JSON.stringify(data.data.user))
    ElMessage.success('登录成功')
    router.replace('/')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const goRegister = () => {
  router.push('/register')
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
