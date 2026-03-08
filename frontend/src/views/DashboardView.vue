<template>
  <el-row :gutter="16">
    <el-col :span="8">
      <el-card>
        <template #header>整改率概览</template>
        <div ref="rateChartRef" style="height: 260px;"></div>
      </el-card>
    </el-col>

    <el-col :span="8">
      <el-card>
        <template #header>整改趋势预测</template>
        <div ref="trendChartRef" style="height: 260px;"></div>
      </el-card>
    </el-col>

    <el-col :span="8">
      <el-card>
        <template #header>预警阈值设置</template>
        <el-form label-width="120px">
          <el-form-item label="超期阈值(天)">
            <el-input-number v-model="threshold.overdueDays" :min="1" />
          </el-form-item>
          <el-form-item label="最低整改率(%)">
            <el-input-number v-model="threshold.minRate" :min="1" :max="100" />
          </el-form-item>
          <el-button type="primary" @click="saveThreshold">保存阈值</el-button>
        </el-form>
      </el-card>
    </el-col>

    <el-col :span="24" style="margin-top: 16px;">
      <el-card>
        <template #header>部门整改热力图</template>
        <div ref="heatmapChartRef" style="height: 320px;"></div>
      </el-card>
    </el-col>

  </el-row>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { fetchDashboard, fetchHeatmap, fetchTrend } from '../api/dashboard'

const rateChartRef = ref()
const trendChartRef = ref()
const heatmapChartRef = ref()

let rateChart
let trendChart
let heatmapChart

const threshold = reactive({
  overdueDays: 7,
  minRate: 85
})

const initCharts = () => {
  rateChart = echarts.init(rateChartRef.value)
  trendChart = echarts.init(trendChartRef.value)
  heatmapChart = echarts.init(heatmapChartRef.value)
}

const renderRateChart = (data) => {
  rateChart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: '68%',
        data: [
          { value: data.completedRate, name: '已整改' },
          { value: 100 - data.completedRate, name: '未整改' }
        ]
      }
    ]
  })
}

const renderTrendChart = (trendData) => {
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trendData.dates },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [
      { name: '整改率', type: 'line', smooth: true, data: trendData.rates },
      { name: '预测整改率', type: 'line', smooth: true, data: trendData.predicted }
    ]
  })
}

const renderHeatmap = (heatData) => {
  heatmapChart.setOption({
    tooltip: {},
    xAxis: { type: 'category', data: heatData.metrics },
    yAxis: { type: 'category', data: heatData.departments },
    visualMap: {
      min: 0,
      max: 100,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0
    },
    series: [
      {
        type: 'heatmap',
        data: heatData.values,
        emphasis: {
          itemStyle: {
            borderColor: '#333',
            borderWidth: 1
          }
        }
      }
    ]
  })
}

const loadData = async () => {
  const [dashboardRes, trendRes, heatmapRes] = await Promise.all([
    fetchDashboard(),
    fetchTrend(),
    fetchHeatmap()
  ])
  renderRateChart(dashboardRes.data)
  renderTrendChart(trendRes.data)
  renderHeatmap(heatmapRes.data)
}

const saveThreshold = () => {
  ElMessage.success(`阈值已保存：超期${threshold.overdueDays}天，最低整改率${threshold.minRate}%`)
}

const handleResize = () => {
  rateChart?.resize()
  trendChart?.resize()
  heatmapChart?.resize()
}

onMounted(async () => {
  await nextTick()
  initCharts()
  await loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  rateChart?.dispose()
  trendChart?.dispose()
  heatmapChart?.dispose()
})
</script>
