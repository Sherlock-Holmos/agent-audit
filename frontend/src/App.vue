
<template>
  <router-view v-if="isAuthPage" />
  <el-container v-else class="full-layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar-fixed">
      <UserAvatarMenu
        :user="user"
        :collapsed="isCollapse"
        @profile="profileDialogVisible = true"
        @logout="logout"
        @deactivate="deactivateAccount"
      />
      <div style="flex:1;overflow:hidden;"><CustomMenu /></div>
      <div style="height:56px;display:flex;align-items:center;justify-content:center;">
        <div @click="toggleCollapse" class="collapse-btn">
          <el-icon :style="{transform: isCollapse ? 'rotate(180deg)' : 'none',transition:'transform 0.2s'}">
            <ArrowLeft />
          </el-icon>
        </div>
      </div>
    </el-aside>
    <el-container class="main-fixed">
      <el-header class="header-fixed">
        <span>基于多源数据融合的审计整改智能驾驶舱</span>
      </el-header>
      <el-main class="main-scroll" :style="mainStyle">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <UserProfileDialog
    v-model="profileDialogVisible"
    :user="user"
    :submitting="profileSubmitting"
    @submit="saveProfile"
  />

  <FloatingAssistant v-if="!isAuthPage" />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomMenu from './components/CustomMenu.vue'
import UserAvatarMenu from './components/user/UserAvatarMenu.vue'
import UserProfileDialog from './components/user/UserProfileDialog.vue'
import FloatingAssistant from './components/assistant/FloatingAssistant.vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useAppStore } from './store/app'
import { storeToRefs } from 'pinia'
import { deactivateMyAccountApi, getMyProfileApi, updateMyProfileApi } from './api/auth'

const appStore = useAppStore()
const { isCollapse, themeMode } = storeToRefs(appStore)
const route = useRoute()
const router = useRouter()
let systemThemeMediaQuery

const isAuthPage = computed(() => ['/login', '/register'].includes(route.path))

const mainStyle = computed(() => ({
  padding: '24px',
  transition: 'margin-left 0.2s',
  marginLeft: isCollapse.value ? '0' : '0'
}))
function toggleCollapse() {
  appStore.toggleCollapse()
}

function handleSystemThemeChange() {
  if (themeMode.value === 'system') {
    appStore.applyTheme()
  }
}

const user = ref({})
const profileDialogVisible = ref(false)
const profileSubmitting = ref(false)

try {
  user.value = JSON.parse(localStorage.getItem('user')) || {}
} catch { user.value = {} }

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  user.value = {}
  router.replace('/login')
}

async function loadProfile() {
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    const { data } = await getMyProfileApi()
    user.value = data.data || {}
    localStorage.setItem('user', JSON.stringify(user.value))
  } catch {
    logout()
  }
}

async function saveProfile(payload) {
  profileSubmitting.value = true
  try {
    const { data } = await updateMyProfileApi(payload)
    user.value = data.data || {}
    localStorage.setItem('user', JSON.stringify(user.value))
    profileDialogVisible.value = false
    ElMessage.success('个人信息已保存')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    profileSubmitting.value = false
  }
}

async function deactivateAccount() {
  try {
    await ElMessageBox.confirm('注销后账号将停用，确认继续？', '注销账号', {
      type: 'warning',
      confirmButtonText: '确认注销',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deactivateMyAccountApi()
    ElMessage.success('账号已注销')
    logout()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '注销失败')
  }
}

watch(
  () => route.path,
  (path) => {
    if (path === '/login' || path === '/register') {
      user.value = {}
      return
    }
    loadProfile()
  },
  { immediate: true }
)

watch(
  () => themeMode.value,
  () => {
    appStore.applyTheme()
  },
  { immediate: true }
)

onMounted(() => {
  systemThemeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  systemThemeMediaQuery.addEventListener('change', handleSystemThemeChange)
})

onBeforeUnmount(() => {
  systemThemeMediaQuery?.removeEventListener('change', handleSystemThemeChange)
})
</script>

<style>
.sidebar-user {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  transition: background 0.2s;
}
.sidebar-user:hover {
  background: #e6f7ff;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  height: 48px;
  border-radius: 24px;
  transition: all 0.2s;
}
.user-info.collapsed {
  padding: 0;
}
.user-name {
  font-size: 16px;
  font-weight: 500;
  white-space: nowrap;
}
.full-layout {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
}
.sidebar-fixed {
  background: #f5f7fa;
  height: 100vh;
  box-shadow: 2px 0 8px #eee;
  --menu-text-color: #303133;
  --menu-icon-color: #606266;
  --menu-arrow-color: #909399;
  --submenu-text-color: #606266;
  --collapsed-submenu-title-color: #909399;
  --menu-hover-bg: #ecf5ff;
  --menu-hover-text-color: #1f2d3d;
  --menu-active-bg: #e6f4ff;
  --menu-active-text-color: #1f2d3d;
  --submenu-hover-bg: #f0f7ff;
  transition: width 0.2s;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.main-fixed {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.header-fixed {
  font-size: 22px;
  font-weight: 600;
  background: #fff;
  border-bottom: 1px solid #eee;
  height: 60px;
  line-height: 60px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-shrink: 0;
  z-index: 10;
  padding: 0 20px;
}
.main-scroll {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 24px;
  background: #fafbfc;
}
.collapse-btn {
  width: 36px;
  height: 36px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 8px #eee;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
}
.collapse-btn:hover {
  background: #f0f0f0;
}
.fade-slide-enter-active, .fade-slide-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.fade-slide-enter-from, .fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
.fade-slide-enter-to, .fade-slide-leave-from {
  opacity: 1;
  transform: translateX(0);
}
.sidebar-title {
  display: inline-block;
  white-space: nowrap;
}

.dark-theme .sidebar-fixed {
  background: #1d2430;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.4);
  --menu-text-color: #ffffff;
  --menu-icon-color: #ffffff;
  --menu-arrow-color: #ffffff;
  --submenu-text-color: #ffffff;
  --collapsed-submenu-title-color: #ffffff;
  --menu-hover-bg: #e6f4ff;
  --menu-hover-text-color: #000000;
  --menu-active-bg: #e6f4ff;
  --menu-active-text-color: #000000;
  --submenu-hover-bg: #e6f4ff;
}

.dark-theme .header-fixed {
  background: #131a24;
  border-bottom-color: #2c3a4f;
  color: #e5eaf3;
}

.dark-theme .main-scroll {
  background: #101722;
  color: #dfe6ef;
}

.dark-theme .collapse-btn {
  background: #253246;
  box-shadow: none;
  color: #dfe6ef;
}

.dark-theme .collapse-btn:hover {
  background: #30425b;
}

.dark-theme .sidebar-user:hover {
  background: #2a3a50;
}
</style>
