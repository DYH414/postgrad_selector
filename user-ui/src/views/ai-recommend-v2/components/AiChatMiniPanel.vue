<template>
  <div class="chat-mini-panel">
    <!-- 收起时的触发按钮 -->
    <div v-if="!visible" class="chat-toggle" @click="$emit('toggle')">
      <span>💬 AI 助手</span>
    </div>

    <!-- 展开的对话面板 -->
    <div v-else class="chat-body">
      <div class="chat-header">
        <span>AI 择校助手</span>
        <el-button type="text" size="small" @click="$emit('toggle')">收起</el-button>
      </div>

      <!-- 消息列表 -->
      <div ref="msgListRef" class="chat-messages">
        <div v-if="messages.length === 0" class="chat-empty">
          问我关于候选学校的问题，或让我帮你调整草稿
        </div>
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="chat-msg"
          :class="'chat-msg--' + msg.role"
        >
          {{ msg.content }}
        </div>
        <!-- 流式输出中的消息 -->
        <div v-if="streamingText" class="chat-msg chat-msg--assistant">
          {{ streamingText }}<span class="cursor">|</span>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="chat-input">
        <el-input
          v-model="inputText"
          placeholder="输入消息..."
          :disabled="streaming"
          @keyup.enter="handleSend"
        />
        <el-button
          type="primary"
          size="small"
          :disabled="!inputText.trim() || streaming"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  messages: { type: Array, default: () => [] },
  streaming: { type: Boolean, default: false }
})

const emit = defineEmits(['send', 'toggle'])

const inputText = ref('')
const streamingText = ref('')
const msgListRef = ref(null)

function handleSend() {
  const text = inputText.value.trim()
  if (!text || props.streaming) return
  inputText.value = ''
  emit('send', text)
}
</script>

<style scoped>
.chat-mini-panel {
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  height: 100%;
}
.chat-toggle {
  text-align: center;
  padding: 12px;
  cursor: pointer;
  color: #409eff;
}
.chat-body {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #eee;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.chat-empty { color: #999; font-size: 13px; text-align: center; margin-top: 24px; }
.chat-msg { margin-bottom: 8px; padding: 8px; border-radius: 6px; font-size: 13px; }
.chat-msg--user { background: #ecf5ff; text-align: right; }
.chat-msg--assistant { background: #f5f5f5; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.chat-input {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #eee;
}
</style>
