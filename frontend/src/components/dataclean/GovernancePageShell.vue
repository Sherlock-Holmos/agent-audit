<template>
  <div class="governance-page-shell">
    <GovernanceSubNav />
    <slot />
  </div>
</template>

<script setup>
import GovernanceSubNav from './GovernanceSubNav.vue'
</script>

<style scoped>
.governance-page-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>