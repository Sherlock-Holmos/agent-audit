<template>
  <section class="cockpit-page">
    <div class="glow-grid"></div>

    <header class="cockpit-header panel-frame">
      <div class="header-title-wrap">
        <h1 class="header-title">审计整改智能驾驶舱</h1>
        <p class="header-subtitle">{{ scopeSubtitle }}</p>
      </div>

      <div class="header-actions">
        <div class="data-badge">{{ scopeBadgeLabel }} · {{ snapshotSummary }}</div>
        <el-button class="ghost-btn" :loading="loading" @click="handleManualRefresh">同步快照</el-button>
        <div class="clock-wrap">
          <span class="clock-date">{{ timeLabel.date }}</span>
          <span class="clock-time">{{ timeLabel.time }}</span>
        </div>
      </div>
    </header>

    <div class="kpi-row">
      <KpiCard
        v-for="card in statCards"
        :key="card.key"
        :label="card.label"
        :value="card.value"
        :desc="card.desc"
      />
    </div>

    <div class="cockpit-main">
      <aside class="left-zone">
        <CockpitPanel :title="rankingTitle" block-class="ranking-panel">
          <el-table
            class="cockpit-table"
            :data="unitRanking"
            :loading="loading"
            border
            stripe
            size="small"
            :row-key="(row) => row.unit"
            :header-cell-style="tableHeaderCellStyle"
            :cell-style="tableCellStyle"
            :empty-text="'暂无单位整改数据'"
          >
            <el-table-column type="index" label="排名" width="72" />
            <el-table-column prop="unit" label="单位" min-width="168" />
            <el-table-column prop="total" label="任务数" width="96" align="right" />
            <el-table-column prop="done" label="已完成" width="96" align="right" />
            <el-table-column label="完成率" min-width="168">
              <template #default="scope">
                <div class="progress-cell">
                  <el-progress
                    :percentage="scope.row.rate"
                    :stroke-width="12"
                    :status="scope.row.rate >= 80 ? 'success' : scope.row.rate >= 50 ? 'warning' : 'exception'"
                  />
                  <span class="progress-value">{{ scope.row.rate }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </CockpitPanel>

        <CockpitPanel title="重点督办清单" block-class="focus-panel">
          <div v-if="focusIssues.length" class="focus-list">
            <article v-for="issue in focusIssues" :key="issue.id" class="focus-item">
              <div class="focus-top">
                <span class="focus-title">{{ issue.title || issue.summary || '未命名问题' }}</span>
                <el-tag :type="riskTagType(issue.level)" effect="dark" size="small">{{ issue.level || '中' }}</el-tag>
              </div>
              <div class="focus-meta">
                <span>{{ resolveIssueUnit(issue) }}</span>
                <span>{{ formatTime(resolveIssueTime(issue)) }}</span>
              </div>
              <div class="focus-desc">{{ issue.description || issue.summary || issue.status || '暂无摘要' }}</div>
            </article>
          </div>
          <el-empty v-else description="暂无重点督办问题" />
        </CockpitPanel>
      </aside>

      <section class="center-zone panel-frame">
        <div class="panel-title center-title">{{ centerTitle }}</div>
        <GaugeWidget :value="completionRate" />
        <div class="center-footnote">
          <div>问题总数 {{ formatNumber(issueTotal) }}</div>
          <div>任务总数 {{ formatNumber(taskTotal) }}</div>
          <div>逾期任务 {{ formatNumber(overdueTaskTotal) }}</div>
        </div>
      </section>

      <aside class="right-zone">
        <CockpitPanel title="风险等级分布" block-class="risk-panel">
          <div v-for="item in riskDistribution" :key="item.level" class="risk-wrap">
            <div class="risk-line">
              <span>{{ item.level }}</span>
              <span>{{ item.count }} 项</span>
            </div>
            <el-progress
              :percentage="item.percent"
              :stroke-width="16"
              :status="item.status"
              :show-text="false"
            />
          </div>
          <div class="risk-total">总问题数 {{ formatNumber(issueTotal) }}</div>
        </CockpitPanel>

        <CockpitPanel title="近期整改动态" block-class="timeline-panel">
          <el-timeline class="cockpit-timeline">
            <el-timeline-item
              v-for="entry in latestTimeline"
              :key="entry.id"
              :timestamp="entry.time"
              :type="entry.type"
              size="large"
            >
              {{ entry.text }}
            </el-timeline-item>
          </el-timeline>
        </CockpitPanel>
      </aside>
    </div>

    <section class="process-zone panel-frame">
      <div class="panel-title">流程排名进度</div>
      <div class="process-grid">
        <div class="process-card">
          <div class="process-card-title">{{ processRankingTitle }}</div>
          <RankWidget
            :departments="processRankingDepartments"
            :metrics="processRankingMetrics"
            :values="processRankingValues"
          />
        </div>
        <div class="process-card">
          <div class="process-card-title">任务进度结构</div>
          <MetricProgressWidget :items="processProgressItems" />
        </div>
      </div>
    </section>

    <section class="bottom-zone panel-frame">
      <div class="panel-title">{{ issueListTitle }}</div>
      <el-table
        class="cockpit-table cockpit-table-bottom"
        :data="issueRows"
        :loading="loading"
        border
        stripe
        size="small"
        :row-key="(row) => row.id"
        :header-cell-style="tableHeaderCellStyle"
        :cell-style="tableCellStyle"
        :empty-text="'暂无问题数据'"
      >
        <el-table-column prop="title" label="问题 / 任务" min-width="220">
          <template #default="scope">
            <div class="issue-cell">
              <span class="issue-title">{{ scope.row.title || scope.row.summary || '未命名问题' }}</span>
              <span class="issue-sub">{{ scope.row.issueId ? `关联问题 #${scope.row.issueId}` : '直接问题记录' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="96" align="center">
          <template #default="scope">
            <el-tag :type="riskTagType(scope.row.level)" effect="dark" size="small">{{ scope.row.level || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)" effect="plain" size="small">{{ scope.row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" min-width="150">
          <template #default="scope">{{ resolveIssueUnit(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="scope">{{ formatTime(resolveIssueTime(scope.row)) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import KpiCard from '../components/dashboard/KpiCard.vue'
import CockpitPanel from '../components/dashboard/CockpitPanel.vue'
import { ROLES } from '../constants/rbac'
import { getCurrentRole, getCurrentUnit } from '../utils/currentUser'

const GaugeWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/GaugeWidget.vue'))
const RankWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/RankWidget.vue'))
const MetricProgressWidget = defineAsyncComponent(() => import('../components/dashboard/widgets/MetricProgressWidget.vue'))

const { snapshot, refreshSnapshot, loading } = useRectificationSnapshot()
const currentRole = computed(() => getCurrentRole())
const currentUnit = computed(() => String(getCurrentUnit() || '').trim())
const isUnitScopedView = computed(() => currentRole.value === ROLES.ORG_ADMIN)

const scopeSubtitle = computed(() =>
  isUnitScopedView.value ? `${currentUnit.value} · 整改态势总览` : '融合全局仪表盘内容 · 智能态势总览'
)
const scopeBadgeLabel = computed(() => (isUnitScopedView.value ? `${currentUnit.value}快照` : '全局快照'))
const rankingTitle = computed(() => (isUnitScopedView.value ? '本单位整改进度（按完成率）' : '单位整改排名（按完成率）'))
const centerTitle = computed(() => (isUnitScopedView.value ? '本单位整改完成率' : '全局整改完成率'))
const processRankingTitle = computed(() => (isUnitScopedView.value ? '本单位流程完成率' : '单位流程完成率排行'))
const issueListTitle = computed(() => (isUnitScopedView.value ? '本单位问题清单' : '全局问题清单'))

let clockTimer
let autoRefreshTimer

const timeLabel = reactive({
  date: '',
  time: ''
})

const scopedIssues = computed(() => {
  const issues = snapshot.value.issues || []
  if (!isUnitScopedView.value || !currentUnit.value) {
    return issues
  }
  return issues.filter((item) => isSameUnit(resolveIssueUnit(item), currentUnit.value))
})

const scopedTasks = computed(() => {
  const tasks = snapshot.value.tasks || []
  if (!isUnitScopedView.value || !currentUnit.value) {
    return tasks
  }
  return tasks.filter((item) => isSameUnit(resolveTaskUnit(item), currentUnit.value))
})

const issueTotal = computed(() => scopedIssues.value.length)
const taskTotal = computed(() => scopedTasks.value.length)

const completedTaskTotal = computed(() => scopedTasks.value.filter((item) => item.status === '已完成').length)

const overdueTaskTotal = computed(() =>
  scopedTasks.value.filter((item) => {
    if (item.status === '已完成' || !item.deadline) return false
    return new Date(item.deadline).getTime() < Date.now()
  }).length
)

const completionRate = computed(() => {
  if (!taskTotal.value) return 0
  return Math.round((completedTaskTotal.value / taskTotal.value) * 100)
})

const snapshotSummary = computed(() => {
  return `${formatNumber(issueTotal.value)} 问题 / ${formatNumber(taskTotal.value)} 任务 / ${completionRate.value}% 完成率`
})

const statCards = computed(() => [
  {
    key: 'issues',
    label: '问题总数',
    value: formatNumber(issueTotal.value),
    desc: '本期纳入整改'
  },
  {
    key: 'tasks',
    label: '任务总数',
    value: formatNumber(taskTotal.value),
    desc: '主任务 + 子任务'
  },
  {
    key: 'completed',
    label: '已完成任务',
    value: formatNumber(completedTaskTotal.value),
    desc: `完成率 ${completionRate.value}%`
  },
  {
    key: 'overdue',
    label: '逾期任务',
    value: formatNumber(overdueTaskTotal.value),
    desc: '需重点督办'
  }
])

const unitRanking = computed(() => {
  const grouped = new Map()
  const tasks = scopedTasks.value

  tasks.forEach((task) => {
    const unit = resolveTaskUnit(task)
    if (!grouped.has(unit)) {
      grouped.set(unit, { unit, total: 0, done: 0, rate: 0 })
    }
    const row = grouped.get(unit)
    row.total += 1
    if (task.status === '已完成') {
      row.done += 1
    }
    row.rate = row.total ? Math.round((row.done / row.total) * 100) : 0
  })

  return Array.from(grouped.values()).sort((left, right) => {
    if (right.rate !== left.rate) return right.rate - left.rate
    if (right.total !== left.total) return right.total - left.total
    return left.unit.localeCompare(right.unit, 'zh-CN')
  })
})

const processRanking = computed(() => {
  const grouped = new Map()
  const tasks = scopedTasks.value

  tasks.forEach((task) => {
    const unit = resolveTaskUnit(task)
    if (!grouped.has(unit)) {
      grouped.set(unit, { unit, total: 0, done: 0, overdue: 0, progressSum: 0 })
    }
    const row = grouped.get(unit)
    row.total += 1
    if (task.status === '已完成') {
      row.done += 1
    }
    if (task.status === '已逾期' || (task.deadline && task.status !== '已完成' && new Date(task.deadline).getTime() < Date.now())) {
      row.overdue += 1
    }
    row.progressSum += resolveTaskProgress(task)
  })

  return Array.from(grouped.values())
    .map((row) => ({
      ...row,
      rate: row.total ? Math.round((row.done / row.total) * 100) : 0,
      progress: row.total ? Math.round(row.progressSum / row.total) : 0
    }))
    .sort((left, right) => {
      if (right.progress !== left.progress) return right.progress - left.progress
      if (right.rate !== left.rate) return right.rate - left.rate
      if (right.total !== left.total) return right.total - left.total
      return left.unit.localeCompare(right.unit, 'zh-CN')
    })
})

const processRankingDepartments = computed(() => processRanking.value.map((row) => row.unit))
const processRankingMetrics = computed(() => ['完成率'])
const processRankingValues = computed(() =>
  processRanking.value.map((row, index) => [index, 0, row.rate])
)

const processProgressItems = computed(() => {
  const tasks = scopedTasks.value
  const total = tasks.length || 1
  const buckets = [
    { name: '已完成', count: 0 },
    { name: '整改中', count: 0 },
    { name: '待审核', count: 0 },
    { name: '已逾期', count: 0 }
  ]

  tasks.forEach((task) => {
    if (task.status === '已完成') {
      buckets[0].count += 1
    } else if (task.status === '待审核') {
      buckets[2].count += 1
    } else if (task.status === '已逾期' || (task.deadline && new Date(task.deadline).getTime() < Date.now())) {
      buckets[3].count += 1
    } else {
      buckets[1].count += 1
    }
  })

  return buckets.map((item) => ({
    name: item.name,
    value: Math.round((item.count / total) * 100)
  }))
})

const riskDistribution = computed(() => {
  const total = issueTotal.value || 1
  const levels = ['重大', '高', '中', '低']

  return levels.map((level) => {
    const count = scopedIssues.value.filter((item) => item.level === level).length
    return {
      level,
      count,
      percent: Math.round((count / total) * 100),
      status: level === '重大' || level === '高' ? 'exception' : level === '中' ? 'warning' : 'success'
    }
  })
})

const latestTimeline = computed(() => {
  const tasks = scopedTasks.value
    .slice()
    .sort((left, right) => new Date(resolveTaskTime(right)).getTime() - new Date(resolveTaskTime(left)).getTime())
    .slice(0, 8)

  return tasks.map((task) => ({
    id: task.id,
    time: formatTime(resolveTaskTime(task)),
    text: `${resolveTaskUnit(task)} · ${task.title || '未命名任务'} 当前状态：${task.status || '未知'}`,
    type: task.status === '已完成' ? 'success' : task.status === '待审核' ? 'warning' : 'primary'
  }))
})

const focusIssues = computed(() => {
  return scopedIssues.value
    .filter((item) => ['高', '重大'].includes(item.level) && item.status !== '已完成')
    .slice()
    .sort((left, right) => {
      const levelDiff = severityScore(right.level) - severityScore(left.level)
      if (levelDiff !== 0) return levelDiff
      return new Date(resolveIssueTime(right)).getTime() - new Date(resolveIssueTime(left)).getTime()
    })
    .slice(0, 6)
})

const issueRows = computed(() => {
  return scopedIssues.value
    .slice()
    .sort((left, right) => {
      const levelDiff = severityScore(right.level) - severityScore(left.level)
      if (levelDiff !== 0) return levelDiff
      return new Date(resolveIssueTime(right)).getTime() - new Date(resolveIssueTime(left)).getTime()
    })
    .slice(0, 12)
})

const tableHeaderCellStyle = {
  background: 'rgba(6, 19, 44, 0.95)',
  color: '#9fd3ff',
  borderColor: 'rgba(74, 164, 255, 0.24)'
}

const tableCellStyle = {
  background: 'rgba(4, 13, 33, 0.58)',
  color: '#d7ecff',
  borderColor: 'rgba(74, 164, 255, 0.14)'
}

function formatNumber(value) {
  const num = Number(value || 0)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : '0'
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function updateClock() {
  const now = new Date()
  timeLabel.date = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short'
  })
  timeLabel.time = now.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function resolveTaskUnit(task) {
  return task.unit || task.department || task.assignee || '未归属单位'
}

function resolveTaskTime(task) {
  return task.updatedAt || task.updated_at || task.createdAt || task.created_at || task.deadline || ''
}

function resolveTaskProgress(task) {
  const explicitProgress = Number(task.progress ?? task.percent ?? task.completionRate ?? task.rate)
  if (Number.isFinite(explicitProgress)) {
    return Math.max(0, Math.min(100, explicitProgress))
  }
  if (task.status === '已完成') return 100
  if (task.status === '待审核') return 75
  if (task.status === '整改中') return 55
  if (task.status === '已逾期') return 25
  return 0
}

function resolveIssueUnit(issue) {
  return issue.unit || issue.department || issue.owner || '未归属单位'
}

function resolveIssueTime(issue) {
  return issue.updatedAt || issue.updated_at || issue.createdAt || issue.created_at || issue.deadline || ''
}

function isSameUnit(left, right) {
  return String(left || '').trim() === String(right || '').trim()
}

function severityScore(level) {
  const map = {
    重大: 4,
    高: 3,
    中: 2,
    低: 1
  }
  return map[level] || 0
}

function riskTagType(level) {
  if (level === '重大') return 'danger'
  if (level === '高') return 'warning'
  if (level === '中') return 'info'
  return 'success'
}

function statusTagType(status) {
  if (status === '已完成') return 'success'
  if (status === '整改中' || status === '待审核') return 'warning'
  if (status === '已逾期') return 'danger'
  return 'info'
}

async function handleManualRefresh() {
  const ok = await refreshSnapshot()
  if (!ok) {
    ElMessage.error('快照刷新失败')
  }
}

function startAutoRefresh() {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
  }
  autoRefreshTimer = window.setInterval(() => {
    if (document.visibilityState !== 'visible') return
    refreshSnapshot()
  }, 60000)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    refreshSnapshot()
  }
}

onMounted(() => {
  updateClock()
  clockTimer = window.setInterval(updateClock, 1000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  startAutoRefresh()
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (clockTimer) window.clearInterval(clockTimer)
  if (autoRefreshTimer) window.clearInterval(autoRefreshTimer)
})
</script>

<style scoped>
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
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
}

.header-title-wrap {
  min-width: 0;
}

.header-title {
  margin: 0;
  font-family: 'Segoe UI', 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif;
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
  justify-content: flex-end;
}

.data-badge {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(70, 173, 255, 0.35);
  background: rgba(8, 25, 57, 0.72);
  color: #c8e9ff;
  font-size: 12px;
  letter-spacing: 0.08em;
  white-space: nowrap;
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
  font-family: 'Segoe UI', 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif;
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
  font-family: 'Segoe UI', 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif;
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
  grid-template-columns: 1.15fr 1.25fr 1.1fr;
  gap: 12px;
  min-height: 540px;
}

.left-zone,
.right-zone {
  display: grid;
  grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.center-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px 14px 10px;
  min-height: 520px;
}

.panel-block {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-title {
  font-size: 13px;
  color: #9fd3ff;
  letter-spacing: 0.08em;
  margin-bottom: 10px;
}

.center-title {
  align-self: stretch;
  text-align: center;
}

.center-footnote {
  width: 100%;
  display: flex;
  justify-content: space-around;
  gap: 12px;
  color: #88bdf1;
  font-size: 12px;
  letter-spacing: 0.05em;
  margin-top: 8px;
  flex-wrap: wrap;
}

.risk-panel,
.timeline-panel,
.ranking-panel,
.focus-panel {
  min-height: 0;
}

.cockpit-table {
  flex: 1;
  min-height: 0;
  background: transparent;
}

.cockpit-table :deep(.el-table),
.cockpit-table-bottom :deep(.el-table) {
  background: transparent;
  color: #d7ecff;
}

.cockpit-table :deep(.el-table__inner-wrapper::before),
.cockpit-table-bottom :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.cockpit-table :deep(.el-table__header-wrapper th),
.cockpit-table-bottom :deep(.el-table__header-wrapper th) {
  background: rgba(6, 19, 44, 0.95) !important;
  color: #9fd3ff;
  border-color: rgba(74, 164, 255, 0.24) !important;
}

.cockpit-table :deep(.el-table__body-wrapper td),
.cockpit-table-bottom :deep(.el-table__body-wrapper td) {
  background: rgba(4, 13, 33, 0.58) !important;
  color: #d7ecff;
  border-color: rgba(74, 164, 255, 0.14) !important;
}

.cockpit-table :deep(.el-table__row:hover > td),
.cockpit-table-bottom :deep(.el-table__row:hover > td) {
  background: rgba(16, 44, 89, 0.85) !important;
}

.cockpit-table :deep(.el-table__empty-text),
.cockpit-table-bottom :deep(.el-table__empty-text) {
  color: #8bbbe9;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-cell :deep(.el-progress) {
  flex: 1;
}

.progress-value {
  min-width: 44px;
  text-align: right;
  color: #8ed8ff;
  font-size: 12px;
}

.focus-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
  min-height: 0;
}

.focus-item {
  border: 1px solid rgba(80, 170, 255, 0.18);
  border-radius: 10px;
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(10, 28, 59, 0.82), rgba(5, 17, 36, 0.76));
}

.focus-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.focus-title {
  font-size: 13px;
  color: #f0f8ff;
  line-height: 1.4;
}

.focus-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  color: #8dbfe8;
  font-size: 12px;
}

.focus-desc {
  margin-top: 8px;
  color: #c7e4ff;
  font-size: 12px;
  line-height: 1.55;
}

.risk-wrap {
  margin-bottom: 14px;
}

.risk-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: #d7ecff;
  font-size: 12px;
}

.risk-total {
  margin-top: 8px;
  color: #8dbfe8;
  font-size: 12px;
  text-align: right;
}

.process-zone {
  z-index: 1;
  position: relative;
  margin-top: 12px;
  padding: 12px 14px 14px;
}

.process-grid {
  display: grid;
  grid-template-columns: 1.35fr 0.9fr;
  gap: 12px;
  align-items: stretch;
}

.process-card {
  min-width: 0;
  padding: 12px 12px 10px;
  border: 1px solid rgba(80, 170, 255, 0.18);
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(10, 28, 59, 0.82), rgba(5, 17, 36, 0.76));
}

.process-card-title {
  margin-bottom: 10px;
  color: #dff2ff;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.cockpit-timeline {
  padding-left: 4px;
}

.cockpit-timeline :deep(.el-timeline-item__timestamp) {
  color: #8dbfe8;
  font-size: 12px;
}

.bottom-zone {
  z-index: 1;
  position: relative;
  margin-top: 12px;
  padding: 12px 14px;
}

.issue-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.issue-title {
  color: #f1f9ff;
}

.issue-sub {
  color: #86b9e6;
  font-size: 12px;
}

@media (max-width: 1360px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cockpit-main {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .left-zone,
  .right-zone {
    grid-template-rows: auto;
  }

  .center-zone {
    min-height: 400px;
  }

  .process-grid {
    grid-template-columns: 1fr;
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
    justify-content: flex-start;
  }

  .kpi-row {
    grid-template-columns: 1fr;
  }

  .center-zone {
    min-height: 320px;
  }

  .process-zone {
    padding: 10px;
  }
}
</style>
