<template>
  <el-card shadow="never" class="toolbar-card">
    <el-form :inline="true" :model="localFilters" class="toolbar-form">
      <el-form-item>
        <el-input
          v-model="localFilters.keyword"
          placeholder="搜索任务名称/目标数据集"
          clearable
          style="width: 280px"
          @keyup.enter="emitSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-select v-model="localFilters.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="待执行" value="READY" />
          <el-option label="已完成" value="COMPLETED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="emitSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
      <el-form-item class="toolbar-right">
        <el-button type="primary" @click="$emit('create')">新建融合任务</el-button>
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
  status: ''
})

watch(
  () => props.filters,
  (newFilters) => {
    localFilters.keyword = newFilters.keyword || ''
    localFilters.status = newFilters.status || ''
  },
  { immediate: true, deep: true }
)

function emitSearch() {
  emit('update:filters', { ...localFilters })
  emit('search')
}

function resetFilters() {
  localFilters.keyword = ''
  localFilters.status = ''
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
