<template>
  <div ref="chartRef" class="gauge-chart"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { createRafThrottle } from '../../../utils/perf'

const props = defineProps({
  value: {
    type: Number,
    default: 0
  }
})

const chartRef = ref()
let chart
const handleResize = createRafThrottle(() => {
  chart?.resize()
})

function render() {
  if (!chart) return
  chart.setOption({
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        min: 0,
        max: 100,
        splitNumber: 10,
        startAngle: 220,
        endAngle: -40,
        progress: {
          show: true,
          overlap: false,
          width: 20,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#1ef4ff' },
              { offset: 1, color: '#2b86ff' }
            ])
          }
        },
        pointer: {
          show: true,
          length: '60%',
          width: 5
        },
        axisLine: {
          lineStyle: {
            width: 20,
            color: [[1, 'rgba(90,128,255,0.22)']]
          }
        },
        axisTick: {
          distance: -25,
          splitNumber: 4,
          lineStyle: { color: 'rgba(152,201,255,0.55)', width: 1 }
        },
        splitLine: {
          distance: -28,
          length: 10,
          lineStyle: { color: '#9ad0ff', width: 1 }
        },
        axisLabel: {
          distance: -42,
          color: '#a7d4ff',
          fontSize: 11
        },
        detail: {
          valueAnimation: true,
          formatter: '{value}%',
          color: '#e6f4ff',
          fontSize: 38,
          fontWeight: 700,
          offsetCenter: [0, '25%']
        },
        title: {
          offsetCenter: [0, '55%'],
          color: '#7ebdf5',
          fontSize: 14
        },
        data: [{ value: props.value, name: '整改完成率' }]
      }
    ]
  })
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  handleResize.cancel()
  chart?.dispose()
  chart = undefined
})

watch(
  () => props.value,
  () => {
    render()
  }
)
</script>

<style scoped>
.gauge-chart {
  width: 100%;
  max-width: 560px;
  height: 360px;
}

@media (max-width: 1360px) {
  .gauge-chart {
    height: 300px;
  }
}
</style>
