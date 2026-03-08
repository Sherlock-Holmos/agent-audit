<template>
  <el-menu
    :default-active="activeMenu"
    class="el-menu-vertical-demo"
    :collapse="collapseProxy"
    router
    @open="handleOpen"
    @close="handleClose"
    style="height:100%;border:none;"
  >
    <el-menu-item index="/">
      <el-icon><location /></el-icon>
      <span>仪表盘</span>
    </el-menu-item>
    <el-sub-menu index="/datasource">
      <template #title>
        <el-icon><icon-menu /></el-icon>
        <span>数据源</span>
      </template>
      <el-menu-item index="/datasource">数据源管理</el-menu-item>
      <el-menu-item index="/datasource/clean">数据清洗</el-menu-item>
      <el-menu-item index="/datasource/fusion">数据融合</el-menu-item>
    </el-sub-menu>
    <el-menu-item index="/ai">
      <el-icon><document /></el-icon>
      <span>AI分析</span>
    </el-menu-item>
    <el-menu-item index="/config">
      <el-icon><setting /></el-icon>
      <span>配置中心</span>
    </el-menu-item>
  </el-menu>
</template>

<script lang="ts" setup>

import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '../store/app'
import { storeToRefs } from 'pinia'
import {
  Document,
  Menu as IconMenu,
  Location,
  Setting,
} from '@element-plus/icons-vue'

const appStore = useAppStore()
const { isCollapse } = storeToRefs(appStore)
const collapseProxy = computed({
  get: () => isCollapse.value,
  set: v => appStore.setCollapse(v)
})
const route = useRoute()
const activeMenu = computed(() => route.path)
const handleOpen = (_key: string, _keyPath: string[]) => {}
const handleClose = (_key: string, _keyPath: string[]) => {}
</script>

<style>
.el-menu-vertical-demo {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
