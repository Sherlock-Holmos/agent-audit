<template>
  <el-drawer
    :model-value="modelValue"
    title="首页组件自定义"
    size="420px"
    :with-header="true"
    @update:model-value="(val) => emit('update:modelValue', val)"
  >
    <div class="customizer-wrap">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        description="可配置组件显示与排序，当前配置会自动保存在浏览器本地。"
      />

      <div class="customizer-section">
        <h4>KPI 指标卡</h4>
        <div
          v-for="item in kpiLayout"
          :key="item.key"
          class="custom-item"
          draggable="true"
          @dragstart="onDragStart('kpi', item.key)"
          @dragover.prevent
          @drop="onDrop('kpi', item.key)"
        >
          <el-switch
            :model-value="item.enabled"
            @change="(val) => emit('toggle-kpi', item.key, val)"
          />
          <span class="item-title">{{ item.label }}</span>
          <div class="item-actions">
            <el-button text @click="emit('move-kpi', item.key, 'up')">上移</el-button>
            <el-button text @click="emit('move-kpi', item.key, 'down')">下移</el-button>
          </div>
        </div>
      </div>

      <div class="customizer-section">
        <h4>功能组件</h4>
        <div
          v-for="item in widgetLayout"
          :key="item.key"
          class="custom-item"
          draggable="true"
          @dragstart="onDragStart('widget', item.key)"
          @dragover.prevent
          @drop="onDrop('widget', item.key)"
        >
          <el-switch
            :model-value="item.enabled"
            @change="(val) => emit('toggle-widget', item.key, val)"
          />
          <div class="item-main">
            <span class="item-title">{{ item.title }}</span>
            <span class="item-meta">区域：{{ regionLabel[item.region] }}</span>
          </div>
          <div class="item-actions">
            <el-button text @click="emit('move-widget', item.key, 'up')">上移</el-button>
            <el-button text @click="emit('move-widget', item.key, 'down')">下移</el-button>
          </div>
        </div>
      </div>

      <div class="customizer-footer">
        <el-button @click="emit('reset-layout')">恢复默认</el-button>
        <el-button type="primary" @click="emit('update:modelValue', false)">完成</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  kpiLayout: {
    type: Array,
    default: () => []
  },
  widgetLayout: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits([
  'update:modelValue',
  'toggle-kpi',
  'move-kpi',
  'reorder-kpi',
  'toggle-widget',
  'move-widget',
  'reorder-widget',
  'reset-layout'
])

const dragPayload = ref({
  type: '',
  key: ''
})

function onDragStart(type, key) {
  dragPayload.value = { type, key }
}

function onDrop(type, targetKey) {
  if (dragPayload.value.type !== type) return
  if (!dragPayload.value.key || dragPayload.value.key === targetKey) return
  if (type === 'kpi') {
    emit('reorder-kpi', dragPayload.value.key, targetKey)
  } else if (type === 'widget') {
    emit('reorder-widget', dragPayload.value.key, targetKey)
  }
  dragPayload.value = { type: '', key: '' }
}

const regionLabel = {
  left: '左侧',
  center: '中间',
  right: '右侧',
  bottom: '底部'
}
</script>

<style scoped>
.customizer-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.customizer-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.custom-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.item-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.item-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.item-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.customizer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
