<template>
  <div class="chat-panel">
    <div ref="msgBox" class="chat-msgs">
      <div v-if="messages.length === 0 && !props.streamingText" class="chat-empty">
        <div class="empty-graphic" aria-hidden="true">
          <span class="g-orb g-orb-1" />
          <span class="g-orb g-orb-2" />
          <span class="g-orb g-orb-3" />
          <div class="g-orb-center">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>
        <p class="empty-hint">可以问我：</p>
        <div class="prompt-chips">
          <button type="button" @click="sendPreset('为什么把当前学校放在稳妥档？')">解释档位</button>
          <button type="button" @click="sendPreset('草稿里哪所学校风险最高？')">检查高风险</button>
          <button type="button" @click="sendPreset('帮我补一个更稳的保底选择')">补保底</button>
          <button type="button" @click="sendPreset('当前草稿的整体录取概率如何？')">整体评估</button>
        </div>
      </div>

      <div v-for="(msg, i) in messages" :key="i" :class="['msg', 'msg--' + msg.role]">
        <span class="msg-avatar" :class="'msg-avatar--' + msg.role">
          {{ msg.role === 'user' ? '我' : 'AI' }}
        </span>
        <div class="msg-body">
          <template v-if="msg.role === 'assistant'">
            <div class="md-body" v-html="renderMarkdown(msg.content)"></div>
          </template>
          <template v-else>
            <p class="msg-text">{{ msg.content }}</p>
          </template>
        </div>
      </div>

      <!-- 流式输出中的消息 -->
      <div v-if="props.streamingText" class="msg msg--assistant msg--streaming">
        <span class="msg-avatar msg-avatar--assistant">AI</span>
        <div class="msg-body">
          <div class="md-body" v-html="renderMarkdown(props.streamingText)"></div>
          <span class="cursor-blink" />
        </div>
      </div>
    </div>

    <div class="chat-input-row">
      <input
        v-model="inputText"
        class="chat-input"
        placeholder="输入消息，回车发送"
        :disabled="streaming"
        @keydown.enter.prevent="handleSend"
      />
      <button
        class="chat-send"
        :disabled="!inputText.trim() || streaming"
        @click="handleSend"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  visible: { type: Boolean, default: false },
  messages: { type: Array, default: () => [] },
  streaming: { type: Boolean, default: false },
  streamingText: { type: String, default: '' }
})

const emit = defineEmits(['send', 'toggle'])

const inputText = ref('')
const msgBox = ref(null)

function handleSend() {
  const text = inputText.value.trim()
  if (!text || props.streaming) return
  inputText.value = ''
  emit('send', text)
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

function sendPreset(text) {
  if (props.streaming) return
  emit('send', text)
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}
</script>

<style scoped>
.chat-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* ── Messages ── */
.chat-msgs {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ── Empty state with graphic ── */
.chat-empty {
  margin: 40px auto 0;
  max-width: 480px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.empty-graphic {
  position: relative;
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.g-orb {
  position: absolute;
  border-radius: 50%;
}

.g-orb-1 {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, var(--brand-soft-2), var(--brand-soft));
  top: 6px;
  left: 6px;
  animation: float 4s var(--ease) infinite;
}

.g-orb-2 {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #f0e9ff, #e6dcff);
  bottom: 6px;
  right: 6px;
  animation: float 4s var(--ease) infinite 0.6s;
}

.g-orb-3 {
  width: 24px;
  height: 24px;
  background: linear-gradient(135deg, var(--warn-soft-2), var(--warn-soft));
  top: 12px;
  right: 4px;
  animation: float 4s var(--ease) infinite 1.2s;
}

.g-orb-center {
  position: relative;
  z-index: 1;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-elev);
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  color: var(--brand);
  box-shadow: 0 6px 16px rgba(36, 78, 156, 0.12);
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(0, -4px); }
}

.empty-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ink-3);
  font-weight: 500;
}

.prompt-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.prompt-chips button {
  padding: 6px 12px;
  background: var(--bg-elev);
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-2);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--t-fast) var(--ease);
}

.prompt-chips button:hover {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand-hover);
  transform: translateY(-1px);
}

/* ── Message bubble ── */
.msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  max-width: 94%;
}

.msg--user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0;
}

.msg-avatar--user {
  background: linear-gradient(135deg, #3a4a66, #1e2740);
  color: #fff;
  box-shadow: 0 3px 8px rgba(30, 39, 64, 0.2);
}

.msg-avatar--assistant {
  background: var(--brand-gradient);
  color: #fff;
  box-shadow: 0 3px 8px rgba(23, 105, 246, 0.3);
}

.msg-body {
  min-width: 0;
  flex: 1;
  padding-top: 4px;
}

.msg-text {
  margin: 0;
  padding: 9px 13px;
  background: var(--ink-1);
  color: #fff;
  border-radius: 14px 14px 4px 14px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
  box-shadow: 0 4px 12px rgba(30, 39, 64, 0.18);
}

.msg--assistant .msg-body {
  background: var(--bg-elev);
  padding: 10px 14px;
  border-radius: 4px 14px 14px 14px;
  border: 1px solid var(--line);
  box-shadow: 0 2px 8px rgba(36, 78, 156, 0.05);
}

.msg--streaming .msg-body { min-height: 24px; }

.cursor-blink {
  display: inline-block;
  margin-left: 2px;
  color: var(--brand);
  font-weight: 700;
  animation: blink 0.9s var(--ease) infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* ── Markdown content ── */
.md-body {
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink-2);
  word-break: break-word;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  margin: 10px 0 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-1);
}

.md-body :deep(p) { margin: 6px 0; }
.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}
.md-body :deep(li) { margin: 3px 0; }

.md-body :deep(code) {
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--brand-soft-3);
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--brand);
}

.md-body :deep(pre) {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--ink-1);
  color: #e7eaf3;
  font-size: 12px;
  overflow-x: auto;
  line-height: 1.5;
}

.md-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.md-body :deep(strong) { font-weight: 700; color: var(--ink-1); }

.md-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 2px solid var(--brand);
  background: var(--brand-soft-3);
  color: var(--ink-2);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
}

.md-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12px;
}

.md-body :deep(th),
.md-body :deep(td) {
  padding: 6px 10px;
  border-bottom: 1px solid var(--line);
  text-align: left;
}

.md-body :deep(th) {
  font-weight: 700;
  color: var(--ink-1);
  background: var(--bg-soft);
}

.md-body :deep(a) {
  color: var(--brand);
  text-decoration: underline;
  text-underline-offset: 2px;
}

/* ── Input row ── */
.chat-input-row {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--line);
  background:
    linear-gradient(180deg, transparent 0%, var(--bg-elev) 30%);
}

.chat-input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  background: var(--bg-elev);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  font-family: inherit;
  font-size: 13px;
  color: var(--ink-1);
  transition: border-color var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease);
}

.chat-input::placeholder { color: var(--ink-5); }

.chat-input:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(23, 105, 246, 0.12);
}

.chat-input:disabled { background: var(--bg-soft); cursor: not-allowed; }

.chat-send {
  height: 38px;
  padding: 0 20px;
  background: var(--brand-gradient);
  color: #fff;
  border: 0;
  border-radius: var(--r-md);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(23, 105, 246, 0.22);
  transition: box-shadow var(--t-fast) var(--ease), transform var(--t-fast) var(--ease);
}

.chat-send:hover:not(:disabled) {
  box-shadow: 0 10px 20px rgba(23, 105, 246, 0.32);
  transform: translateY(-1px);
}

.chat-send:active:not(:disabled) { transform: translateY(0); }

.chat-send:disabled {
  background: var(--line-strong);
  color: var(--ink-4);
  box-shadow: none;
  cursor: not-allowed;
}
</style>
