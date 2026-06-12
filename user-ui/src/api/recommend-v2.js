/**
 * AI 推荐 v2 API 调用封装。
 *
 * SSE 端点使用 fetch + ReadableStream 手动解析。
 * 非 SSE 端点使用项目统一的 axios request 实例。
 */
import request, { getToken } from './request'

// ── 草稿 ──

/** 获取当前草稿 */
export function getDraft() {
  return request({ url: '/app/ai-recommend-v2/draft', method: 'get' })
}

/** SSE 生成草稿（返回 fetch Response，由组件自行解析 SSE 流） */
export function generateDraft() {
  return fetch('/dev-api/app/ai-recommend-v2/draft/generate', {
    method: 'POST',
    headers: {
      'Authorization': 'Bearer ' + (getToken() || ''),
      'Accept': 'text/event-stream'
    }
  })
}

/** 从草稿中移除候选 */
export function removeCandidate(programId) {
  return request({
    url: '/app/ai-recommend-v2/draft/remove',
    method: 'post',
    data: { programId }
  })
}

/** 替换候选 */
export function replaceCandidate(removeProgramId, tier, preference = 'safer') {
  return request({
    url: '/app/ai-recommend-v2/draft/replace',
    method: 'post',
    data: { removeProgramId, tier, preference }
  })
}

/** 加回候选 */
export function addBackCandidate(programId) {
  return request({
    url: '/app/ai-recommend-v2/draft/add-back',
    method: 'post',
    data: { programId }
  })
}

/** 获取同档替代候选 */
export function getAlternatives(tier, excludeId) {
  return request({
    url: '/app/ai-recommend-v2/draft/alternatives',
    method: 'get',
    params: { tier, excludeId }
  })
}

// ── 报告 ──

/** 从草稿生成最终报告 */
export function generateReport() {
  return request({
    url: '/app/ai-recommend-v2/report/generate',
    method: 'post'
  })
}

/** 查看报告详情 */
export function getReport(reportId) {
  return request({
    url: `/app/ai-recommend-v2/report/${reportId}`,
    method: 'get'
  })
}

/** 我的 v2 报告列表 */
export function listReports() {
  return request({
    url: '/app/ai-recommend-v2/reports',
    method: 'get'
  })
}

// ── 对话 ──

/** 开始/重置对话 */
export function startChat() {
  return request({
    url: '/app/ai-recommend-v2/chat/start',
    method: 'post'
  })
}

/** SSE 流式对话（返回 fetch Response） */
export function sendChatMessage(message) {
  return fetch('/dev-api/app/ai-recommend-v2/chat/send', {
    method: 'POST',
    headers: {
      'Authorization': 'Bearer ' + (getToken() || ''),
      'Accept': 'text/event-stream',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ message })
  })
}

/** 恢复对话 */
export function resumeChat() {
  return request({
    url: '/app/ai-recommend-v2/chat/resume',
    method: 'get'
  })
}
