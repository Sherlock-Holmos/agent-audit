import { defineStore } from 'pinia'

export type WidgetRegion = 'left' | 'center' | 'right' | 'bottom'

export interface WidgetLayoutItem {
  key: string
  title: string
  region: WidgetRegion
  enabled: boolean
  order: number
}

export interface KpiLayoutItem {
  key: string
  label: string
  enabled: boolean
  order: number
}

const WIDGET_LAYOUT_KEY = 'dashboard_widget_layout_v1'
const KPI_LAYOUT_KEY = 'dashboard_kpi_layout_v1'

const defaultWidgetLayout: WidgetLayoutItem[] = [
  { key: 'trend', title: '整改趋势与预测', region: 'left', enabled: true, order: 1 },
  { key: 'progress', title: '指标执行进度', region: 'left', enabled: true, order: 2 },
  { key: 'gauge', title: '整改完成总览', region: 'center', enabled: true, order: 1 },
  { key: 'rank', title: '分组完成率排行', region: 'right', enabled: true, order: 1 },
  { key: 'threshold', title: '预警阈值设置', region: 'right', enabled: true, order: 2 },
  { key: 'heatmap', title: '融合内容质量热力图', region: 'bottom', enabled: true, order: 1 }
]

const defaultKpiLayout: KpiLayoutItem[] = [
  { key: 'totalRows', label: '融合总记录', enabled: true, order: 1 },
  { key: 'completedRate', label: '整改完成率', enabled: true, order: 2 },
  { key: 'overdueCount', label: '待处理疑似空值', enabled: true, order: 3 },
  { key: 'departmentRank', label: '融合任务体量排名', enabled: true, order: 4 }
]

function sortByOrder<T extends { order: number }>(list: T[]) {
  return [...list].sort((a, b) => a.order - b.order)
}

function normalizeOrders<T extends { order: number }>(list: T[]) {
  sortByOrder(list).forEach((item, index) => {
    item.order = index + 1
  })
}

function loadWidgetLayout(): WidgetLayoutItem[] {
  const raw = localStorage.getItem(WIDGET_LAYOUT_KEY)
  if (!raw) return defaultWidgetLayout
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return defaultWidgetLayout

    const merged = defaultWidgetLayout.map((item) => {
      const hit = parsed.find((it: WidgetLayoutItem) => it.key === item.key)
      return hit ? { ...item, ...hit } : item
    })
    return sortByOrder(merged)
  } catch {
    return defaultWidgetLayout
  }
}

function loadKpiLayout(): KpiLayoutItem[] {
  const raw = localStorage.getItem(KPI_LAYOUT_KEY)
  if (!raw) return defaultKpiLayout
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return defaultKpiLayout

    const merged = defaultKpiLayout.map((item) => {
      const hit = parsed.find((it: KpiLayoutItem) => it.key === item.key)
      return hit ? { ...item, ...hit } : item
    })
    return sortByOrder(merged)
  } catch {
    return defaultKpiLayout
  }
}

function persistWidgetLayout(layout: WidgetLayoutItem[]) {
  localStorage.setItem(WIDGET_LAYOUT_KEY, JSON.stringify(layout))
}

function persistKpiLayout(layout: KpiLayoutItem[]) {
  localStorage.setItem(KPI_LAYOUT_KEY, JSON.stringify(layout))
}

export const useDashboardLayoutStore = defineStore('dashboardLayout', {
  state: () => ({
    widgetLayout: loadWidgetLayout() as WidgetLayoutItem[],
    kpiLayout: loadKpiLayout() as KpiLayoutItem[]
  }),
  getters: {
    sortedWidgetLayout: (state) => sortByOrder(state.widgetLayout),
    sortedKpiLayout: (state) => sortByOrder(state.kpiLayout)
  },
  actions: {
    toggleWidget(key: string, enabled: boolean) {
      const idx = this.widgetLayout.findIndex((it) => it.key === key)
      if (idx === -1) return
      this.widgetLayout[idx].enabled = enabled
      persistWidgetLayout(this.widgetLayout)
    },
    toggleKpi(key: string, enabled: boolean) {
      const idx = this.kpiLayout.findIndex((it) => it.key === key)
      if (idx === -1) return
      this.kpiLayout[idx].enabled = enabled
      persistKpiLayout(this.kpiLayout)
    },
    moveWidget(key: string, direction: 'up' | 'down') {
      const item = this.widgetLayout.find((it) => it.key === key)
      if (!item) return
      const sameRegion = sortByOrder(this.widgetLayout.filter((it) => it.region === item.region))
      const index = sameRegion.findIndex((it) => it.key === key)
      if (index === -1) return
      const targetIndex = direction === 'up' ? index - 1 : index + 1
      if (targetIndex < 0 || targetIndex >= sameRegion.length) return

      const target = sameRegion[targetIndex]
      const currentOrder = item.order
      item.order = target.order
      target.order = currentOrder
      persistWidgetLayout(this.widgetLayout)
    },
    moveKpi(key: string, direction: 'up' | 'down') {
      const sorted = sortByOrder(this.kpiLayout)
      const index = sorted.findIndex((it) => it.key === key)
      if (index === -1) return
      const targetIndex = direction === 'up' ? index - 1 : index + 1
      if (targetIndex < 0 || targetIndex >= sorted.length) return

      const current = sorted[index]
      const target = sorted[targetIndex]
      const tmp = current.order
      current.order = target.order
      target.order = tmp
      persistKpiLayout(this.kpiLayout)
    },
    reorderKpi(dragKey: string, targetKey: string) {
      if (dragKey === targetKey) return
      const sorted = sortByOrder(this.kpiLayout)
      const dragIndex = sorted.findIndex((it) => it.key === dragKey)
      const targetIndex = sorted.findIndex((it) => it.key === targetKey)
      if (dragIndex < 0 || targetIndex < 0) return

      const [dragged] = sorted.splice(dragIndex, 1)
      sorted.splice(targetIndex, 0, dragged)
      normalizeOrders(sorted)
      persistKpiLayout(this.kpiLayout)
    },
    reorderWidget(dragKey: string, targetKey: string) {
      if (dragKey === targetKey) return
      const dragged = this.widgetLayout.find((it) => it.key === dragKey)
      const target = this.widgetLayout.find((it) => it.key === targetKey)
      if (!dragged || !target) return
      if (dragged.region !== target.region) return

      const sameRegion = sortByOrder(this.widgetLayout.filter((it) => it.region === dragged.region))
      const dragIndex = sameRegion.findIndex((it) => it.key === dragKey)
      const targetIndex = sameRegion.findIndex((it) => it.key === targetKey)
      if (dragIndex < 0 || targetIndex < 0) return

      const [item] = sameRegion.splice(dragIndex, 1)
      sameRegion.splice(targetIndex, 0, item)
      normalizeOrders(sameRegion)
      persistWidgetLayout(this.widgetLayout)
    },
    resetLayout() {
      this.widgetLayout = defaultWidgetLayout.map((it) => ({ ...it }))
      this.kpiLayout = defaultKpiLayout.map((it) => ({ ...it }))
      persistWidgetLayout(this.widgetLayout)
      persistKpiLayout(this.kpiLayout)
    }
  }
})
