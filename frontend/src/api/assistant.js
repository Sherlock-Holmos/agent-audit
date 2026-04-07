import client from './client'

export const chatWithAssistant = (question, llmConfig = null) =>
  client.post('/agent/chat', {
    question,
    ...(llmConfig ? { llmConfig } : {})
  })

export async function chatWithAssistantStream({ question, llmConfig, onChunk, onFinal, onError, signal }) {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  const username = user?.username || user?.userName || ''

  const resp = await fetch('/api/agent/chat/stream', {
    method: 'POST',
    signal,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(username ? { 'X-User-Name': username } : {})
    },
    body: JSON.stringify({
      question,
      ...(llmConfig ? { llmConfig } : {})
    })
  })

  if (!resp.ok || !resp.body) {
    throw new Error(`流式请求失败: HTTP ${resp.status}`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''

    for (const frame of frames) {
      const line = frame
        .split('\n')
        .map((it) => it.trim())
        .find((it) => it.startsWith('data:'))
      if (!line) continue

      const raw = line.slice(5).trim()
      if (raw === '[DONE]') {
        return
      }

      try {
        const payload = JSON.parse(raw)
        if (payload.type === 'chunk') {
          onChunk?.(payload.content || '')
        } else if (payload.type === 'final') {
          onFinal?.(payload)
        } else if (payload.type === 'error') {
          onError?.({
            code: payload.code || 'stream_error',
            message: payload.message || '流式响应异常',
            retryable: Boolean(payload.retryable)
          })
        }
      } catch {
        // ignore malformed partial frames
      }
    }
  }
}
