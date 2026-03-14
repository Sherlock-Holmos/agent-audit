<template>
  <div ref="chartRef" class="chart-medium"></div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  departments: {
    type: Array,
    default: () => []
  },
  metrics: {
    type: Array,
    default: () => []
  },
  values: {
    type: Array,
    default: () => []
  }
})

const chartRef = ref()
let chart

const rankRows = computed(() => {
  const completionMetricIndex = props.metrics.findIndex((it) => it === '完成率')
  const rows = props.departments.map((name, deptIndex) => {
    const hit = props.values.find((item) => Array.isArray(item) && item[0] === deptIndex && item[1] === completionMetricIndex)
    return { name, value: hit ? Number(hit[2]) || 0 : 0 }
  })
  rows.sort((a, b) => b.value - a.value)
  return rows
})

function render() {
  if (!chart) return
  chart.setOption({
    grid: { left: 80, right: 22, top: 12, bottom: 18 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(6,22,45,0.9)',
      borderColor: '#2f74ff'
    },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { color: '#8fc6ff' },
      splitLine: { lineStyle: { color: 'rgba(58,108,168,0.2)' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rankRows.value.map((it) => it.name),
      axisLabel: { color: '#a9d2ff', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        data: rankRows.value.map((it) => it.value),
        barWidth: 10,
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#0dd8ff' },
            { offset: 1, color: '#3078ff' }
          ])
        },
        label: {
          show: true,
          position: 'right',
          color: '#d7efff',
          formatter: '{c}%'
        }
      }
    ]
  })
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = undefined
})

watch(
  () => [props.departments, props.metrics, props.values],
  () => render(),
  { deep: true }
)
</script>

<style scoped>
.chart-medium {
  height: 240px;
}

@media (max-width: 840px) {
  .chart-medium {
    height: 220px;
  }
}
</style>
