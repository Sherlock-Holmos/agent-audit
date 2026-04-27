<template>
  <section class="ai-analysis-page">
    <header class="hero-card">
      <div>
        <h2>AI 分析工作台</h2>
        <p>基于 LangChain 的整改风险诊断与行动建议，支持多轮上下文分析。</p>
      </div>
      <div class="hero-actions">
        <el-tag type="success" effect="plain">LangChain</el-tag>
        <el-tag type="info" effect="plain">多轮会话</el-tag>
        <el-button text type="primary" :loading="reportLoading" @click="generateReport">生成分析报告</el-button>
        <el-button text type="primary" @click="clearConversation">清空会话</el-button>
      </div>
    </header>

    <div class="analysis-layout">
      <el-card class="chat-panel" shadow="never">
        <template #header>
          <div class="chat-header">
            <span>分析对话</span>
            <span class="sub">
              最近{{ latestHistoryTurns }}轮上下文已自动携带 ·
              {{ reportModeLabel }} · {{ enableStream ? '流式回复' : '普通回复' }}
            </span>
          </div>
        </template>

        <div ref="chatBodyRef" class="chat-body">
          <div
            v-for="item in messages"
            :key="item.id"
            :class="['msg-row', item.role]"
          >
            <div class="msg-bubble">
              <div class="msg-text">{{ item.content }}</div>
              <div v-if="item.role === 'assistant' && (item.confidence || item.historyTurns >= 0)" class="msg-meta">
                <span v-if="item.confidence">置信度 {{ item.confidence }}</span>
                <span v-if="item.historyTurns >= 0">上下文 {{ item.historyTurns }} 轮</span>
              </div>
            </div>
          </div>

        </div>

        <div class="composer">
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            resize="none"
            placeholder="请输入你的分析问题。Enter 发送，Shift+Enter 换行。"
            @keydown.enter.exact.prevent="sendQuestion"
          />
          <div class="composer-actions">
            <el-button @click="copyLatestAnswer" :disabled="!latestAnswer">复制最新回答</el-button>
            <el-button
              v-if="loading && enableStream"
              type="danger"
              plain
              @click="stopStreaming"
            >
              停止生成
            </el-button>
            <el-button type="primary" :loading="loading" @click="sendQuestion">开始分析</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <el-drawer v-model="reportDrawerVisible" title="整改分析报告" size="50%" destroy-on-close>
      <div class="report-toolbar">
        <el-tag :type="missingReportSections.length ? 'warning' : 'success'" effect="plain">
          {{ missingReportSections.length ? `结构缺失 ${missingReportSections.length} 项` : '结构检查通过' }}
        </el-tag>
        <el-button type="primary" plain @click="downloadReport('md')" :disabled="!reportMarkdown">下载 Markdown</el-button>
        <el-button plain @click="downloadReport('json')" :disabled="!reportJson">下载 JSON</el-button>
      </div>
      <div v-if="missingReportSections.length" class="report-alert">
        缺失章节：{{ missingReportSections.join('、') }}
      </div>
      <pre class="report-preview">{{ reportMarkdown || '暂无报告内容。' }}</pre>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { chatWithAssistant, chatWithAssistantStream } from '../api/assistant'
import { ROLES, ROLE_HOME_ROUTE, isRoleAllowed, roleLabel } from '../constants/rbac'
import { getCurrentRole } from '../utils/currentUser'

const router = useRouter()

const reportMode = ref('weekly')
const enableStream = ref(true)
const AI_MODEL_SETTINGS_KEY = 'app:ai:model-settings:v1'
const AI_DIALOG_STRATEGY_KEY = 'app:ai:dialog-strategy:v1'
const question = ref('')
const loading = ref(false)
const streamController = ref(null)
const reportLoading = ref(false)
const chatBodyRef = ref(null)
const latestHistoryTurns = ref(0)
const reportDrawerVisible = ref(false)
const reportMarkdown = ref('')
const reportJson = ref(null)
const llmProvider = ref('mock')
const llmBaseUrl = ref('')
const llmApiKey = ref('')
const llmApiVersion = ref('')
const selectedModel = ref('gpt-4o-mini')
const modelOptions = ref([
  { label: 'GPT-4o-mini', value: 'gpt-4o-mini' },
  { label: 'GPT-4o', value: 'gpt-4o' },
  { label: 'DeepSeek-V3', value: 'deepseek-chat' }
])

const messages = ref([
  {
    id: 1,
    role: 'assistant',
    content: '你好，我是 LangChain 驱动的审计整改分析助手。你可以直接提问，或在系统设置中调整对话策略。',
    confidence: '',
    historyTurns: 0
  }
])

const requiredSections = {
  weekly: ['执行摘要', '关键风险点', '根因分析', '整改行动计划', '预期收益与跟踪指标'],
  executive: ['执行摘要', '关键结论', '管理建议', '下周重点'],
  deep: ['问题定义', '证据与现状', '根因分解', '整改路线图', '风险与依赖']
}

const assistantRouteIntents = [
  { route: '/dashboard', label: '数据仪表盘', aliases: ['驾驶舱', '总览', '看板', '概览', '仪表盘'], roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR] },
  { route: '/audit-admin/focus-issues', label: '重点问题督办', aliases: ['重点问题', '督办台账', '问题台账'], roles: [ROLES.AUDIT_ADMIN] },
  { route: '/org-admin/tasks/collaboration', label: '任务协同中心', aliases: ['任务协同', '协同任务', '任务协作'], roles: [ROLES.ORG_ADMIN] },
  { route: '/org-admin/report/submit', label: '整改总报告', aliases: ['整改汇报', '整改报告', '总报告'], roles: [ROLES.ORG_ADMIN] },
  { route: '/org-operator/tasks/claim', label: '子任务认领', aliases: ['任务认领', '认领任务', '领取任务'], roles: [ROLES.ORG_OPERATOR] },
  { route: '/org-operator/execution-center', label: '执行反馈中心', aliases: ['执行反馈', '整改填写', '整改填报', '反馈中心'], roles: [ROLES.ORG_OPERATOR] },
  { route: '/messages', label: '消息中心', aliases: ['消息中心', '消息', '通知'], roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR, ROLES.ORG_ADMIN, ROLES.ORG_OPERATOR] },
  { route: '/ai', label: 'AI分析工作台', aliases: ['ai分析', '分析工作台', '智能分析'], roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR, ROLES.ORG_ADMIN, ROLES.ORG_OPERATOR] }
]

const latestAnswer = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i -= 1) {
    if (messages.value[i].role === 'assistant') {
      return messages.value[i].content
    }
  }
  return ''
})

const missingReportSections = computed(() => {
  const markdown = reportMarkdown.value || ''
  const targets = requiredSections[reportMode.value] || []
  return targets.filter((section) => !markdown.includes(section))
})

const reportModeLabel = computed(() => {
  if (reportMode.value === 'executive') return '管理摘要'
  if (reportMode.value === 'deep') return '深度诊断'
  return '标准周报'
})

function detectRouteIntent(text) {
  const normalized = String(text || '').toLowerCase()
  return assistantRouteIntents.find((item) => item.aliases.some((alias) => normalized.includes(alias.toLowerCase())))
}

function shouldHandleWithLocalAction(text) {
  const q = String(text || '').trim()
  if (!q) return false
  const routeIntent = detectRouteIntent(q)
  if (routeIntent && (q.includes('打开') || q.includes('进入') || q.includes('跳转') || q.includes('去'))) {
    return true
  }
  return false
}

async function handleLocalAction(text) {
  const q = String(text || '').trim()
  const routeIntent = detectRouteIntent(q)
  if (!routeIntent) return ''
  await router.push(routeIntent.route)
  return `已为你打开 ${routeIntent.label}（${routeIntent.route}）`
}

function getAccessibleRouteIntents() {
  const currentRole = getCurrentRole()
  return assistantRouteIntents.filter((item) => isRoleAllowed(currentRole, item.roles))
}

function buildRouteCapabilityReply() {
  const currentRole = getCurrentRole()
  const routes = getAccessibleRouteIntents()
  const routeText = routes.map((item) => `${item.label}（${item.route}）`).join('、')
  return `当前账号角色为${roleLabel(currentRole)}，我可以帮你跳转到：${routeText}。你也可以直接说页面名称，我会自动打开。`
}

function buildCurrentTaskReply() {
  const currentRole = getCurrentRole()
  const homeRoute = ROLE_HOME_ROUTE[currentRole] || '/dashboard'
  return `当前已启用${reportModeLabel.value}，回复方式为${enableStream.value ? '流式' : '普通'}。如果你是想看系统待办，可以直接告诉我页面名称，我也可以直接跳转到${homeRoute}。`
}

function buildLocalCapabilityReply(text) {
  const q = String(text || '').trim()
  if (!q) return ''

  if (/(你可以|能)跳转(哪些|什么|到)?页面|可跳转页面|能去哪些页面|可以去哪些页面/.test(q)) {
    return buildRouteCapabilityReply()
  }

  if (/(当前|目前|现在).*(有什么|有哪些)任务|你.*任务|当前有什么任务/.test(q)) {
    return buildCurrentTaskReply()
  }

  return ''
}

function buildLlmConfigPayload() {
  const provider = (llmProvider.value || 'mock').trim().toLowerCase()
  const hasExternalConfig = Boolean(
    (selectedModel.value || '').trim() ||
    (llmApiKey.value || '').trim() ||
    (llmBaseUrl.value || '').trim() ||
    (llmApiVersion.value || '').trim()
  )

  return {
    provider:
      provider === 'azure' || provider === 'openai' || provider === 'custom'
        ? provider
        : hasExternalConfig
          ? 'custom'
          : 'mock',
    model: selectedModel.value || '',
    apiKey: llmApiKey.value || '',
    baseUrl: llmBaseUrl.value || '',
    apiVersion: llmApiVersion.value || ''
  }
}

function resolveAiError(error) {
  const errCode = error?.code || ''
  const rawMessage = String(error?.response?.data?.message || error?.message || '').trim()
  const lowerMessage = rawMessage.toLowerCase()
  const isInsufficientBalance =
    errCode === 'insufficient_balance' ||
    lowerMessage.includes('account balance is insufficient') ||
    lowerMessage.includes('code\': 30001') ||
    lowerMessage.includes('"code": 30001') ||
    lowerMessage.includes('code: 30001')
  const isModelNotFound =
    errCode === 'model_not_found' ||
    lowerMessage.includes('model does not exist') ||
    lowerMessage.includes('code\': 20012') ||
    lowerMessage.includes('"code": 20012') ||
    lowerMessage.includes('code: 20012')

  if (isInsufficientBalance) {
    return {
      toast: '模型平台账号余额不足，请先充值或更换可用账号。',
      bubble: '模型调用失败：当前模型平台账号余额不足（code: 30001）。\n请在模型平台充值后重试，或切换到有可用余额的 API Key。'
    }
  }

  if (isModelNotFound) {
    return {
      toast: `模型标识无效：${selectedModel.value || '未选择模型'}，请在系统设置中改为平台支持的 model id。`,
      bubble: `模型调用失败：当前模型标识“${selectedModel.value || '未选择模型'}”不存在。\n请到“系统设置 > 大模型选项管理”确认模型标识（value）是否为模型平台实际支持的 model id。`
    }
  }

  if (errCode === 'rate_limit') {
    return {
      toast: '请求过于频繁，请稍后重试',
      bubble: '请求过于频繁，请稍后重试。'
    }
  }
  if (errCode === 'stream_timeout') {
    return {
      toast: '本次生成超时，建议缩小问题范围后重试',
      bubble: '本次生成超时，请缩小问题范围后重试。'
    }
  }
  if (errCode === 'upstream_error') {
    return {
      toast: rawMessage || '模型服务暂时不可用，请稍后重试',
      bubble: rawMessage || '模型服务暂时不可用，请稍后再试。'
    }
  }

  return {
    toast: rawMessage || 'AI 分析请求失败',
    bubble: rawMessage || 'AI 服务暂时不可用，请稍后再试。'
  }
}

async function sendQuestion() {
  const rawQuestion = question.value.trim()
  const finalQuestion = rawQuestion
  if (!finalQuestion || loading.value) return

  const localReply = buildLocalCapabilityReply(rawQuestion)

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: localReply ? rawQuestion : finalQuestion,
    confidence: '',
    historyTurns: -1
  })

  question.value = ''

  if (localReply) {
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: localReply,
      confidence: '',
      historyTurns: -1
    })
    await nextTick()
    scrollToBottom()
    return
  }

  loading.value = true
  await nextTick()
  scrollToBottom()

  try {
    if (shouldHandleWithLocalAction(rawQuestion)) {
      const localAnswer = await handleLocalAction(rawQuestion)
      messages.value.push({
        id: Date.now() + 1,
        role: 'assistant',
        content: String(localAnswer || '已执行页面跳转。'),
        confidence: '',
        historyTurns: -1
      })
      return
    }

    if (enableStream.value) {
      const assistantMsg = reactive({
        id: Date.now() + 1,
        role: 'assistant',
        content: '',
        confidence: '',
        historyTurns: -1
      })
      messages.value.push(assistantMsg)
      await nextTick()
      scrollToBottom()

      streamController.value = new AbortController()
      await chatWithAssistantStream({
        question: finalQuestion,
        llmConfig: buildLlmConfigPayload(),
        signal: streamController.value?.signal,
        onChunk: async (chunk) => {
          assistantMsg.content += chunk
          await nextTick()
          scrollToBottom()
        },
        onFinal: (payload) => {
          const historyTurns = Number(payload.historyTurns || 0)
          latestHistoryTurns.value = historyTurns
          assistantMsg.content = String(payload.answer || assistantMsg.content || '未获取到分析结论，请稍后重试。')
          assistantMsg.confidence = payload.confidence != null ? Number(payload.confidence).toFixed(2) : ''
          assistantMsg.historyTurns = historyTurns
        },
        onError: (message) => {
          const err = new Error(message?.message || message || '流式分析失败')
          err.code = message?.code || 'stream_error'
          err.retryable = Boolean(message?.retryable)
          throw err
        }
      })
      return
    }

    const { data } = await chatWithAssistant(finalQuestion, buildLlmConfigPayload())
    const payload = data?.data || data || {}
    const answer = String(payload.answer || '未获取到分析结论，请稍后重试。')
    const confidence = payload.confidence != null ? Number(payload.confidence).toFixed(2) : ''
    const historyTurns = Number(payload.historyTurns || 0)

    latestHistoryTurns.value = historyTurns
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: answer,
      confidence,
      historyTurns
    })
  } catch (error) {
    const isAbort = error?.name === 'AbortError'
    if (isAbort) {
      ElMessage.info('已停止生成')
    } else {
      const resolved = resolveAiError(error)
      ElMessage.warning(resolved.toast)
      messages.value.push({
        id: Date.now() + 2,
        role: 'assistant',
        content: resolved.bubble,
        confidence: '',
        historyTurns: -1
      })
      return
    }
    messages.value.push({
      id: Date.now() + 2,
      role: 'assistant',
      content: isAbort ? '本次分析已手动停止。' : 'AI 服务暂时不可用，请稍后再试。',
      confidence: '',
      historyTurns: -1
    })
  } finally {
    streamController.value = null
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function stopStreaming() {
  streamController.value?.abort()
}

async function copyLatestAnswer() {
  if (!latestAnswer.value) return
  try {
    await navigator.clipboard.writeText(latestAnswer.value)
    ElMessage.success('已复制最新回答')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

function clearConversation() {
  messages.value = [
    {
      id: Date.now(),
      role: 'assistant',
      content: '会话已清空。你可以继续发起新的分析问题。',
      confidence: '',
      historyTurns: 0
    }
  ]
  latestHistoryTurns.value = 0
}

function buildRecentTranscript(limit = 8) {
  const recent = messages.value
    .filter((item) => item.role === 'user' || item.role === 'assistant')
    .slice(-limit)

  return recent
    .map((item) => {
      const role = item.role === 'user' ? '用户' : '助手'
      return `${role}: ${item.content}`
    })
    .join('\n')
}

async function generateReport() {
  if (reportLoading.value) return

  const transcript = buildRecentTranscript()
  if (!transcript) {
    ElMessage.warning('当前没有足够会话内容可生成报告')
    return
  }

  reportLoading.value = true
  try {
    const scope = '全局'
    const theme = '通用审计整改分析'
    const sectionRules = requiredSections[reportMode.value] || requiredSections.weekly
    const modeLabel = reportMode.value === 'weekly'
      ? '标准周报'
      : reportMode.value === 'executive'
        ? '管理摘要'
        : '深度诊断'
    const prompt = [
      '请基于以下审计整改会话生成一份结构化报告，要求使用 Markdown 输出。',
      `报告模式：${modeLabel}`,
      `分析主题：${theme}`,
      `关注范围：${scope}`,
      '',
      '报告必须包含以下章节：',
      ...sectionRules.map((item, idx) => `${idx + 1}. ${item}`),
      '',
      '请包含至少一个 Markdown 表格，用于展示优先级或行动清单。',
      '',
      '以下为会话内容：',
      transcript
    ].join('\n')

    const { data } = await chatWithAssistant(prompt, buildLlmConfigPayload())
    const payload = data?.data || data || {}
    const markdown = String(payload.answer || '').trim()
    if (!markdown) {
      throw new Error('报告内容为空')
    }

    reportMarkdown.value = markdown
    reportJson.value = {
      generatedAt: new Date().toISOString(),
      reportMode: modeLabel,
      theme,
      scope,
      historyTurns: payload.historyTurns ?? latestHistoryTurns.value,
      confidence: payload.confidence ?? null,
      missingSections: missingReportSections.value,
      markdown
    }
    reportDrawerVisible.value = true
    ElMessage.success('分析报告生成成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '生成分析报告失败')
  } finally {
    reportLoading.value = false
  }
}

function downloadReport(type) {
  const scopeSlug = 'global'
  const modeSlug = reportMode.value
  const baseName = `ai-analysis-${modeSlug}-${scopeSlug}-${Date.now()}`

  if (type === 'md' && reportMarkdown.value) {
    const fileName = `${baseName}.md`
    const blob = new Blob([reportMarkdown.value], { type: 'text/markdown;charset=utf-8' })
    triggerDownload(blob, fileName)
    return
  }

  if (type === 'json' && reportJson.value) {
    const fileName = `${baseName}.json`
    const blob = new Blob([JSON.stringify(reportJson.value, null, 2)], { type: 'application/json;charset=utf-8' })
    triggerDownload(blob, fileName)
  }
}

function triggerDownload(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function scrollToBottom() {
  const el = chatBodyRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

function loadAiModelSettings() {
  try {
    const cached = localStorage.getItem(AI_MODEL_SETTINGS_KEY)
    if (!cached) return
    const parsed = JSON.parse(cached)

    if (Array.isArray(parsed?.modelOptions)) {
      const normalized = parsed.modelOptions
        .filter((it) => typeof it?.label === 'string' && typeof it?.value === 'string')
        .map((it) => ({ label: it.label, value: it.value }))
      if (normalized.length > 0) {
        modelOptions.value = normalized
      }
    }

    if (typeof parsed?.defaultModel === 'string' && parsed.defaultModel) {
      selectedModel.value = parsed.defaultModel
    } else if (!modelOptions.value.some((it) => it.value === selectedModel.value)) {
      selectedModel.value = modelOptions.value[0]?.value || ''
    }
    if (typeof parsed?.provider === 'string') {
      llmProvider.value = parsed.provider
    }
    if (typeof parsed?.baseUrl === 'string') {
      llmBaseUrl.value = parsed.baseUrl
    }
    if (typeof parsed?.apiKey === 'string') {
      llmApiKey.value = parsed.apiKey
    }
    if (typeof parsed?.apiVersion === 'string') {
      llmApiVersion.value = parsed.apiVersion
    }
  } catch {
    // ignore invalid settings cache
  }
}

function loadAiDialogStrategy() {
  try {
    const cached = localStorage.getItem(AI_DIALOG_STRATEGY_KEY)
    if (!cached) return
    const parsed = JSON.parse(cached)
    if (typeof parsed?.reportMode === 'string' && requiredSections[parsed.reportMode]) {
      reportMode.value = parsed.reportMode
    }
    if (typeof parsed?.enableStream === 'boolean') {
      enableStream.value = parsed.enableStream
    }
  } catch {
    // ignore invalid cache
  }
}

function handleAiModelSettingsChanged(event) {
  const detail = event?.detail
  if (!detail || typeof detail !== 'object') {
    loadAiModelSettings()
    return
  }

  if (Array.isArray(detail.modelOptions)) {
    const normalized = detail.modelOptions
      .filter((it) => typeof it?.label === 'string' && typeof it?.value === 'string')
      .map((it) => ({ label: it.label, value: it.value }))
    if (normalized.length > 0) {
      modelOptions.value = normalized
    }
  }

  if (typeof detail.defaultModel === 'string' && detail.defaultModel) {
    selectedModel.value = detail.defaultModel
  }
  if (typeof detail.provider === 'string') {
    llmProvider.value = detail.provider
  }
  if (typeof detail.baseUrl === 'string') {
    llmBaseUrl.value = detail.baseUrl
  }
  if (typeof detail.apiKey === 'string') {
    llmApiKey.value = detail.apiKey
  }
  if (typeof detail.apiVersion === 'string') {
    llmApiVersion.value = detail.apiVersion
  }
  if (!modelOptions.value.some((it) => it.value === selectedModel.value)) {
    selectedModel.value = modelOptions.value[0]?.value || ''
  }
}

function handleAiDialogStrategyChanged(event) {
  const detail = event?.detail
  if (!detail || typeof detail !== 'object') {
    loadAiDialogStrategy()
    return
  }
  if (typeof detail.reportMode === 'string' && requiredSections[detail.reportMode]) {
    reportMode.value = detail.reportMode
  }
  if (typeof detail.enableStream === 'boolean') {
    enableStream.value = detail.enableStream
  }
}

onMounted(() => {
  loadAiDialogStrategy()
  loadAiModelSettings()
  window.addEventListener('ai-model-settings-changed', handleAiModelSettingsChanged)
  window.addEventListener('ai-dialog-strategy-changed', handleAiDialogStrategyChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('ai-model-settings-changed', handleAiModelSettingsChanged)
  window.removeEventListener('ai-dialog-strategy-changed', handleAiDialogStrategyChanged)
})
</script>

<style scoped>
.ai-analysis-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-radius: 14px;
  background: linear-gradient(110deg, #f3f8ff 0%, #effdf6 50%, #fff8ed 100%);
  border: 1px solid #d9ecff;
}

.hero-card h2 {
  margin: 0;
  font-size: 24px;
  color: #183153;
}

.hero-card p {
  margin: 6px 0 0;
  color: #4f6379;
  font-size: 14px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.analysis-layout {
  min-height: 620px;
}

.chat-panel {
  border-radius: 14px;
  border: 1px solid #e8edf5;
}

.chat-panel {
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header .sub {
  color: #77869b;
  font-size: 12px;
}

.chat-body {
  height: 460px;
  overflow-y: auto;
  padding: 8px 2px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.msg-row {
  display: flex;
}

.msg-row.user {
  justify-content: flex-end;
}

.msg-row.assistant {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: 78%;
  padding: 11px 13px;
  border-radius: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.55;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.05);
}

.msg-row.user .msg-bubble {
  background: #1e88e5;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-row.assistant .msg-bubble {
  background: #f4f8ff;
  color: #1d2a3a;
  border: 1px solid #e3ecfb;
  border-bottom-left-radius: 4px;
}

.msg-meta {
  margin-top: 8px;
  display: flex;
  gap: 10px;
  font-size: 12px;
  opacity: 0.75;
}

.composer {
  margin-top: 10px;
  border-top: 1px solid #edf2f8;
  padding-top: 12px;
}

.composer-actions {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
}

.report-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.report-alert {
  margin-bottom: 10px;
  font-size: 13px;
  color: #9a6700;
  background: #fff9e8;
  border: 1px solid #f2dea3;
  border-radius: 8px;
  padding: 8px 10px;
}

.report-preview {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f6f8fb;
  border: 1px solid #e5eaf2;
  border-radius: 10px;
  padding: 14px;
  line-height: 1.6;
  color: #1d2a3a;
  min-height: 320px;
}

@media (max-width: 1100px) {
  .analysis-layout {
    grid-template-columns: 1fr;
  }

  .chat-body {
    height: 400px;
  }

  .hero-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
