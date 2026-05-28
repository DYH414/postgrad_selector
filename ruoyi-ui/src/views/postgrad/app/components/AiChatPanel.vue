<template>
  <div class="ai-chat-panel" :class="{ open: visible }">
    <div class="panel-header">
      <span><i class="el-icon-chat-dot-round" /> AI 择校顾问</span>
      <el-button type="text" icon="el-icon-close" @click="$emit('close')" />
    </div>

    <div class="panel-body" ref="body">
      <div v-if="messages.length === 0 && !loading" class="empty-state">
        <i class="el-icon-chat-line-round" />
        <p>点击「AI 推荐」开始智能择校对话</p>
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" :class="['msg', msg.role]">
        <div class="msg-avatar">
          <i v-if="msg.role === 'assistant'" class="el-icon-cpu" />
          <i v-else class="el-icon-user" />
        </div>
        <div class="msg-bubble">{{ msg.content }}</div>
      </div>

      <div v-if="loading" class="msg assistant">
        <div class="msg-avatar"><i class="el-icon-cpu" /></div>
        <div class="msg-bubble typing"><span>.</span><span>.</span><span>.</span></div>
      </div>
    </div>

    <div v-if="currentOptions.length > 0 && !loading" class="options-bar">
      <el-button v-for="(opt, i) in currentOptions" :key="i"
        size="small" type="primary" plain @click="sendOption(opt)">
        {{ opt }}
      </el-button>
    </div>

    <div class="input-bar">
      <el-input v-model="input" placeholder="输入你的想法..."
        size="small" @keyup.enter.native="sendMessage">
        <el-button slot="append" icon="el-icon-s-promotion"
          :disabled="!input.trim() || loading" @click="sendMessage" />
      </el-input>
      <el-button v-if="messages.length >= 4" type="success" size="small"
        style="margin-top:6px;width:100%" @click="generateReport">
        生成推荐报告
      </el-button>
    </div>
  </div>
</template>

<script>
import { postAiStart, postAiChat, postAiGenerateReport } from '@/api/postgrad/ai'

export default {
  name: 'AiChatPanel',
  props: {
    visible: { type: Boolean, default: false },
    candidateIds: { type: Array, default: () => [] }
  },
  data() {
    return {
      conversationId: null,
      messages: [],
      currentOptions: [],
      input: '',
      loading: false
    }
  },
  watch: {
    visible(val) {
      if (val && !this.conversationId) this.startConversation()
    }
  },
  methods: {
    async startConversation() {
      this.loading = true
      try {
        const res = await postAiStart({ candidateIds: this.candidateIds })
        this.conversationId = res.data.conversationId
        this.messages = [{ role: 'assistant', content: res.data.message }]
        this.currentOptions = res.data.options || []
        this.saveToLocal()
      } catch (e) {
        this.$message.error('启动 AI 对话失败')
      } finally {
        this.loading = false
      }
    },

    async sendMessage() {
      const text = this.input.trim()
      if (!text || this.loading) return
      this.messages.push({ role: 'user', content: text })
      this.input = ''
      this.currentOptions = []
      this.loading = true
      await this.callChat(text)
      this.scrollToBottom()
    },

    sendOption(opt) {
      this.messages.push({ role: 'user', content: opt })
      this.currentOptions = []
      this.loading = true
      this.callChat(opt)
      this.scrollToBottom()
    },

    async callChat(text) {
      try {
        const res = await postAiChat({ conversationId: this.conversationId, message: text })
        if (res.data.fallback) {
          this.$emit('fallback', res.data)
          return
        }
        this.messages.push({ role: 'assistant', content: res.data.message })
        this.currentOptions = res.data.options || []
        this.saveToLocal()
      } catch (e) {
        this.$message.error('对话请求失败')
      } finally {
        this.loading = false
      }
    },

    async generateReport() {
      this.loading = true
      try {
        const res = await postAiGenerateReport({ conversationId: this.conversationId })
        this.$router.push({ name: 'AiReport', params: { id: res.data.reportId } })
      } catch (e) {
        this.$message.error('生成报告失败')
      } finally {
        this.loading = false
      }
    },

    saveToLocal() {
      try {
        localStorage.setItem('ai_conv_' + this.conversationId,
          JSON.stringify({ messages: this.messages, options: this.currentOptions }))
      } catch (e) { /* quota exceeded, ignore */ }
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.body
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  }
}
</script>

<style scoped>
.ai-chat-panel {
  position: fixed; right: -420px; top: 0; width: 400px; height: 100vh;
  background: #fff; box-shadow: -2px 0 12px rgba(0,0,0,.1);
  display: flex; flex-direction: column; transition: right .3s; z-index: 2000;
}
.ai-chat-panel.open { right: 0; }
.panel-header {
  padding: 12px 16px; border-bottom: 1px solid #ebeef5;
  display: flex; justify-content: space-between; align-items: center;
  font-weight: 600;
}
.panel-body { flex: 1; overflow-y: auto; padding: 12px; }
.empty-state { text-align: center; color: #909399; padding-top: 80px; }
.empty-state i { font-size: 48px; display: block; margin-bottom: 12px; }
.msg { display: flex; margin-bottom: 12px; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%;
  background: #f0f2f5; display: flex; align-items: center; justify-content: center;
  margin: 0 8px; flex-shrink: 0; }
.msg.assistant .msg-avatar { background: #409eff; color: #fff; }
.msg-bubble { max-width: 280px; padding: 8px 12px; border-radius: 12px;
  font-size: 14px; line-height: 1.6; }
.msg.assistant .msg-bubble { background: #f0f2f5; border-top-left-radius: 2px; }
.msg.user .msg-bubble { background: #409eff; color: #fff; border-top-right-radius: 2px; }
.typing span { animation: blink 1.4s infinite both; }
.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,80%,100% { opacity: 0; } 40% { opacity: 1; } }
.options-bar { padding: 8px 12px; display: flex; flex-wrap: wrap; gap: 6px; }
.input-bar { padding: 8px 12px; border-top: 1px solid #ebeef5; }
</style>
