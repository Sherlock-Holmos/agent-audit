<template>
  <nav class="custom-menu" :class="{ collapsed: isCollapse }">
    <ul class="menu-list">
      <li
        v-for="item in menuList"
        :key="item.path"
        :class="['menu-item', { active: isTopActive(item), hasChildren: !!item.children }]"
      >
        <template v-if="item.children && isCollapse">
          <el-popover
            placement="right-start"
            trigger="hover"
            :width="180"
            popper-class="collapsed-submenu-popper"
          >
            <template #reference>
              <div class="menu-item-main" @click.prevent>
                <el-icon class="icon"><component :is="item.icon" /></el-icon>
              </div>
            </template>
            <div class="collapsed-submenu-wrap">
              <div class="collapsed-submenu-title">{{ item.title }}</div>
              <div
                v-for="sub in item.children"
                :key="sub.path"
                :class="['collapsed-submenu-item', { active: isActive(sub.path) }]"
                @click="goToPath(sub.path)"
              >
                {{ sub.title }}
              </div>
            </div>
          </el-popover>
        </template>

        <template v-else>
          <div class="menu-item-main" @click="handleMenuClick(item)">
            <el-icon class="icon"><component :is="item.icon" /></el-icon>
            <transition name="fade-slide">
              <span v-if="!isCollapse" class="title">{{ item.title }}</span>
            </transition>
            <el-icon
              v-if="item.children && !isCollapse"
              class="arrow"
              :class="{ open: item.open }"
              @click.stop="toggleSub(item)"
            >
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <transition name="submenu-slide">
          <ul v-if="item.children && item.open && !isCollapse" class="submenu">
            <li
              v-for="sub in item.children"
              :key="sub.path"
              :class="['submenu-item', { active: isActive(sub.path) }]"
              @click="handleMenuClick(sub)"
            >
              <span class="submenu-title">{{ sub.title }}</span>
            </li>
          </ul>
        </transition>
      </li>
    </ul>
  </nav>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ArrowRight, DataBoard, Coin, Cpu, Setting } from '@element-plus/icons-vue'
import { useAppStore } from '../store/app'

const appStore = useAppStore()
const { isCollapse } = storeToRefs(appStore)
const route = useRoute()
const router = useRouter()

const menuList = ref([
  { title: '仪表盘', path: '/', icon: DataBoard },
  {
    title: '数据源',
    path: '/datasource',
    icon: Coin,
    open: false,
    children: [
      { title: '数据源管理', path: '/datasource' },
      { title: '数据清洗', path: '/datasource/clean' },
      { title: '清洗规则管理', path: '/datasource/clean-rules' },
      { title: '数据融合', path: '/datasource/fusion' }
    ]
  },
  { title: 'AI分析', path: '/ai', icon: Cpu },
  { title: '设置', path: '/settings', icon: Setting }
])

function isActive(path) {
  return route.path === path
}

function isTopActive(item) {
  if (!item.children) return isActive(item.path)
  return item.children.some((sub) => route.path === sub.path)
}

function handleMenuClick(item) {
  if (item.children) {
    if (isCollapse.value) {
      return
    }
    toggleSub(item)
    return
  }
  goToPath(item.path)
}

function toggleSub(item) {
  item.open = !item.open
}

function goToPath(path) {
  if (route.path !== path) {
    router.push(path)
  }
}

watch(
  () => route.path,
  (path) => {
    menuList.value.forEach((item) => {
      if (item.children) {
        item.open = item.children.some((sub) => sub.path === path)
      }
    })
  },
  { immediate: true }
)
</script>

<style scoped>
.custom-menu {
  width: 220px;
  height: 100%;
  overflow: hidden;
  transition: width 0.24s ease;
}

.custom-menu.collapsed {
  width: 64px;
}

.menu-list,
.submenu {
  list-style: none;
  margin: 0;
  padding: 0;
}

.menu-item {
  display: flex;
  flex-direction: column;
}

.menu-item-main {
  margin: 6px 10px;
  height: 42px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.menu-item-main:hover {
  background: var(--menu-hover-bg);
}

.menu-item-main:hover .title,
.menu-item-main:hover .icon,
.menu-item-main:hover .arrow {
  color: var(--menu-hover-text-color);
}

.menu-item.active .menu-item-main {
  background: var(--menu-active-bg);
}

.menu-item.active .menu-item-main .title,
.menu-item.active .menu-item-main .icon,
.menu-item.active .menu-item-main .arrow {
  color: var(--menu-active-text-color) !important;
}

.icon {
  margin-left: 12px;
  font-size: 18px;
  color: var(--menu-icon-color);
  flex-shrink: 0;
}

.title {
  margin-left: 10px;
  font-size: 14px;
  color: var(--menu-text-color);
  white-space: nowrap;
}

.arrow {
  margin-left: auto;
  margin-right: 10px;
  color: var(--menu-arrow-color);
  transition: transform 0.2s ease;
}

.arrow.open {
  transform: rotate(90deg);
}

.submenu {
  padding: 2px 0 6px;
}

.submenu-item {
  margin: 4px 10px 4px 44px;
  height: 34px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.submenu-item:hover {
  background: var(--submenu-hover-bg);
}

.submenu-item:hover .submenu-title {
  color: var(--menu-hover-text-color);
}

.submenu-item.active {
  background: var(--menu-active-bg);
}

.submenu-title {
  font-size: 13px;
  color: var(--submenu-text-color);
}

.submenu-item.active .submenu-title {
  color: var(--menu-active-text-color);
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

.submenu-slide-enter-active,
.submenu-slide-leave-active {
  transition: max-height 0.24s ease, opacity 0.2s ease;
  overflow: hidden;
}

.submenu-slide-enter-from,
.submenu-slide-leave-to {
  max-height: 0;
  opacity: 0;
}

.submenu-slide-enter-to,
.submenu-slide-leave-from {
  max-height: 220px;
  opacity: 1;
}

:deep(.collapsed-submenu-popper) {
  padding: 8px 0;
}

.collapsed-submenu-wrap {
  min-width: 160px;
}

.collapsed-submenu-title {
  padding: 4px 12px 8px;
  font-size: 12px;
  color: var(--collapsed-submenu-title-color);
}

.collapsed-submenu-item {
  height: 34px;
  line-height: 34px;
  padding: 0 12px;
  border-radius: 6px;
  margin: 2px 6px;
  font-size: 13px;
  color: var(--submenu-text-color);
  cursor: pointer;
}

.collapsed-submenu-item:hover {
  background: var(--submenu-hover-bg);
  color: var(--menu-hover-text-color);
}

.collapsed-submenu-item.active {
  background: var(--menu-active-bg);
  color: var(--menu-active-text-color);
}
</style>
