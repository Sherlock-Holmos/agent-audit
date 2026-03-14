<template>
  <div
    :class="['assistant-shell', { dragging: shell.dragging }]"
    :style="shellStyle"
    @mousedown="onDragStart"
    @touchstart.passive="onTouchStart"
  >
    <transition name="assistant-fade">
      <div v-if="open" class="assistant-panel" @mousedown.stop @touchstart.stop>
        <div class="assistant-header" @mousedown.stop="onHeaderDragStart" @touchstart.stop.prevent="onHeaderTouchStart">
          <div class="assistant-title">智能助手</div>
          <div class="assistant-header-actions">
            <el-button link class="assistant-header-btn" @click="toggleCompact">{{ compact ? '展开按钮' : '最小化' }}</el-button>
            <el-button link class="assistant-header-btn" @click="clearMessages">清空</el-button>
            <el-button link class="assistant-header-btn" @click="toggleOpen">收起</el-button>
          </div>
        </div>

        <div ref="messageRef" class="assistant-messages">
          <div v-for="msg in messages" :key="msg.id" :class="['bubble-wrap', msg.role]">
            <div class="bubble">{{ msg.content }}</div>
          </div>
          <div v-if="loading" class="bubble-wrap assistant">
            <div class="bubble">正在思考中...</div>
          </div>
        </div>

        <div class="assistant-input-wrap">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入问题，例如：帮我分析当前整改风险"
            @keydown.enter.exact.prevent="send"
          />
          <div class="assistant-send-row">
            <span class="assistant-tip">Enter 发送，Shift+Enter 换行</span>
            <el-button type="primary" :loading="loading" @click="send">发送</el-button>
          </div>
        </div>
      </div>
    </transition>

    <button
      v-if="!open && !compact"
      class="assistant-fab"
      type="button"
      @click.stop="onLauncherClick"
    >
      <span class="fab-icon">AI</span>
      <span class="fab-label">智能助手</span>
      <span v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </button>

    <button
      v-if="!open && compact"
      class="assistant-mini"
      type="button"
      @click.stop="onLauncherClick"
    >
      <span>AI</span>
      <span v-if="unreadCount > 0" class="badge mini-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { chatWithAssistant } from '../../api/assistant'

const ASSISTANT_POSITION_KEY = 'assistant_floating_position_v1'
const ASSISTANT_COMPACT_KEY = 'assistant_compact_mode_v1'
const DRAG_CLICK_SUPPRESS_MS = 240
const DRAG_THRESHOLD = 6
const DOCK_THRESHOLD = 120

const open = ref(false)
const compact = ref(true)
const loading = ref(false)
const question = ref('')
const messageRef = ref()
const unreadCount = ref(0)

const shell = reactive({
  x: window.innerWidth - 90,
  y: Math.max(window.innerHeight - 160, 80),
  dragging: false,
  dragOffsetX: 0,
  dragOffsetY: 0,
  dragStartX: 0,
  dragStartY: 0,
  moved: false,
  suppressClickUntil: 0
})

const messages = ref([
  {
    id: 1,
    role: 'assistant',
    content: '你好，我是审计整改智能助手。你可以问我当前整改率、风险点和优先级建议。'
  }
])

const shellStyle = computed(() => ({
  left: `${shell.x}px`,
  top: `${shell.y}px`
}))

function persistCompactMode() {
  localStorage.setItem(ASSISTANT_COMPACT_KEY, compact.value ? '1' : '0')
}

function loadCompactMode() {
  const raw = localStorage.getItem(ASSISTANT_COMPACT_KEY)
  if (raw === null) return
  compact.value = raw === '1'
}

function persistPosition() {
  localStorage.setItem(ASSISTANT_POSITION_KEY, JSON.stringify({ x: shell.x, y: shell.y }))
}

function loadPosition() {
  const raw = localStorage.getItem(ASSISTANT_POSITION_KEY)
  if (!raw) return
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
      shell.x = parsed.x
      shell.y = parsed.y
    }
  } catch {
    // ignore invalid local cache
  }
}

function clampPosition() {
  const panelWidth = open.value ? 360 : compact.value ? 56 : 110
  const panelHeight = open.value ? 500 : 64
  const maxX = Math.max(window.innerWidth - panelWidth - 8, 8)
  const maxY = Math.max(window.innerHeight - panelHeight - 8, 8)

  if (shell.x < 8) shell.x = 8
  if (shell.y < 8) shell.y = 8
  if (shell.x > maxX) shell.x = maxX
  if (shell.y > maxY) shell.y = maxY
}

function snapToEdge(force = false) {
  const panelWidth = open.value ? 360 : compact.value ? 56 : 110
  const distanceToLeft = shell.x
  const distanceToRight = window.innerWidth - (shell.x + panelWidth)

  // Magnetic docking: only snap when close enough to side edges.
  if (force || Math.min(distanceToLeft, distanceToRight) <= DOCK_THRESHOLD) {
    shell.x = distanceToLeft <= distanceToRight ? 8 : Math.max(window.innerWidth - panelWidth - 8, 8)
  }

  clampPosition()
  persistPosition()
}

function startDrag(clientX, clientY) {
  shell.dragging = true
  shell.moved = false
  shell.dragStartX = clientX
  shell.dragStartY = clientY
  shell.dragOffsetX = clientX - shell.x
  shell.dragOffsetY = clientY - shell.y
}

function onDragStart(event) {
  if (open.value) return
  startDrag(event.clientX, event.clientY)
}

function onHeaderDragStart(event) {
  if (!open.value) return
  startDrag(event.clientX, event.clientY)
}

function onTouchStart(event) {
  if (open.value) return
  const touch = event.touches?.[0]
  if (!touch) return
  startDrag(touch.clientX, touch.clientY)
}

function onHeaderTouchStart(event) {
  if (!open.value) return
  const touch = event.touches?.[0]
  if (!touch) return
  startDrag(touch.clientX, touch.clientY)
}

function onPointerMove(clientX, clientY) {
  if (!shell.dragging) return

  if (!shell.moved) {
    const dx = Math.abs(clientX - shell.dragStartX)
    const dy = Math.abs(clientY - shell.dragStartY)
    if (dx >= DRAG_THRESHOLD || dy >= DRAG_THRESHOLD) {
      shell.moved = true
    }
  }

  shell.x = clientX - shell.dragOffsetX
  shell.y = clientY - shell.dragOffsetY
  clampPosition()
}

function onMouseMove(event) {
  onPointerMove(event.clientX, event.clientY)
}

function onTouchMove(event) {
  const touch = event.touches?.[0]
  if (!touch) return
  onPointerMove(touch.clientX, touch.clientY)
}

function stopDrag() {
  if (!shell.dragging) return
  shell.dragging = false
  if (shell.moved) {
    shell.suppressClickUntil = Date.now() + DRAG_CLICK_SUPPRESS_MS
  }
  // Collapsed assistant should always auto-dock after drag.
  snapToEdge(!open.value)
}

function shouldIgnoreLauncherClick() {
  return Date.now() < shell.suppressClickUntil
}

function onLauncherClick() {
  if (shouldIgnoreLauncherClick()) {
    return
  }
  toggleOpen()
}

function toggleOpen() {
  open.value = !open.value
  nextTick(() => {
    clampPosition()
    if (open.value) {
      unreadCount.value = 0
      scrollToBottom()
    } else {
      // Always dock when collapsing.
      snapToEdge(true)
    }
  })
}

function toggleCompact() {
  compact.value = !compact.value
  persistCompactMode()
  nextTick(() => {
    clampPosition()
    if (!open.value) {
      snapToEdge(true)
    }
    persistPosition()
  })
}

function clearMessages() {
  messages.value = [
    {
      id: Date.now(),
      role: 'assistant',
      content: '会话已清空。你可以继续提问。'
    }
  ]
}

async function send() {
  const q = question.value.trim()
  if (!q || loading.value) return

  messages.value.push({ id: Date.now(), role: 'user', content: q })
  question.value = ''
  loading.value = true
  await nextTick()
  scrollToBottom()

  try {
    const { data } = await chatWithAssistant(q)
    const payload = data?.data || data || {}
    const answer = payload.answer || '暂未获取到建议，请稍后重试。'
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: String(answer)
    })
    if (!open.value) {
      unreadCount.value += 1
    }
  } catch (error) {
    messages.value.push({
      id: Date.now() + 2,
      role: 'assistant',
      content: '智能助手暂时不可用，请稍后再试。'
    })
    if (!open.value) {
      unreadCount.value += 1
    }
    ElMessage.error(error?.response?.data?.message || error?.message || '智能助手请求失败')
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function scrollToBottom() {
  const el = messageRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

function onWindowResize() {
  clampPosition()
  if (!open.value) {
    snapToEdge(true)
  }
  persistPosition()
}

onMounted(() => {
  loadCompactMode()
  loadPosition()
  clampPosition()
  if (!open.value) {
    snapToEdge(true)
  }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', stopDrag)
  window.addEventListener('touchmove', onTouchMove, { passive: true })
  window.addEventListener('touchend', stopDrag)
  window.addEventListener('resize', onWindowResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', stopDrag)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', stopDrag)
  window.removeEventListener('resize', onWindowResize)
})
</script>

<style scoped>
.assistant-shell {
  position: fixed;
  z-index: 1200;
  user-select: none;
  transition: left 0.18s ease, top 0.18s ease;
}

.assistant-shell.dragging {
  transition: none;
}

.assistant-fab {
  width: 110px;
  height: 58px;
  border: none;
  border-radius: 14px;
  cursor: grab;
  background: linear-gradient(135deg, #0f64d8 0%, #09b5ff 100%);
  color: #fff;
  box-shadow: 0 10px 22px rgba(10, 109, 219, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  position: relative;
}

.assistant-fab:active {
  cursor: grabbing;
}

.fab-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.fab-label {
  font-size: 13px;
  font-weight: 600;
}

.assistant-mini {
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 50%;
  cursor: grab;
  background: linear-gradient(135deg, #0f64d8 0%, #09b5ff 100%);
  color: #fff;
  box-shadow: 0 10px 22px rgba(10, 109, 219, 0.35);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  position: relative;
}

.assistant-mini:active {
  cursor: grabbing;
}

.badge {
  position: absolute;
  right: -6px;
  top: -6px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 20px;
}

.mini-badge {
  right: -4px;
  top: -4px;
}

.assistant-panel {
  width: 360px;
  height: 500px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 20px 40px rgba(13, 32, 64, 0.25);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.assistant-header {
  height: 48px;
  padding: 0 12px;
  background: linear-gradient(135deg, #0f64d8 0%, #09b5ff 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: grab;
}

.assistant-header:active {
  cursor: grabbing;
}

.assistant-title {
  font-size: 14px;
  font-weight: 600;
}

.assistant-header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.assistant-header-btn {
  color: #fff;
}

.assistant-messages {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  background: #f7f9fc;
}

.bubble-wrap {
  display: flex;
  margin-bottom: 10px;
}

.bubble-wrap.user {
  justify-content: flex-end;
}

.bubble-wrap.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 82%;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-wrap.user .bubble {
  background: #1769ff;
  color: #fff;
}

.bubble-wrap.assistant .bubble {
  background: #fff;
  color: #334155;
  border: 1px solid #dbe5f2;
}

.assistant-input-wrap {
  border-top: 1px solid #e7edf6;
  padding: 10px;
  background: #fff;
}

.assistant-send-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.assistant-tip {
  font-size: 12px;
  color: #94a3b8;
}

.assistant-fade-enter-active,
.assistant-fade-leave-active {
  transition: all 0.2s ease;
}

.assistant-fade-enter-from,
.assistant-fade-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

@media (max-width: 768px) {
  .assistant-panel {
    width: min(92vw, 360px);
    height: min(74vh, 500px);
  }

  .assistant-fab {
    width: 96px;
    height: 52px;
  }

  .assistant-mini {
    width: 52px;
    height: 52px;
  }

  .fab-label {
    font-size: 12px;
  }
}
</style>
