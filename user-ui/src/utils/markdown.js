import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  breaks: true,
  linkify: true,
  html: false
})
md.linkify.set({ fuzzyLink: false })

const methodologySource = 'https://www.moe.gov.cn/srcsite/A15/moe_778/s3261/202509/t20250918_1413836.html'
const methodologyBackup = 'https://yzst.chsi.com.cn/kyzx/jybzc/202509/20250925/2293432170-9.html'

function citationUrl(value) {
  if (typeof value !== 'string' || !/^https?:\/\//i.test(value)) return ''
  try {
    return new URL(value).href
  } catch {
    return ''
  }
}

export function getMessageEvidence(message) {
  let metadata = message?.metadataJson || message?.metadata || message?.extra
  if (typeof metadata === 'string') {
    try { metadata = JSON.parse(metadata) } catch { return [] }
  }
  const citations = metadata?.ragCitations || metadata?.citations || metadata?.evidence?.citations
  if (!Array.isArray(citations)) return []
  const sources = new Map()
  for (const citation of citations) {
    const name = citation?.sourceName || citation?.title || citation?.docType
    if (typeof name !== 'string' || !name.trim()) continue
    const url = citationUrl(citation.sourceUrl)
    const source = sources.get(name) || { name, url: '' }
    if (!source.url) source.url = url
    if (name === '05-择校方法论.md' && url === methodologySource) {
      source.backup = { name: '研招网', url: citationUrl(citation.sourceBackupUrl) || methodologyBackup }
    }
    sources.set(name, source)
  }
  return [...sources.values()]
}

md.core.ruler.after('linkify', 'citation_links', state => {
  const urls = new Map(state.env.citations.map(source => [source.name, source.url]))
  if (!urls.size) return
  const names = [...urls.keys()].sort((a, b) => b.length - a.length)
    .map(name => name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  const citation = new RegExp(`(?<![\\w/-])(${names.join('|')})(?![\\w/-]|\\.[\\w])`, 'g')

  function link(tokens, url) {
    if (!url) return tokens
    const open = new state.Token('link_open', 'a', 1)
    open.attrSet('href', url)
    open.attrSet('target', '_blank')
    open.attrSet('rel', 'noreferrer')
    return [open, ...tokens, new state.Token('link_close', 'a', -1)]
  }

  for (const block of state.tokens) {
    if (block.type !== 'inline' || !block.children) continue
    const tokens = block.children
    const children = []
    for (let index = 0; index < tokens.length; index++) {
      const token = tokens[index]
      if (token.type === 'link_open') {
        const end = tokens.findIndex((item, position) => position > index && item.type === 'link_close')
        const body = tokens.slice(index + 1, end)
        const label = body.map(item => item.content).join('')
        children.push(...(urls.has(label) ? link(body, urls.get(label)) : tokens.slice(index, end + 1)))
        index = end
      } else if (token.type === 'code_inline') {
        children.push(...link([token], urls.get(token.content)))
      } else if (token.type === 'text') {
        for (const [partIndex, part] of token.content.split(citation).entries()) {
          if (!part) continue
          const text = new state.Token('text', '', 0)
          text.content = part
          children.push(...link([text], partIndex % 2 ? urls.get(part) : ''))
        }
      } else {
        children.push(token)
      }
    }
    block.children = children
  }
})

/**
 * 渲染 markdown 字符串为 HTML。
 * 仅用于 AI 回复内容，用户消息不经过此函数。
 */
export function renderMarkdown(text, citations = []) {
  if (!text) return ''
  return md.render(text, { citations })
}
