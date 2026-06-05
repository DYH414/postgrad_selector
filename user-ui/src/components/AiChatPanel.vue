<template>
  <div class="ai-chat-panel" :class="{ open: visible }">
    <div class="panel-header">
      <div class="advisor-title">
        <span class="advisor-mark">AI</span>
        <div>
          <strong>择校顾问</strong>
          <small>{{ loading ? (thinkingText || '正在综合判断') : '分数 · 招生 · 地区 · 层次' }}</small>
        </div>
      </div>
      <el-button class="panel-close" type="text" icon="el-icon-close" @click="emit('close')" />
    </div>

    <div class="panel-body" ref="body">
      <div v-if="messages.length === 0 && !loading" class="empty-state">
        <i class="el-icon-chat-line-round" />
        <p>等待生成你的择校判断</p>
      </div>

      <div v-for="msg in decoratedMessages" :key="msg.key" :class="['msg', msg.role]">
        <div class="msg-avatar">
          <i v-if="msg.role === 'assistant'" class="el-icon-cpu" />
          <i v-else class="el-icon-user" />
        </div>
        <div v-if="msg.role === 'assistant'" class="advisor-card">
          <div class="advisor-card-head">
            <span>择校判断</span>
            <em>第 {{ msg.turn }} 轮</em>
          </div>
          <div class="advisor-text">
            <template v-for="(section, sIdx) in msg.sections" :key="sIdx">
              <p v-if="section.type === 'text'">{{ section.text }}</p>
              <div v-else-if="section.type === 'school'" class="school-reco-card">
                <div class="school-card-head">
                  <div>
                    <strong>{{ section.school }}</strong>
                    <small v-if="section.program">{{ section.program }}</small>
                  </div>
                  <span v-if="section.level" :class="['school-level', section.levelTone]">{{ section.level }}</span>
                </div>
                <div class="school-metrics">
                  <span v-if="section.avg"><b>均分</b>{{ section.avg }}</span>
                  <span v-if="section.gap" :class="section.gapTone"><b>差距</b>{{ section.gap }}</span>
                  <span v-if="section.quota" :class="section.quotaTone"><b>招生</b>{{ section.quota }}</span>
                </div>
                <p v-if="section.reason" class="school-reason">{{ section.reason }}</p>
              </div>
            </template>
          </div>
        </div>
        <div v-else class="user-pill">{{ msg.text }}</div>
      </div>

      <div v-if="loading" class="msg assistant">
        <div class="msg-avatar"><i class="el-icon-cpu" /></div>
        <div class="advisor-card thinking-card">
          <div class="advisor-card-head">
            <span>{{ thinkingText || '正在综合判断' }}</span>
            <em>AI</em>
          </div>
          <div class="thinking-steps">
            <span />
            <span />
            <span />
          </div>
        </div>
      </div>
    </div>

    <div v-if="currentOptions.length > 0 && !loading" class="options-bar">
      <div class="options-title">下一步</div>
      <el-button v-for="(opt, i) in currentOptions" :key="i"
        size="small" class="decision-option" @click="sendOption(opt)">
        {{ opt }}
      </el-button>
    </div>

    <div class="input-bar">
      <el-input v-model="input" placeholder="输入你的想法..."
        size="small" @keyup.enter="sendMessage">
        <template #append>
          <el-button icon="el-icon-s-promotion"
            :disabled="!input.trim() || loading" @click="sendMessage" />
        </template>
      </el-input>
      <el-button v-if="messages.length >= 4" type="success" size="small"
        class="report-button" @click="generateReport">
        生成推荐报告
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { postAiStart, postAiChat, postAiChatStream, postAiGenerateReport } from '@/api/ai'

const props = defineProps({
  visible: { type: Boolean, default: false },
  candidateIds: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'fallback'])

const router = useRouter()

const body = ref(null)
const conversationId = ref(null)
const messages = ref([])
const currentOptions = ref([])
const input = ref('')
const loading = ref(false)
const thinkingText = ref('')

// ---- decorated messages ----
const decoratedMessages = computed(() => {
  let assistantTurn = 0
  return messages.value.map((msg, index) => {
    if (msg.role === 'assistant') assistantTurn += 1
    return {
      ...msg,
      key: `${msg.role}-${index}`,
      turn: assistantTurn,
      text: stripMarkdown(msg.content),
      sections: msg.role === 'assistant' ? messageSections(msg.content, msg.cards) : []
    }
  })
})

watch(() => props.visible, (val) => {
  if (val && !conversationId.value) startConversation()
})

// ---- conversation ----
async function startConversation() {
  loading.value = true
  try {
    const res = await postAiStart({ candidateIds: props.candidateIds })
    conversationId.value = res.data.conversationId
    messages.value = [{ role: 'assistant', content: res.data.message, cards: res.data.cards || [] }]
    currentOptions.value = res.data.options || []
    saveToLocal()
  } catch (e) {
    ElMessage.error('启动 AI 对话失败')
  } finally {
    loading.value = false
  }
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  currentOptions.value = []
  thinkingText.value = ''
  loading.value = true
  await callChat(text)
  scrollToBottom()
}

async function sendOption(opt) {
  messages.value.push({ role: 'user', content: opt })
  currentOptions.value = []
  thinkingText.value = ''
  loading.value = true
  await callChat(opt)
  scrollToBottom()
}

async function callChat(text) {
  let assistantIndex = -1
  let rawAssistantContent = ''
  const ensureAssistantMsg = () => {
    if (assistantIndex < 0) {
      messages.value.push({ role: 'assistant', content: '' })
      assistantIndex = messages.value.length - 1
    }
    return assistantIndex
  }
  try {
    await postAiChatStream(
      { conversationId: conversationId.value, message: text },
      {
        onThinking(text) {
          thinkingText.value = text
        },
        onToken(token) {
          rawAssistantContent += token
          const idx = ensureAssistantMsg()
          messages.value[idx].content = stripMarkdown(visibleStreamContent(rawAssistantContent))
          loading.value = false
          scrollToBottom()
        },
        onDone(data) {
          const idx = ensureAssistantMsg()
          messages.value[idx].content = visibleStreamContent(data.message || messages.value[idx].content)
          messages.value[idx].cards = data.cards || []
          currentOptions.value = data.options || []
          saveToLocal()
        },
        onError(error) {
          throw error
        }
      }
    )
  } catch (e) {
    await callChatFallback(text, ensureAssistantMsg(), e)
  } finally {
    loading.value = false
  }
}

function visibleStreamContent(content) {
  const optionsMarker = content.indexOf('---OPTIONS---')
  return (optionsMarker >= 0 ? content.slice(0, optionsMarker) : content).trimStart()
}

// ---- text cleaning ----
function cleanTechnicalText(text) {
  if (!text) return text
  return text
    .replace(/✅\s*canBeSafe[，,、\s]*/gi, '可作为低风险候选，')
    .replace(/canBeSafe\s*[:=]?\s*true/gi, '保底边界通过')
    .replace(/canBeSafe\s*[:=]?\s*false/gi, '不能作为保底')
    .replace(/safeBlockReason\s*[:：=]?\s*/gi, '不能作为保底的原因：')
    .replace(/quotaRisk\s*[:：=]?\s*(very_high|high|medium|normal|unknown)/gi, (_, risk) => {
      const map = {
        very_high: '招生波动风险极高',
        high: '招生波动风险较高',
        medium: '招生波动风险中等',
        normal: '招生规模正常',
        unknown: '招生规模待核验'
      }
      return map[risk] || ''
    })
    .replace(/dataCompleteness\s*[:：=]?\s*([ABC])/gi, '数据完整度$1')
    .replace(/\bprogramId\s*[:：=]?\s*\d+\b/gi, '')
    .replace(/\bsourceUrl\s*[:：=]?\s*\S+/gi, '')
    .replace(/\bsourceOwner\s*[:：=]?\s*\S+/gi, '')
}

function stripMarkdown(text) {
  if (!text) return text
  return cleanTechnicalText(text)
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^###\s+/gm, '')
    .replace(/^##\s+/gm, '')
    .replace(/^#\s+/gm, '')
    .replace(/^>\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '· ')
    .replace(/\|/g, ' ')
}

function messageParagraphs(content) {
  const text = stripMarkdown(visibleStreamContent(content || ''))
  if (!text) return []
  const normalized = text
    .replace(/\s*---OPTIONS---[\s\S]*$/g, '')
    .replace(/([。！？])\s*/g, '$1\n')
    .replace(/\n{2,}/g, '\n')
    .trim()
  const paragraphs = normalized.split('\n').map(p => p.trim()).filter(Boolean)
  return paragraphs.filter(paragraph => !/^-{3,}$/.test(paragraph.replace(/\s/g, '')))
}

// ---- school card extraction ----
function messageSections(content, hydratedCards) {
  const text = stripMarkdown(visibleStreamContent(content || ''))
  if (!text) return []
  if (Array.isArray(hydratedCards) && hydratedCards.length) {
    const textSections = messageParagraphs(content).map(paragraph => ({ type: 'text', text: paragraph }))
    return [
      ...textSections,
      ...hydratedCards.slice(0, 8).map(hydratedCardSection)
    ]
  }
  if (!hasConcreteSchoolAnalysis(text)) {
    return messageParagraphs(content).map(paragraph => ({ type: 'text', text: paragraph }))
  }

  const compact = text
    .replace(/\s*---OPTIONS---[\s\S]*$/g, '')
    .replace(/\n+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  const cards = extractSchoolCards(compact)
  if (!cards.length) {
    return messageParagraphs(content).map(paragraph => ({ type: 'text', text: paragraph }))
  }

  const sections = []
  const firstSchoolAt = compact.search(/(?:大学|学院)/)
  const intro = firstSchoolAt > 0 ? compact.slice(0, firstSchoolAt).replace(/[·、，,\s]+$/g, '').trim() : ''
  if (intro) {
    messageParagraphs(intro).slice(0, 2).forEach(paragraph => sections.push({ type: 'text', text: paragraph }))
  }
  cards.forEach(card => sections.push({ type: 'school', ...card }))
  return sections
}

function hydratedCardSection(card) {
  const gapValue = formatSignedGap(card.gap == null ? '' : String(card.gap))
  const quotaValue = card.quota == null || card.quota === '' ? '' : `${card.quota}人`
  return {
    type: 'school',
    school: card.school || card.schoolName || '',
    program: card.program || card.programName || '',
    avg: card.avg == null ? '' : String(card.avg),
    gap: gapValue,
    quota: quotaValue,
    gapTone: gapValue.startsWith('-') ? 'metric-danger' : 'metric-safe',
    quotaTone: Number(card.quota) > 0 && Number(card.quota) <= 9 ? 'metric-warning' : '',
    level: card.level || '',
    levelTone: levelTone(card.level || ''),
    reason: card.reason || ''
  }
}

function hydratedIntroSections(text, hydratedCards) {
  const firstSchool = hydratedCards
    .map(card => card.school || card.schoolName)
    .find(Boolean)
  const cutoff = firstSchool ? text.indexOf(firstSchool) : -1
  const intro = cutoff > 0 ? text.slice(0, cutoff) : text
  return intro
    .replace(/\s*---OPTIONS---[\s\S]*$/g, '')
    .replace(/[-—_=]{3,}/g, '')
    .replace(/[·、，,\s]+$/g, '')
    .split(/\n|(?<=[。！？])/)
    .map(paragraph => paragraph.trim())
    .filter(Boolean)
    .slice(0, 2)
    .map(paragraph => ({ type: 'text', text: paragraph }))
}

// ---- Fixed: extractSchoolCards with proper multi-school handling ----
function extractSchoolCards(text) {
  // STEP 1: Insert newlines before school names so each becomes its own line.
  // Handles many more separator patterns than the old 。；;-only approach.
  const SCHOOL_LA = '(?=[\\u4e00-\\u9fa5A-Za-z0-9（）()·\\-]{2,40}?(?:大学|学院))'

  let normalized = text
    // Punctuation separators: 。；;，、 before school name
    .replace(new RegExp('([。；;，、])[\\s]*' + SCHOOL_LA, 'g'), '$1\n')
    // Transition words: 另外 / 此外 / 还有 / 以及 before school name
    .replace(new RegExp('(另外|此外|还有|以及)\\s*' + SCHOOL_LA, 'g'), '$1\n')
    // Numbered list markers: "1." "2、" "3）" before school name
    .replace(new RegExp('(\\d+[\\.\\、\\)）])\\s*' + SCHOOL_LA, 'g'), '$1\n')

  // STEP 2: Split on newlines, strip leading noise
  const parts = normalized
    .split(/\n/)
    .map(part => part
      .replace(/^[·\-—\s\d+\.\、\)）》、】🔴🟠🟢🔵🟡⚪]+/g, '')
      .replace(/^(?:另外|此外|还有|以及)\s*/g, '')
      .trim()
    )
    .filter(Boolean)

  // STEP 3: Use global scanner so we never drop 2nd/3rd school in same part
  const cards = []
  for (let i = 0; i < parts.length; i++) {
    const found = extractSchoolsFromOnePart(parts[i])
    for (let j = 0; j < found.length; j++) { cards.push(found[j]) }
  }

  return cards.slice(0, 8)
}

/**
 * Scan ONE text block for every school-card pattern using global regex.
 * This is the key fix: the old code used .match() (single match) which
 * dropped schools beyond the first in the same part.
 */
function extractSchoolsFromOnePart(part) {
  const cards = []

  // Format A: "学校名 - 专业名：详细数据" (colon-separated)
  const colonRe = /([一-龥A-Za-z0-9（）()·\-]{2,40}?(?:大学|学院))\s*[·\-—、]?\s*([^：:。；;，,\n]{0,38})[：:]\s*(.+?)(?=[一-龥A-Za-z0-9（）()·\-]{2,40}?(?:大学|学院)|$)/g
  let m
  while ((m = colonRe.exec(part)) !== null) {
    const school = m[1].replace(/^[🔴🟠🟢🔵🟡⚪\s]+/g, '').trim()
    const program = m[2].trim().replace(/[，,。；;]$/g, '')
    const detail = m[3].trim()
    if (isSchoolDetail(detail)) {
      cards.push(buildSchoolCard(school, program, detail))
    }
  }
  if (cards.length > 0) return cards

  // Format B: natural prose without colon
  // e.g. 北京大学计算机技术专硕录取均分345，差距-45
  const proseRe = /([一-龥A-Za-z0-9（）()·\-]{2,40}?(?:大学|学院))\s*[·\-—、]?\s*((?:计算机|软件|人工智能|网络|信息|电子|通信|数据|智能|安全|大数据|网安|AI|软件工程|电子信息|计算机技术|计算机科学)\S{0,20})(?:[，,]\s*|\s+)((?:录取均分|均分|差距|分差|招生|名额|风险|保底|稳妥|冲刺).+?)(?=[一-龥A-Za-z0-9（）()·\-]{2,40}?(?:大学|学院)|$)/g
  while ((m = proseRe.exec(part)) !== null) {
    const pschool = m[1].replace(/^[🔴🟠🟢🔵🟡⚪\s]+/g, '').trim()
    const pprogram = m[2].trim().replace(/[，,。；;]$/g, '')
    const pdetail = m[3].trim()
    if (isSchoolDetail(pdetail)) {
      cards.push(buildSchoolCard(pschool, pprogram, pdetail))
    }
  }

  return cards
}

/** Quick check: does this text contain school-metrics signals? */
function isSchoolDetail(text) {
  return /(均分|录取均分|差距|分差|招|招生|名额|风险|保底|稳妥|冲刺)/.test(text)
}

function buildSchoolCard(school, program, detail) {
  const avg = detail.match(/(?:录取)?均分[：:\s]*(\d{2,3})/)
  const gap = detail.match(/(?:差距|分差|你高|高)[：:\s]*([+-]?\d{1,3})/)
  const quota = detail.match(/(?:统考招生|招生|招|名额)[：:\s]*(\d{1,4})\s*人?/)
  const level = detail.match(/(保底级|保底|稳妥偏保底|稳妥|冲刺|高风险|风险偏高|低风险)/)
  const gapValue = gap ? formatSignedGap(gap[1]) : ''
  const quotaValue = quota ? `${quota[1]}人` : ''
  return {
    school,
    program,
    avg: avg ? avg[1] : '',
    gap: gapValue,
    quota: quotaValue,
    gapTone: gapValue.startsWith('-') ? 'metric-danger' : 'metric-safe',
    quotaTone: quota && Number(quota[1]) <= 9 ? 'metric-warning' : '',
    level: level ? level[1] : '',
    levelTone: levelTone(level ? level[1] : ''),
    reason: compactReason(detail)
  }
}

function formatSignedGap(value) {
  if (!value) return ''
  return value.startsWith('-') || value.startsWith('+') ? value : `+${value}`
}

function levelTone(level) {
  if (/高风险|冲刺|风险偏高/.test(level)) return 'danger'
  if (/保底|低风险/.test(level)) return 'safe'
  return 'warning'
}

function compactReason(detail) {
  return detail
    .replace(/^[，,。；;\s]+/g, '')
    .replace(/\s+/g, ' ')
    .slice(0, 88)
}

function hasConcreteSchoolAnalysis(text) {
  if (!text) return false
  const hasAnalysisSignal = /(分析如下|推荐|候选|学校|院校|录取均分|拟录取|统考招生|录取区间|招生\d|招生\s*\d|差距|分差)/.test(text)
  const hasQuestionOnlySignal = /(你更倾向|你更看重|哪个维度|哪个方向|请选择|咱们直接开始)/.test(text)
  const hasConcreteFact = /(录取均分|均分[：:\s]*\d|差距[：:\s]*[+-]?\d|分差[：:\s]*[+-]?\d|招生[：:\s]*\d|名额[：:\s]*\d|拟录取区间|统考招生)/.test(text)
  return hasAnalysisSignal && hasConcreteFact && !hasQuestionOnlySignal
}

// ---- fallback & error handling ----
async function callChatFallback(text, assistantIndex, streamError) {
  const hadContent = messages.value[assistantIndex] && messages.value[assistantIndex].content
  if (!hadContent) {
    messages.value[assistantIndex].content = friendlyChatError(streamError, '正在尝试普通对话通道...')
  }
  await new Promise(r => setTimeout(r, 2000))
  try {
    const res = await postAiChat({ conversationId: conversationId.value, message: text })
    if (res.data.fallback) {
      messages.value.splice(assistantIndex, 1)
      emit('fallback', res.data)
      return
    }
    messages.value[assistantIndex].content = visibleStreamContent(res.data.message)
    messages.value[assistantIndex].cards = res.data.cards || []
    currentOptions.value = res.data.options || []
    saveToLocal()
  } catch (e) {
    await new Promise(r => setTimeout(r, 3000))
    try {
      const res = await postAiChat({ conversationId: conversationId.value, message: text })
      messages.value[assistantIndex].content = visibleStreamContent(res.data.message || 'AI 对话暂不可用，请稍后重试。')
      messages.value[assistantIndex].cards = res.data.cards || []
      currentOptions.value = res.data.options || []
    } catch (_) {
      messages.value[assistantIndex].content = friendlyChatError(streamError)
    }
  }
}

function friendlyChatError(error, fallback) {
  fallback = fallback || 'AI 对话暂不可用，请稍后重试。'
  const raw = error && error.message ? error.message : ''
  if (!raw) return fallback
  if (/abort|timeout|timed out/i.test(raw)) {
    return '本轮分析耗时较久，已停止等待。你可以缩小问题范围，或直接生成当前推荐报告。'
  }
  if (raw.includes('对话已过期')) {
    return '对话已过期，请重新开始 AI 推荐。'
  }
  return raw
}

// ---- report generation ----
function trackPendingReport(reportId) {
  try {
    const pending = JSON.parse(sessionStorage.getItem('pending_reports') || '[]')
    pending.push({ id: reportId, ts: Date.now() })
    sessionStorage.setItem('pending_reports', JSON.stringify(pending.slice(-5)))
  } catch (_) {}
}

async function generateReport() {
  loading.value = true
  try {
    const res = await postAiGenerateReport({ conversationId: conversationId.value })
    const reportId = res.data.reportId
    trackPendingReport(reportId)
    router.push({ name: 'AiReport', params: { id: reportId } })
  } catch (e) {
    ElMessage.error('生成报告失败')
  } finally {
    loading.value = false
  }
}

// ---- persistence ----
function saveToLocal() {
  try {
    const data = JSON.stringify({ messages: messages.value, options: currentOptions.value })
    if (data.length > 500_000) {
      const trimmed = { messages: messages.value.slice(-10), options: currentOptions.value }
      localStorage.setItem('ai_conv_' + conversationId.value, JSON.stringify(trimmed))
      return
    }
    localStorage.setItem('ai_conv_' + conversationId.value, data)
  } catch (e) { /* quota exceeded, ignore */ }
}

function scrollToBottom() {
  nextTick(() => {
    const el = body.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.ai-chat-panel {
  position: fixed;
  right: -440px;
  top: 0;
  width: 420px;
  height: 100vh;
  background: #f6f9ff;
  box-shadow: -12px 0 34px rgba(31, 64, 124, .16);
  display: flex;
  flex-direction: column;
  transition: right .24s ease;
  z-index: 2000;
  border-left: 1px solid #d9e6f8;
  color: #10213f;
}
.ai-chat-panel.open { right: 0; }
.panel-header {
  min-height: 70px;
  padding: 14px 16px;
  border-bottom: 1px solid #dce8f7;
  background: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.advisor-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.advisor-mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #1f6fff;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(31, 111, 255, .22);
}
.advisor-title strong {
  display: block;
  font-size: 16px;
  line-height: 20px;
  color: #0b1b34;
}
.advisor-title small {
  display: block;
  margin-top: 3px;
  color: #6c7f99;
  font-size: 12px;
  line-height: 16px;
}
.panel-close {
  color: #70829b;
}
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 14px 18px;
}
.panel-body::-webkit-scrollbar {
  width: 8px;
}
.panel-body::-webkit-scrollbar-thumb {
  background: #b8c9df;
  border-radius: 999px;
}
.empty-state {
  text-align: center;
  color: #7c8ca5;
  padding-top: 80px;
}
.empty-state i {
  font-size: 44px;
  display: block;
  margin-bottom: 12px;
  color: #5b8dff;
}
.msg {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}
.msg.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: #e9f1ff;
  border: 1px solid #cfe0fb;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #1f6fff;
}
.msg.assistant .msg-avatar {
  background: #1f6fff;
  color: #fff;
  border-color: #1f6fff;
}
.advisor-card {
  width: min(100%, 334px);
  background: #ffffff;
  border: 1px solid #d7e5f8;
  border-radius: 8px;
  box-shadow: 0 10px 24px rgba(32, 76, 137, .08);
  overflow: hidden;
}
.advisor-card-head {
  min-height: 38px;
  padding: 9px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e8eef7;
  background: #fbfdff;
}
.advisor-card-head span {
  font-size: 13px;
  font-weight: 700;
  color: #0b1b34;
}
.advisor-card-head em {
  font-style: normal;
  font-size: 12px;
  color: #7890ad;
}
.advisor-text {
  padding: 10px 12px 12px;
}
.advisor-text p {
  margin: 0;
  color: #334864;
  font-size: 14px;
  line-height: 1.72;
  word-break: break-word;
}
.advisor-text p + p {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e1e9f3;
}
.school-reco-card {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #dbe8fb;
  border-radius: 8px;
  background: #f8fbff;
}
.advisor-text p + .school-reco-card,
.school-reco-card + p {
  margin-top: 10px;
}
.school-reco-card + .school-reco-card {
  margin-top: 8px;
}
.school-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.school-card-head strong {
  display: block;
  color: #0c1f3f;
  font-size: 14px;
  line-height: 20px;
}
.school-card-head small {
  display: block;
  margin-top: 2px;
  color: #607592;
  font-size: 12px;
  line-height: 17px;
}
.school-level {
  flex-shrink: 0;
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 16px;
  font-weight: 700;
  background: #fff7e8;
  color: #a85d00;
  border: 1px solid #ffe0a6;
}
.school-level.safe {
  background: #edfdf5;
  color: #087443;
  border-color: #c9f1dc;
}
.school-level.danger {
  background: #fff1f1;
  color: #bf2f2f;
  border-color: #ffd1d1;
}
.school-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin-top: 9px;
}
.school-metrics span {
  min-width: 0;
  min-height: 42px;
  padding: 6px 7px;
  border-radius: 7px;
  background: #ffffff;
  border: 1px solid #e3edf8;
  color: #10213f;
  font-size: 13px;
  line-height: 16px;
  font-weight: 700;
}
.school-metrics b {
  display: block;
  margin-bottom: 3px;
  color: #6b7f99;
  font-size: 11px;
  line-height: 14px;
  font-weight: 600;
}
.school-metrics .metric-safe {
  color: #087443;
}
.school-metrics .metric-warning {
  color: #a85d00;
}
.school-metrics .metric-danger {
  color: #bf2f2f;
}
.school-reason {
  margin-top: 8px !important;
  padding-top: 8px;
  border-top: 1px dashed #dce7f4;
  color: #445a76 !important;
  font-size: 13px !important;
  line-height: 1.62 !important;
}
.user-pill {
  max-width: 300px;
  min-height: 34px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #1f6fff;
  color: #fff;
  font-size: 14px;
  line-height: 1.5;
  box-shadow: 0 8px 18px rgba(31, 111, 255, .2);
  word-break: break-word;
}
.thinking-card {
  padding-bottom: 12px;
}
.thinking-steps {
  display: flex;
  gap: 6px;
  padding: 12px;
}
.thinking-steps span {
  width: 48px;
  height: 8px;
  border-radius: 999px;
  background: #dbe8ff;
  animation: loading-bar 1.2s ease-in-out infinite;
}
.thinking-steps span:nth-child(2) { animation-delay: .12s; }
.thinking-steps span:nth-child(3) { animation-delay: .24s; }
@keyframes loading-bar {
  0%, 100% { opacity: .45; transform: scaleX(.72); transform-origin: left; }
  50% { opacity: 1; transform: scaleX(1); }
}
.options-bar {
  padding: 12px 14px;
  border-top: 1px solid #dce8f7;
  background: #ffffff;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.options-title {
  width: 100%;
  color: #63758f;
  font-size: 12px;
  line-height: 16px;
  font-weight: 700;
}
.decision-option {
  margin: 0 !important;
  min-height: 32px;
  border-radius: 7px;
  border-color: #bcd5ff;
  background: #f4f8ff;
  color: #1b63d8;
  font-weight: 600;
}
.decision-option:hover,
.decision-option:focus {
  border-color: #1f6fff;
  background: #eaf2ff;
  color: #0d54ca;
}
.input-bar {
  padding: 10px 14px 12px;
  border-top: 1px solid #dce8f7;
  background: #ffffff;
}
.input-bar :deep(.el-input__wrapper),
.input-bar :deep(.el-input-group__append) {
  box-shadow: 0 0 0 1px #cddbec inset;
}
.report-button {
  margin-top: 8px;
  width: 100%;
  min-height: 34px;
  border-radius: 7px;
  border: none;
  background: #0f9f6e;
  font-weight: 700;
}
@media (max-width: 520px) {
  .ai-chat-panel {
    width: 100vw;
    right: -100vw;
  }
  .advisor-card {
    width: calc(100vw - 72px);
  }
}
</style>
