<template>
  <div class="governance-table-wrap">
    <AppDataTable
      :data="data"
      :loading="loading"
      :layout-storage-key="layoutStorageKey"
      :column-keys="columnKeys"
      :min-by-key="minByKey"
      :empty-text="emptyText"
      :show-empty="true"
      :with-card="false"
      card-class="governance-table-inner"
      :follow-global-table-size="true"
    >
      <template #default="{ resolveWidth, resolveMinWidth }">
        <slot :resolveWidth="resolveWidth" :resolveMinWidth="resolveMinWidth" />
      </template>
    </AppDataTable>
  </div>
</template>

<script setup>
import AppDataTable from '../shared/AppDataTable.vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  layoutStorageKey: {
    type: String,
    required: true
  },
  columnKeys: {
    type: Array,
    default: () => []
  },
  minByKey: {
    type: Object,
    default: () => ({})
  },
  emptyText: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.governance-table-wrap {
  width: 100%;
}

:deep(.governance-table-inner) {
  width: 100%;
}
</style>
