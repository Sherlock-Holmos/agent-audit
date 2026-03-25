<template>
  <GovernancePageShell>

    <el-card shadow="never" style="margin-top: 0">
      <template #header>
        <GovernanceSectionHeader title="Bronze/Silver/Gold 分层统计">
          <template #actions>
          <el-space>
            <el-select v-model="layerFilter.taskType" clearable placeholder="任务类型" style="width: 130px">
              <el-option label="CLEAN" value="CLEAN" />
              <el-option label="FUSION" value="FUSION" />
            </el-select>
            <el-input-number v-model="layerFilter.taskId" :min="1" :step="1" placeholder="任务ID" style="width: 140px" />
            <el-button type="primary" :loading="loadingLayerStats" @click="queryLayerStats">查询</el-button>
            <el-button @click="resetLayerFilter">重置</el-button>
          </el-space>
          </template>
        </GovernanceSectionHeader>
      </template>
      <el-row :gutter="12" style="margin-bottom: 10px">
        <el-col :span="6">
          <el-statistic title="Bronze 行数" :value="layerSummary.bronzeRows || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="Silver 行数" :value="layerSummary.silverRows || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="Gold 行数" :value="layerSummary.goldRows || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="任务数" :value="layerSummary.taskCount || 0" />
        </el-col>
      </el-row>
      <el-table :data="layerDetails" v-loading="loadingLayerStats" border style="width: 100%">
        <el-table-column prop="taskType" label="任务类型" width="120" />
        <el-table-column prop="taskId" label="任务ID" width="120" />
        <el-table-column prop="bronzeRows" label="Bronze" width="140" align="right" />
        <el-table-column prop="silverRows" label="Silver" width="140" align="right" />
        <el-table-column prop="goldRows" label="Gold" width="140" align="right" />
      </el-table>
    </el-card>
  </GovernancePageShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import GovernancePageShell from '../components/dataclean/GovernancePageShell.vue'
import GovernanceSectionHeader from '../components/dataclean/GovernanceSectionHeader.vue'
import { listLayerStats } from '../api/layer-stats'
import { getErrorMessage } from '../utils/error'

const layerDetails = ref([])
const loadingLayerStats = ref(false)

const layerFilter = reactive({
  taskType: '',
  taskId: null
})

const layerSummary = reactive({
  bronzeRows: 0,
  silverRows: 0,
  goldRows: 0,
  taskCount: 0
})

async function queryLayerStats() {
  loadingLayerStats.value = true
  try {
    const params = {}
    if (layerFilter.taskType) {
      params.taskType = layerFilter.taskType
    }
    if (layerFilter.taskId) {
      params.taskId = layerFilter.taskId
    }
    const { data } = await listLayerStats(params)
    const payload = data.data || {}
    Object.assign(layerSummary, payload.summary || {
      bronzeRows: 0,
      silverRows: 0,
      goldRows: 0,
      taskCount: 0
    })
    layerDetails.value = payload.details || []
  } catch (error) {
    layerDetails.value = []
    Object.assign(layerSummary, {
      bronzeRows: 0,
      silverRows: 0,
      goldRows: 0,
      taskCount: 0
    })
    ElMessage.error(getErrorMessage(error, '加载分层统计失败'))
  } finally {
    loadingLayerStats.value = false
  }
}

function resetLayerFilter() {
  layerFilter.taskType = ''
  layerFilter.taskId = null
  queryLayerStats()
}

onMounted(queryLayerStats)
</script>
