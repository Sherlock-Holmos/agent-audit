<template>
  <section :class="['panel-frame', 'panel-block', blockClass]">
    <div v-if="title" class="panel-title">{{ title }}</div>
    <slot />
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  blockClass: {
    type: String,
    default: ''
  }
})
</script>
