<template>
  <div ref="chartRef" class="chart-large"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { createRafThrottle } from '../../../utils/perf'

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
const handleResize = createRafThrottle(() => {
  chart?.resize()
})
const scheduleRender = createRafThrottle(() => {
  render()
})

function render() {
  if (!chart) return

  const maxDeptLength = props.departments.reduce((max, item) => Math.max(max, String(item || '').length), 0)
  const deptLabelWidth = Math.min(220, Math.max(90, maxDeptLength * 9))

  chart.setOption({
    grid: { left: 10, right: 30, top: 24, bottom: 32, containLabel: true },
    tooltip: {
      position: 'top',
      backgroundColor: 'rgba(6,22,45,0.9)',
      borderColor: '#2f74ff',
      formatter: (params) => {
        const value = params?.value
        if (!Array.isArray(value)) return ''
        const dept = props.departments[value[0]] || '-'
        const metric = props.metrics[value[1]] || '-'
        return `${dept}<br/>${metric}：${value[2]}%`
      }
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
      axisLabel: {
        color: '#9fcbff',
        width: deptLabelWidth,
        overflow: 'truncate'
      }
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
        progressive: 1000,
        progressiveThreshold: 2000,
        label: {
          show: true,
          color: '#f0fbff',
          formatter: ({ value }) => `${Array.isArray(value) ? value[2] : value}%`
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.45)'
          }
        }
      }
    ]
  }, { notMerge: true, lazyUpdate: true, silent: true })
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  handleResize.cancel()
  scheduleRender.cancel()
  chart?.dispose()
  chart = undefined
})

watch(
  () => [props.departments, props.metrics, props.values],
  () => scheduleRender()
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
