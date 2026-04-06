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
      <el-card class="prompt-panel" shadow="never">
        <template #header>
          <span>分析配置</span>
        </template>

        <el-form label-position="top" class="prompt-form">
          <el-form-item label="分析主题">
            <el-select v-model="analysisTheme" placeholder="选择分析主题">
              <el-option label="整改风险优先级" value="risk" />
              <el-option label="数据质量薄弱点" value="quality" />
              <el-option label="治理执行效率" value="efficiency" />
              <el-option label="管理层周报摘要" value="weekly" />
            </el-select>
          </el-form-item>

          <el-form-item label="关注范围（可选）">
            <el-input
              v-model="focusScope"
              placeholder="例如：财务条线 / 华东区域 / 本周新增问题"
              maxlength="80"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="报告模式">
            <el-radio-group v-model="reportMode" class="report-mode-group">
              <el-radio-button label="weekly">标准周报</el-radio-button>
              <el-radio-button label="executive">管理摘要</el-radio-button>
              <el-radio-button label="deep">深度诊断</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>

        <div class="template-wrap">
          <div class="template-title">快捷问题模板</div>
          <el-button
            v-for="item in quickTemplates"
            :key="item"
            class="template-btn"
            @click="useTemplate(item)"
          >
            {{ item }}
          </el-button>
        </div>
      </el-card>

      <el-card class="chat-panel" shadow="never">
        <template #header>
          <div class="chat-header">
            <span>分析对话</span>
            <span class="sub">最近{{ latestHistoryTurns }}轮上下文已自动携带</span>
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

          <div v-if="loading" class="msg-row assistant">
            <div class="msg-bubble">正在进行分析，请稍候...</div>
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
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { chatWithAssistant } from '../api/assistant'

const analysisTheme = ref('risk')
const reportMode = ref('weekly')
const focusScope = ref('')
const question = ref('')
const loading = ref(false)
const reportLoading = ref(false)
const chatBodyRef = ref(null)
const latestHistoryTurns = ref(0)
const reportDrawerVisible = ref(false)
const reportMarkdown = ref('')
const reportJson = ref(null)

const quickTemplates = [
  '请按风险等级给出本周整改优先级清单。',
  '请指出导致整改完成率提升缓慢的三个关键因素。',
  '请输出一个可执行的7天整改行动计划。',
  '请用管理层摘要格式输出当前整改态势和建议。'
]

const messages = ref([
  {
    id: 1,
    role: 'assistant',
    content: '你好，我是 LangChain 驱动的审计整改分析助手。你可以直接提问，或先选择左侧分析主题。',
    confidence: '',
    historyTurns: 0
  }
])

const themeLabelMap = {
  risk: '整改风险优先级',
  quality: '数据质量薄弱点',
  efficiency: '治理执行效率',
  weekly: '管理层周报摘要'
}

const requiredSections = {
  weekly: ['执行摘要', '关键风险点', '根因分析', '整改行动计划', '预期收益与跟踪指标'],
  executive: ['执行摘要', '关键结论', '管理建议', '下周重点'],
  deep: ['问题定义', '证据与现状', '根因分解', '整改路线图', '风险与依赖']
}

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

function buildQuestion() {
  const raw = question.value.trim()
  if (!raw) return ''

  const themeLabel = themeLabelMap[analysisTheme.value]

  const scope = focusScope.value.trim()
  const prefix = scope
    ? `分析主题：${themeLabel}；关注范围：${scope}。`
    : `分析主题：${themeLabel}。`

  return `${prefix}\n${raw}`
}

function useTemplate(text) {
  question.value = text
}

async function sendQuestion() {
  const finalQuestion = buildQuestion()
  if (!finalQuestion || loading.value) return

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: finalQuestion,
    confidence: '',
    historyTurns: -1
  })

  question.value = ''
  loading.value = true
  await nextTick()
  scrollToBottom()

  try {
    const { data } = await chatWithAssistant(finalQuestion)
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
    ElMessage.error(error?.response?.data?.message || error?.message || 'AI 分析请求失败')
    messages.value.push({
      id: Date.now() + 2,
      role: 'assistant',
      content: 'AI 服务暂时不可用，请稍后再试。',
      confidence: '',
      historyTurns: -1
    })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
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
    const scope = focusScope.value.trim() || '全局'
    const theme = themeLabelMap[analysisTheme.value]
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

    const { data } = await chatWithAssistant(prompt)
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
  const scopeSlug = (focusScope.value.trim() || 'global').replace(/[\\/:*?"<>|\s]+/g, '-').slice(0, 24)
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
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  min-height: 620px;
}

.prompt-panel,
.chat-panel {
  border-radius: 14px;
  border: 1px solid #e8edf5;
}

.prompt-form {
  margin-bottom: 16px;
}

.template-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.template-title {
  font-size: 13px;
  color: #637184;
}

.template-btn {
  justify-content: flex-start;
  margin: 0;
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

.report-mode-group {
  width: 100%;
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
