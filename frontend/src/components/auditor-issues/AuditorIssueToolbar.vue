<template>
  <el-card shadow="never" class="toolbar-card">
    <el-form :inline="true" :model="localFilters" class="toolbar-form">
      <el-form-item>
        <el-input
          v-model="localFilters.keyword"
          placeholder="搜索问题标题/单位"
          clearable
          style="width: 260px"
          @keyup.enter="emitSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-select v-model="localFilters.level" placeholder="等级" clearable style="width: 140px">
          <el-option label="低" value="低" />
          <el-option label="中" value="中" />
          <el-option label="高" value="高" />
          <el-option label="重大" value="重大" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="emitSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
      <el-form-item class="toolbar-right">
        <el-button type="primary" @click="$emit('create')">新增审计问题</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  filters: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:filters', 'search', 'reset', 'create'])

const localFilters = reactive({
  keyword: '',
  level: ''
})

watch(
  () => props.filters,
  (newFilters) => {
    localFilters.keyword = newFilters.keyword || ''
    localFilters.level = newFilters.level || ''
  },
  { immediate: true, deep: true }
)

function emitSearch() {
  emit('update:filters', { ...localFilters })
  emit('search')
}

function resetFilters() {
  localFilters.keyword = ''
  localFilters.level = ''
  emit('update:filters', { ...localFilters })
  emit('reset')
}
</script>

<style scoped>
.toolbar-card {
  margin-bottom: 16px;
}

.toolbar-form {
  display: flex;
  flex-wrap: wrap;
}

.toolbar-right {
  margin-left: auto;
}
</style>
