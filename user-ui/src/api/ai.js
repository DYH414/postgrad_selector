import request, { getToken } from './request'

export function postAiStart(data) {
  return request({ url: '/app/ai-recommend/start', method: 'post', data })
}

export function postAiChat(data) {
  return request({ url: '/app/ai-recommend/chat', method: 'post', data, timeout: 120000 })
}

export async function postAiChatStream(data, handlers = {}) {
  const token = getToken()
  const controller = new AbortController()
  const timeoutMs = handlers.timeoutMs || 300000
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch('/dev-api/app/ai-recommend/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {})
      },
      body: JSON.stringify(data),
      signal: controller.signal
    })
    if (!response.ok || !response.body) {
      throw new Error('流式对话连接失败')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() || ''
      blocks.forEach(block => handleSseBlock(block, handlers))
    }
    if (buffer.trim()) {
      handleSseBlock(buffer, handlers)
    }
  } finally {
    window.clearTimeout(timeoutId)
  }
}

function handleSseBlock(block, handlers) {
  const lines = block.split(/\r?\n/)
  let event = 'message'
  const dataLines = []
  lines.forEach(line => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  })
  if (!dataLines.length) return
  const rawData = dataLines.join('\n')
  const data = JSON.parse(rawData)
  if (event === 'thinking') {
    handlers.onThinking?.(data.text || '')
  } else if (event === 'token') {
    handlers.onToken?.(data.text || '')
  } else if (event === 'done') {
    handlers.onDone?.(data)
  } else if (event === 'error') {
    handlers.onError?.(new Error(data.message || '对话请求失败'))
  }
}

export function postAiGenerateReport(data) {
  return request({ url: '/app/ai-recommend/generate-report', method: 'post', data })
}

export function getAiReport(id) {
  return request({ url: '/app/ai-recommend/report/' + id, method: 'get' })
}

export function getAiReportProgress(id) {
  return request({ url: '/app/ai-recommend/report/' + id + '/progress', method: 'get' })
}

export function getAiReports() {
  return request({ url: '/app/ai-recommend/reports', method: 'get' })
}

export function postAiResume(data) {
  return request({ url: '/app/ai-recommend/resume', method: 'post', data })
}

export function postAiAnalyze() {
  return request({ url: '/app/ai-recommend/analyze', method: 'post' })
}

export function getBookmarks(conversationId) {
  return request({ url: '/app/ai-recommend/bookmarks/' + conversationId, method: 'get' })
}

export function deleteBookmark(conversationId, programId) {
  return request({ url: '/app/ai-recommend/bookmarks/' + conversationId + '/' + programId, method: 'delete' })
}
