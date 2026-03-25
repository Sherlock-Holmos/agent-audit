<template>
  <el-card shadow="never" :style="cardStyle">
    <template #header>
      <GovernanceSectionHeader :title="title">
        <template #actions>
          <slot name="actions" />
        </template>
      </GovernanceSectionHeader>
    </template>
    <slot />
  </el-card>
</template>

<script setup>
import GovernanceSectionHeader from './GovernanceSectionHeader.vue'

defineProps({
  title: {
    type: String,
    required: true
  },
  cardStyle: {
    type: [String, Object, Array],
    default: 'margin-top: 0'
  }
})
</script>
