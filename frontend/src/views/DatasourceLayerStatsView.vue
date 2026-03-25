<template>
  <GovernancePageShell>

    <GovernanceCardSection title="Bronze/Silver/Gold 分层统计" card-style="margin-top: 0">
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
      <GovernanceTable
        :data="layerDetails"
        :loading="loadingLayerStats"
        layout-storage-key="governance-layer-stats-table"
        :column-keys="['taskType', 'taskId', 'bronzeRows', 'silverRows', 'goldRows']"
      >
        <template #default="{ resolveWidth, resolveMinWidth }">
          <el-table-column
            column-key="taskType"
            prop="taskType"
            label="任务类型"
            :width="resolveWidth('taskType', 140)"
            :min-width="resolveMinWidth('taskType', 140)"
          />
          <el-table-column
            column-key="taskId"
            prop="taskId"
            label="任务ID"
            :width="resolveWidth('taskId', 120)"
            :min-width="resolveMinWidth('taskId', 120)"
          />
          <el-table-column
            column-key="bronzeRows"
            prop="bronzeRows"
            label="Bronze"
            :width="resolveWidth('bronzeRows', 160)"
            :min-width="resolveMinWidth('bronzeRows', 160)"
            align="right"
          />
          <el-table-column
            column-key="silverRows"
            prop="silverRows"
            label="Silver"
            :width="resolveWidth('silverRows', 160)"
            :min-width="resolveMinWidth('silverRows', 160)"
            align="right"
          />
          <el-table-column
            column-key="goldRows"
            prop="goldRows"
            label="Gold"
            :width="resolveWidth('goldRows', 160)"
            :min-width="resolveMinWidth('goldRows', 160)"
            align="right"
          />
        </template>
      </GovernanceTable>
    </GovernanceCardSection>
  </GovernancePageShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import GovernancePageShell from '../components/dataclean/GovernancePageShell.vue'
import GovernanceCardSection from '../components/dataclean/GovernanceCardSection.vue'
import GovernanceTable from '../components/dataclean/GovernanceTable.vue'
import { listLayerStats } from '../api/layer-stats'
import { useAsyncTask } from '../composables/useAsyncTask'

const layerDetails = ref([])
const { loading: loadingLayerStats, run: runQueryLayerStats } = useAsyncTask()

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
  const result = await runQueryLayerStats(async () => {
    const params = {}
    if (layerFilter.taskType) {
      params.taskType = layerFilter.taskType
    }
    if (layerFilter.taskId) {
      params.taskId = layerFilter.taskId
    }
    return listLayerStats(params)
  }, {
    errorMessage: '加载分层统计失败',
    onError: () => {
      layerDetails.value = []
      Object.assign(layerSummary, {
        bronzeRows: 0,
        silverRows: 0,
        goldRows: 0,
        taskCount: 0
      })
    }
  })

  if (!result) {
    return
  }

  const payload = result.data?.data || {}
    Object.assign(layerSummary, payload.summary || {
      bronzeRows: 0,
      silverRows: 0,
      goldRows: 0,
      taskCount: 0
    })
    layerDetails.value = payload.details || []
}

function resetLayerFilter() {
  layerFilter.taskType = ''
  layerFilter.taskId = null
  queryLayerStats()
}

onMounted(queryLayerStats)
</script>
