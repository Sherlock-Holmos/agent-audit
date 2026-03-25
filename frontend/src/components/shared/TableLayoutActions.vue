<template>
  <div class="table-layout-actions">
    <el-tooltip content="重置列宽" placement="left">
      <el-button class="table-layout-reset-btn" size="small" circle @click="$emit('reset')">
        <el-icon><RefreshRight /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { RefreshRight } from '@element-plus/icons-vue'

defineEmits(['reset'])
</script>

<style scoped>
.table-layout-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.table-layout-reset-btn {
  display: inline-flex;
  align-items: center;
}
</style>
