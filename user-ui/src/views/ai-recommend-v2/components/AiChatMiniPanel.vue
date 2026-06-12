<template>
  <div class="chat-mini">
    <!-- 收起态 -->
    <div v-if="!visible" class="chat-collapsed" @click="$emit('toggle')">
      <span>💬 问 AI 关于草稿的问题</span>
      <i class="el-icon-arrow-up"></i>
    </div>

    <!-- 展开态 -->
    <div v-else class="chat-expanded">
      <div class="chat-top">
        <span>AI 择校助手</span>
        <el-button type="text" size="small" @click="$emit('toggle')">收起</el-button>
      </div>

      <div ref="msgBox" class="chat-msgs">
        <div v-if="messages.length === 0" class="chat-empty">
          问我关于候选学校的问题，或让我帮你调整草稿。例如：<br>
          "换掉 XX 大学"、"分析一下这个学校"、"加一个北京的保底"
        </div>
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', 'msg--' + msg.role]">
          {{ msg.content }}
        </div>
        <div v-if="streamingText" class="msg msg--assistant">
          {{ streamingText }}<span class="cursor-blink">|</span>
        </div>
      </div>

      <div class="chat-input-row">
        <el-input
          v-model="inputText"
          placeholder="输入消息..."
          :disabled="streaming"
          size="small"
          @keyup.enter="handleSend"
        />
        <el-button
          type="primary"
          size="small"
          :disabled="!inputText.trim() || streaming"
          @click="handleSend"
        >发送</el-button>
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
const msgBox = ref(null)

function handleSend() {
  const text = inputText.value.trim()
  if (!text || props.streaming) return
  inputText.value = ''
  emit('send', text)
}
</script>

<style scoped>
.chat-mini {
  border: 1px solid rgba(215,227,245,.9);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(42,84,153,.08);
}
.chat-collapsed {
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  color: #409eff;
  font-size: 13px;
  transition: background .2s;
}
.chat-collapsed:hover { background: #f8fafd; }
.chat-expanded { display: flex; flex-direction: column; height: 480px; }
.chat-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f4fa;
  font-size: 14px;
  font-weight: 600;
}
.chat-msgs {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  font-size: 13px;
  line-height: 20px;
}
.chat-empty { color: #a8b2c1; text-align: center; padding: 32px 0; font-size: 13px; line-height: 22px; }
.msg { margin-bottom: 10px; padding: 8px 12px; border-radius: 6px; max-width: 90%; }
.msg--user { background: rgba(64,158,255,.08); color: #303133; margin-left: auto; }
.msg--assistant { background: #f5f7fa; color: #303133; }
.cursor-blink { animation: blink .8s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.chat-input-row {
  display: flex;
  gap: 6px;
  padding: 10px 12px;
  border-top: 1px solid #f0f4fa;
}
</style>
