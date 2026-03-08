<template>
  <div class="sidebar-user" @mouseenter="visible = true" @mouseleave="visible = false">
    <el-dropdown trigger="hover" v-model:visible="visible">
      <div class="user-info" :class="{ collapsed }">
        <el-avatar :size="40" :src="avatarSrc" />
        <span v-if="!collapsed" class="user-name">{{ displayName }}</span>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="$emit('profile')">个人信息</el-dropdown-item>
          <el-dropdown-item divided @click="$emit('logout')">退出登录</el-dropdown-item>
          <el-dropdown-item @click="$emit('deactivate')">注销账号</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  user: {
    type: Object,
    default: () => ({})
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

defineEmits(['profile', 'logout', 'deactivate'])

const visible = ref(false)

const displayName = computed(() => {
  return props.user?.nickname || props.user?.name || props.user?.username || '用户'
})

const avatarSrc = computed(() => {
  if (props.user?.avatarUrl) return props.user.avatarUrl
  const seed = encodeURIComponent(props.user?.username || displayName.value || 'user')
  return `https://api.dicebear.com/7.x/identicon/svg?seed=${seed}`
})
</script>
