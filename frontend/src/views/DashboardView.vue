<template>
  <section class="cockpit-page">
    <div class="glow-grid"></div>

    <header class="cockpit-header panel-frame">
      <div class="header-title-wrap">
        <h1 class="header-title">审计整改智能驾驶舱</h1>
        <p class="header-subtitle">多源融合数据实时态势 · 可视化大屏</p>
      </div>

      <div class="header-actions">
        <el-select
          v-model="selectedFusionTaskId"
          class="fusion-select"
          placeholder="选择融合任务"
          clearable
          filterable
          @change="handleFusionChange"
        >
          <el-option
            v-for="item in fusionOptions"
            :key="item.id"
            :label="`${item.taskName} (${item.targetTable})`"
            :value="item.id"
          />
        </el-select>
        <el-button class="ghost-btn" @click="handleManualRefresh">手动刷新</el-button>
        <el-button class="ghost-btn" @click="layoutDrawerVisible = true">布局定制</el-button>
        <div class="clock-wrap">
          <span class="clock-date">{{ timeLabel.date }}</span>
          <span class="clock-time">{{ timeLabel.time }}</span>
        </div>
      </div>
    </header>

    <div class="kpi-row">
      <KpiCard
        v-for="card in visibleKpiCards"
        :key="card.key"
        :label="card.label"
        :value="card.value"
        :desc="card.desc"
      />
    </div>

    <div class="cockpit-main">
      <aside class="left-zone">
        <template v-for="widget in leftWidgets" :key="widget.key">
          <CockpitPanel v-if="widget.key === 'trend'" :title="widget.title">
            <TrendWidget :dates="trendData.dates" :rates="trendData.rates" :predicted="trendData.predicted" />
          </CockpitPanel>

          <CockpitPanel v-else-if="widget.key === 'progress'" :title="widget.title">
            <MetricProgressWidget :items="metricProgress" />
          </CockpitPanel>
        </template>
      </aside>

      <section class="center-zone panel-frame" v-if="showCenterGauge">
        <div class="panel-title center-title">{{ centerTitle }}</div>
        <GaugeWidget :value="dashboardData.completedRate" />
        <div class="center-footnote">
          <div>目标值 {{ threshold.minRate }}%</div>
          <div>预警阈值 {{ threshold.overdueDays }} 天</div>
        </div>
      </section>
      <section class="center-zone panel-frame empty-center" v-else>
        <div class="panel-title center-title">中间主视图已隐藏</div>
      </section>

      <aside class="right-zone">
        <template v-for="widget in rightWidgets" :key="widget.key">
          <CockpitPanel v-if="widget.key === 'rank'" :title="widget.title">
            <RankWidget :departments="heatData.departments" :metrics="heatData.metrics" :values="heatData.values" />
          </CockpitPanel>

          <CockpitPanel v-else-if="widget.key === 'threshold'" :title="widget.title" block-class="threshold-panel">
            <ThresholdConfigWidget
              :overdue-days="threshold.overdueDays"
              :min-rate="threshold.minRate"
              @update:overdue-days="(val) => (threshold.overdueDays = val)"
              @update:min-rate="(val) => (threshold.minRate = val)"
              @save="saveThreshold"
            />
          </CockpitPanel>
        </template>
      </aside>
    </div>

    <section class="bottom-zone panel-frame" v-if="showBottomHeatmap">
      <div class="panel-title">{{ bottomTitle }}</div>
      <HeatmapWidget :departments="heatData.departments" :metrics="heatData.metrics" :values="heatData.values" />
    </section>

    <LayoutCustomizer
      v-model="layoutDrawerVisible"
      :kpi-layout="kpiLayout"
      :widget-layout="widgetLayout"
      @toggle-kpi="toggleKpi"
      @move-kpi="moveKpi"
      @reorder-kpi="reorderKpi"
      @toggle-widget="toggleWidget"
      @move-widget="moveWidget"
      @reorder-widget="reorderWidget"
      @reset-layout="resetLayout"
    />
  </section>
</template>

<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDashboard, fetchFusionOptions, fetchHeatmap, fetchTrend } from '../api/dashboard'
import { storeToRefs } from 'pinia'
import KpiCard from '../components/dashboard/KpiCard.vue'
import CockpitPanel from '../components/dashboard/CockpitPanel.vue'
import LayoutCustomizer from '../components/dashboard/LayoutCustomizer.vue'
import { useDashboardLayoutStore } from '../store/dashboardLayout'

const GaugeWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/GaugeWidget.vue'))
const TrendWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/TrendWidget.vue'))
const RankWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/RankWidget.vue'))
const HeatmapWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/HeatmapWidget.vue'))
const MetricProgressWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/MetricProgressWidget.vue'))
const ThresholdConfigWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/ThresholdConfigWidget.vue'))

let clockTimer
let autoRefreshTimer
const layoutDrawerVisible = ref(false)
const loadingData = ref(false)
const pendingReload = ref(false)
const requestVersion = ref(0)
const lastWarningMessage = ref('')

const dashboardLayoutStore = useDashboardLayoutStore()
const { sortedWidgetLayout: widgetLayout, sortedKpiLayout: kpiLayout } = storeToRefs(dashboardLayoutStore)

const selectedFusionTaskId = ref('')
const fusionOptions = ref([])

const dashboardData = reactive({
  fusionTaskId: '',
  targetTable: '-',
  completedRate: 0,
  overdueCount: 0,
  departmentRank: 0,
  totalRows: 0,
  message: ''
})

const trendData = reactive({
  dates: [],
  rates: [],
  predicted: []
})

const heatData = reactive({
  departments: [],
  metrics: [],
  values: []
})

const timeLabel = reactive({
  date: '',
  time: ''
})

const threshold = reactive({
  overdueDays: 7,
  minRate: 85
})

const kpiCards = computed(() => [
  {
    key: 'totalRows',
    label: '融合总记录',
    value: formatNumber(dashboardData.totalRows),
    desc: `分析表：${dashboardData.targetTable || '-'}`
  },
  {
    key: 'completedRate',
    label: '整改完成率',
    value: `${dashboardData.completedRate}%`,
    desc: dashboardData.completedRate >= threshold.minRate ? '达到目标阈值' : '低于目标阈值'
  },
  {
    key: 'overdueCount',
    label: '待处理疑似空值',
    value: formatNumber(dashboardData.overdueCount),
    desc: `预警阈值：${threshold.overdueDays} 天`
  },
  {
    key: 'departmentRank',
    label: '融合任务体量排名',
    value: dashboardData.departmentRank ? `#${dashboardData.departmentRank}` : '-',
    desc: '按完成融合任务记录量排序'
  }
])

const visibleKpiCards = computed(() => {
  const lookup = new Map(kpiCards.value.map((item) => [item.key, item]))
  return kpiLayout.value
    .filter((it) => it.enabled)
    .map((it) => lookup.get(it.key))
    .filter(Boolean)
})

const leftWidgets = computed(() => widgetLayout.value.filter((it) => it.enabled && it.region === 'left'))
const rightWidgets = computed(() => widgetLayout.value.filter((it) => it.enabled && it.region === 'right'))
const centerWidget = computed(() => widgetLayout.value.find((it) => it.region === 'center'))
const bottomWidget = computed(() => widgetLayout.value.find((it) => it.region === 'bottom'))
const showCenterGauge = computed(() => centerWidget.value?.enabled && centerWidget.value?.key === 'gauge')
const showBottomHeatmap = computed(() => bottomWidget.value?.enabled && bottomWidget.value?.key === 'heatmap')
const centerTitle = computed(() => centerWidget.value?.title || '整改完成总览')
const bottomTitle = computed(() => bottomWidget.value?.title || '融合内容质量热力图')

const metricProgress = computed(() => {
  if (!heatData.metrics.length || !heatData.values.length) {
    return [
      { name: '完成率', value: dashboardData.completedRate || 0 },
      { name: '空值率', value: 0 },
      { name: '重复率', value: 0 },
      { name: '闭环率', value: 0 }
    ]
  }

  return heatData.metrics.map((name, metricIndex) => {
    const metricValues = heatData.values
      .filter((it) => Array.isArray(it) && it[1] === metricIndex)
      .map((it) => Number(it[2]) || 0)
    const avg = metricValues.length
      ? Math.round(metricValues.reduce((sum, val) => sum + val, 0) / metricValues.length)
      : 0
    return { name, value: avg }
  })
})

function formatNumber(value) {
  const num = Number(value || 0)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : '0'
}

function updateClock() {
  const now = new Date()
  const date = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short'
  })
  const time = now.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
  timeLabel.date = date
  timeLabel.time = time
}


async function loadFusionOptions(options = {}) {
  const { data } = await fetchFusionOptions(options)
  fusionOptions.value = data.data || []
  if (!selectedFusionTaskId.value && fusionOptions.value.length > 0) {
    selectedFusionTaskId.value = fusionOptions.value[0].id
  }
}

async function loadAllData(options = {}) {
  const forceRefresh = Boolean(options.forceRefresh)
  if (loadingData.value) {
    pendingReload.value = pendingReload.value || forceRefresh
    return
  }

  loadingData.value = true
  const currentVersion = ++requestVersion.value
  try {
    const fusionTaskId = selectedFusionTaskId.value || undefined
    const [dashboardRes, trendRes, heatmapRes] = await Promise.all([
      fetchDashboard(fusionTaskId, { forceRefresh }),
      fetchTrend(fusionTaskId, { forceRefresh }),
      fetchHeatmap(fusionTaskId, { forceRefresh })
    ])

    if (currentVersion !== requestVersion.value) {
      return
    }

    Object.assign(dashboardData, dashboardRes.data || {})
    Object.assign(trendData, trendRes.data || { dates: [], rates: [], predicted: [] })
    Object.assign(heatData, heatmapRes.data || { departments: [], metrics: [], values: [] })

    if (dashboardData.message && dashboardData.message !== lastWarningMessage.value) {
      lastWarningMessage.value = dashboardData.message
      ElMessage.warning(dashboardData.message)
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '大屏数据加载失败')
  } finally {
    loadingData.value = false
    if (pendingReload.value) {
      pendingReload.value = false
      await loadAllData({ forceRefresh: true })
    }
  }
}

async function handleFusionChange() {
  await loadAllData({ forceRefresh: true })
}

async function handleManualRefresh() {
  await loadAllData({ forceRefresh: true })
}

function saveThreshold() {
  ElMessage.success(`阈值已保存：超期 ${threshold.overdueDays} 天，目标整改率 ${threshold.minRate}%`)
}

function toggleWidget(key, enabled) {
  dashboardLayoutStore.toggleWidget(key, enabled)
}

function moveWidget(key, direction) {
  dashboardLayoutStore.moveWidget(key, direction)
}

function toggleKpi(key, enabled) {
  dashboardLayoutStore.toggleKpi(key, enabled)
}

function moveKpi(key, direction) {
  dashboardLayoutStore.moveKpi(key, direction)
}

function resetLayout() {
  dashboardLayoutStore.resetLayout()
}

function reorderKpi(dragKey, targetKey) {
  dashboardLayoutStore.reorderKpi(dragKey, targetKey)
}

function reorderWidget(dragKey, targetKey) {
  dashboardLayoutStore.reorderWidget(dragKey, targetKey)
}

function startAutoRefresh() {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
  }
  autoRefreshTimer = window.setInterval(() => {
    if (document.visibilityState !== 'visible') return
    loadAllData({ forceRefresh: true })
  }, 60000)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    loadAllData({ forceRefresh: true })
  }
}

onMounted(async () => {
  updateClock()
  clockTimer = window.setInterval(updateClock, 1000)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  await loadFusionOptions()
  await loadAllData()

  startAutoRefresh()
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (clockTimer) window.clearInterval(clockTimer)
  if (autoRefreshTimer) window.clearInterval(autoRefreshTimer)
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@500;700&family=Teko:wght@400;600&display=swap');

.cockpit-page {
  position: relative;
  min-height: calc(100vh - 120px);
  color: #d7ecff;
  border-radius: 12px;
  overflow: hidden;
  background:
    radial-gradient(circle at 20% 18%, rgba(45, 94, 255, 0.32), transparent 38%),
    radial-gradient(circle at 85% 12%, rgba(2, 184, 255, 0.28), transparent 34%),
    linear-gradient(160deg, #050d27 0%, #08163d 46%, #041026 100%);
  padding: 16px;
}

.glow-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.26;
  background-image:
    linear-gradient(rgba(59, 142, 255, 0.14) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 142, 255, 0.14) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle at 50% 40%, black 35%, transparent 90%);
}

.panel-frame {
  position: relative;
  background: linear-gradient(180deg, rgba(11, 38, 81, 0.62) 0%, rgba(8, 27, 62, 0.66) 100%);
  border: 1px solid rgba(58, 148, 255, 0.35);
  border-radius: 10px;
  box-shadow: inset 0 0 24px rgba(34, 128, 255, 0.12), 0 0 22px rgba(9, 54, 128, 0.35);
  backdrop-filter: blur(2px);
}

.panel-frame::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 10px;
  pointer-events: none;
  border: 1px solid rgba(33, 208, 255, 0.15);
}

.cockpit-header {
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  margin-bottom: 14px;
}

.header-title-wrap {
  min-width: 0;
}

.header-title {
  margin: 0;
  font-family: 'Orbitron', 'Teko', 'Microsoft YaHei', sans-serif;
  letter-spacing: 0.08em;
  font-size: 30px;
  font-weight: 700;
  color: #eff8ff;
  text-shadow: 0 0 14px rgba(41, 187, 255, 0.55);
}

.header-subtitle {
  margin: 6px 0 0;
  color: #8ec8ff;
  font-size: 12px;
  letter-spacing: 0.18em;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.fusion-select {
  width: 320px;
}

:deep(.fusion-select .el-select__wrapper) {
  background: rgba(11, 30, 64, 0.92);
  box-shadow: inset 0 0 0 1px rgba(67, 162, 255, 0.4);
}

.ghost-btn {
  border: 1px solid rgba(70, 173, 255, 0.55);
  background: linear-gradient(90deg, rgba(25, 78, 176, 0.4), rgba(18, 123, 196, 0.35));
  color: #d9efff;
}

.ghost-btn:hover {
  color: #ffffff;
  border-color: rgba(121, 212, 255, 0.95);
  background: linear-gradient(90deg, rgba(25, 98, 199, 0.72), rgba(8, 149, 255, 0.68));
}

.clock-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
  font-family: 'Orbitron', 'Teko', 'Microsoft YaHei', sans-serif;
}

.clock-date {
  color: #96caff;
  font-size: 12px;
}

.clock-time {
  color: #f3fbff;
  font-size: 19px;
  letter-spacing: 0.1em;
}

.kpi-row {
  z-index: 1;
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.kpi-card {
  padding: 12px 14px;
}

.kpi-label {
  font-size: 12px;
  color: #9ccfff;
  letter-spacing: 0.08em;
}

.kpi-value {
  margin: 6px 0;
  font-family: 'Orbitron', 'Teko', 'Microsoft YaHei', sans-serif;
  font-size: 34px;
  color: #2ee5ff;
  text-shadow: 0 0 14px rgba(66, 215, 255, 0.52);
  line-height: 1;
}

.kpi-desc {
  font-size: 12px;
  color: #79b2ea;
}

.cockpit-main {
  z-index: 1;
  position: relative;
  display: grid;
  grid-template-columns: 1.15fr 1.35fr 1.1fr;
  gap: 12px;
  min-height: 480px;
}

.left-zone,
.right-zone {
  display: grid;
  grid-template-rows: 1fr 1fr;
  gap: 12px;
}

.center-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px 14px 10px;
}

.empty-center {
  justify-content: center;
  color: #80b5ea;
  min-height: 360px;
}

.panel-block {
  padding: 10px 12px;
}

.panel-title {
  font-size: 13px;
  color: #9fd3ff;
  letter-spacing: 0.08em;
  margin-bottom: 6px;
}

.center-title {
  align-self: stretch;
  text-align: center;
}

.center-footnote {
  width: 100%;
  display: flex;
  justify-content: space-around;
  color: #88bdf1;
  font-size: 12px;
  letter-spacing: 0.05em;
}

.threshold-panel {
  padding-bottom: 6px;
}

.bottom-zone {
  z-index: 1;
  position: relative;
  margin-top: 12px;
  padding: 12px 14px;
}

@media (max-width: 1360px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cockpit-main {
    grid-template-columns: 1fr;
  }

  .left-zone,
  .right-zone {
    grid-template-rows: auto;
  }

}

@media (max-width: 840px) {
  .cockpit-page {
    padding: 10px;
    border-radius: 0;
  }

  .cockpit-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .header-title {
    font-size: 24px;
  }

  .header-actions {
    width: 100%;
  }

  .fusion-select {
    width: 100%;
  }

  .kpi-row {
    grid-template-columns: 1fr;
  }

}
</style>
