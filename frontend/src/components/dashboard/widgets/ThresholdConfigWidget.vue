<template>
  <el-form label-width="100px" class="threshold-form">
    <el-form-item label="超期阈值(天)" class="compact-item">
      <el-input-number
        :model-value="overdueDays"
        :min="1"
        :max="365"
        controls-position="right"
        @update:model-value="(val) => emit('update:overdueDays', val)"
      />
    </el-form-item>
    <el-form-item label="最低整改率(%)" class="compact-item">
      <el-input-number
        :model-value="minRate"
        :min="1"
        :max="100"
        controls-position="right"
        @update:model-value="(val) => emit('update:minRate', val)"
      />
    </el-form-item>

    <div class="threshold-preview">
      <div class="preview-row">
        <span>当前预警线</span>
        <strong>{{ overdueDays }} 天</strong>
      </div>
      <div class="preview-row">
        <span>当前目标值</span>
        <strong>{{ minRate }}%</strong>
      </div>
    </div>

    <el-button class="ghost-btn save-btn" @click="emit('save')">保存阈值</el-button>
  </el-form>
</template>

<script setup>
defineProps({
  overdueDays: {
    type: Number,
    default: 7
  },
  minRate: {
    type: Number,
    default: 85
  }
})

const emit = defineEmits(['update:overdueDays', 'update:minRate', 'save'])
</script>

<style scoped>
.threshold-form {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.compact-item {
  margin-bottom: 8px;
}

.threshold-form :deep(.el-form-item__label) {
  color: #a8d5ff;
}

.threshold-form :deep(.el-input__wrapper),
.threshold-form :deep(.el-input-number__decrease),
.threshold-form :deep(.el-input-number__increase) {
  background: rgba(14, 38, 80, 0.9);
  border-color: rgba(67, 162, 255, 0.4);
  color: #d8eeff;
}

.threshold-preview {
  margin-top: 2px;
  padding: 8px 10px;
  border: 1px solid rgba(73, 170, 255, 0.28);
  background: rgba(8, 29, 67, 0.55);
  border-radius: 8px;
}

.preview-row {
  display: flex;
  justify-content: space-between;
  color: #9fcbff;
  font-size: 12px;
  line-height: 1.8;
}

.preview-row strong {
  color: #d9f0ff;
}

.save-btn {
  margin-top: auto;
  width: 120px;
}
</style>
