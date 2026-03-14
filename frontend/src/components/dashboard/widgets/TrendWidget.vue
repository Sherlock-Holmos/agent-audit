<template>
  <div ref="chartRef" class="chart-medium"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  dates: {
    type: Array,
    default: () => []
  },
  rates: {
    type: Array,
    default: () => []
  },
  predicted: {
    type: Array,
    default: () => []
  }
})

const chartRef = ref()
let chart

function render() {
  if (!chart) return
  chart.setOption({
    color: ['#3bd8ff', '#80ffa5'],
    grid: { left: 36, right: 16, top: 34, bottom: 24 },
    legend: {
      top: 4,
      textStyle: { color: '#8fc6ff', fontSize: 12 }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(6,22,45,0.9)',
      borderColor: '#2f74ff'
    },
    xAxis: {
      type: 'category',
      data: props.dates,
      axisLine: { lineStyle: { color: '#245f9f' } },
      axisLabel: { color: '#8fc6ff' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(58,108,168,0.25)' } },
      axisLabel: { color: '#8fc6ff' }
    },
    series: [
      {
        name: '整改率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: props.rates,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59,216,255,0.35)' },
            { offset: 1, color: 'rgba(59,216,255,0.03)' }
          ])
        }
      },
      {
        name: '预测率',
        type: 'line',
        smooth: true,
        symbol: 'diamond',
        symbolSize: 6,
        data: props.predicted,
        lineStyle: { type: 'dashed' }
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
  () => [props.dates, props.rates, props.predicted],
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
