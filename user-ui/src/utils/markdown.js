import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  breaks: true,
  linkify: true,
  html: false
})

/**
 * 渲染 markdown 字符串为 HTML。
 * 仅用于 AI 回复内容，用户消息不经过此函数。
 */
export function renderMarkdown(text) {
  if (!text) return ''
  return md.render(text)
}
