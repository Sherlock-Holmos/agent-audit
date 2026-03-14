<template>
  <div class="metric-list">
    <div class="metric-item" v-for="metric in items" :key="metric.name">
      <div class="metric-head">
        <span>{{ metric.name }}</span>
        <span>{{ metric.value }}%</span>
      </div>
      <div class="metric-bar">
        <div class="metric-fill" :style="{ width: `${metric.value}%` }"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.metric-list {
  margin-top: 8px;
}

.metric-item {
  margin-bottom: 12px;
}

.metric-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 4px;
  color: #a8d3ff;
}

.metric-bar {
  width: 100%;
  height: 8px;
  background: rgba(24, 58, 106, 0.75);
  border-radius: 999px;
  overflow: hidden;
}

.metric-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0ad1ff 0%, #1e6fff 100%);
  box-shadow: 0 0 10px rgba(18, 179, 255, 0.45);
  transition: width 0.35s ease;
}
</style>
