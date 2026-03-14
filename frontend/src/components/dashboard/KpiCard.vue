<template>
  <article class="kpi-card panel-frame">
    <div class="kpi-label">{{ label }}</div>
    <div class="kpi-value">{{ value }}</div>
    <div class="kpi-desc">{{ desc }}</div>
  </article>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    default: ''
  },
  value: {
    type: String,
    default: ''
  },
  desc: {
    type: String,
    default: ''
  }
})
</script>
