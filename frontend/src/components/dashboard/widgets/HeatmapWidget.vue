<template>
  <div ref="chartRef" class="chart-large"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
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

function render() {
  if (!chart) return
  chart.setOption({
    grid: { left: 60, right: 30, top: 24, bottom: 32 },
    tooltip: {
      position: 'top',
      backgroundColor: 'rgba(6,22,45,0.9)',
      borderColor: '#2f74ff'
    },
    xAxis: {
      type: 'category',
      data: props.metrics,
      splitArea: { show: true },
      axisLabel: { color: '#9fcbff' }
    },
    yAxis: {
      type: 'category',
      data: props.departments,
      splitArea: { show: true },
      axisLabel: { color: '#9fcbff' }
    },
    visualMap: {
      min: 0,
      max: 100,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      textStyle: { color: '#9fcbff' },
      inRange: {
        color: ['#102d69', '#1f5bd8', '#1dc8ff', '#8df2ff']
      }
    },
    series: [
      {
        type: 'heatmap',
        data: props.values,
        label: {
          show: true,
          color: '#f0fbff',
          formatter: '{c}%'
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.45)'
          }
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
.chart-large {
  height: 320px;
}

@media (max-width: 840px) {
  .chart-large {
    height: 280px;
  }
}
</style>
