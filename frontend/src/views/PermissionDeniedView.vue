<template>
  <el-card class="forbidden-card">
    <template #header>
      <div class="title">无访问权限</div>
    </template>
    <p>当前账号无权访问该页面，请联系审计管理员调整角色授权。</p>
    <el-button type="primary" @click="goHome">返回工作台</el-button>
  </el-card>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ROLE_HOME_ROUTE } from '../constants/rbac'
import { getCurrentRole } from '../utils/currentUser'

const router = useRouter()

function goHome() {
  router.replace(ROLE_HOME_ROUTE[getCurrentRole()] || '/workbench/auditor')
}
</script>

<style scoped>
.forbidden-card {
  max-width: 560px;
  margin: 40px auto;
}

.title {
  font-size: 18px;
  font-weight: 600;
}
</style>
