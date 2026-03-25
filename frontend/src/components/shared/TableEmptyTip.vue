<template>
  <div v-if="show" class="empty-tip">{{ text }}</div>
</template>

<script setup>
defineProps({
  show: {
    type: Boolean,
    default: false
  },
  text: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.empty-tip {
  margin-top: 14px;
  text-align: center;
  color: #909399;
}
</style>
