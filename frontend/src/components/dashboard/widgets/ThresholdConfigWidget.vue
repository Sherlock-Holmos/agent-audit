<template>
  <el-form label-width="100px" class="threshold-form">
    <el-form-item label="超期阈值(天)">
      <el-input-number :model-value="overdueDays" :min="1" :max="365" @update:model-value="(val) => emit('update:overdueDays', val)" />
    </el-form-item>
    <el-form-item label="最低整改率(%)">
      <el-input-number :model-value="minRate" :min="1" :max="100" @update:model-value="(val) => emit('update:minRate', val)" />
    </el-form-item>
    <el-button class="ghost-btn" @click="emit('save')">保存阈值</el-button>
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
</style>
