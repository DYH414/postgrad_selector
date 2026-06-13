<template>
  <div class="chat-mini">
    <!-- 收起态 -->
    <div v-if="!visible" class="chat-collapsed" @click="$emit('toggle')">
      <span>问 AI 关于草稿的问题</span>
      <i class="el-icon-arrow-up"></i>
    </div>

    <!-- 展开态 -->
    <div v-else class="chat-expanded">
      <div class="chat-top">
        <span>AI 择校助手</span>
        <el-button type="text" size="small" @click="$emit('toggle')">收起</el-button>
      </div>

      <div ref="msgBox" class="chat-msgs">
        <div v-if="messages.length === 0 && !props.streamingText" class="chat-empty">
          问我关于候选学校的问题，或让我帮你调整草稿。例如：<br>
          "换掉 XX 大学"、"分析一下这个学校"、"加一个北京的保底"
        </div>
        <!-- 历史消息 -->
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', 'msg--' + msg.role]">
          <template v-if="msg.role === 'assistant'">
            <div class="md-body" v-html="renderMarkdown(msg.content)"></div>
          </template>
          <template v-else>
            {{ msg.content }}
          </template>
        </div>
        <!-- 流式输出中的消息 -->
        <div v-if="props.streamingText" class="msg msg--assistant">
          <div class="md-body" v-html="renderMarkdown(props.streamingText)"></div>
          <span class="cursor-blink">|</span>
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
}
</script>

<style scoped>
.chat-mini {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #f7faff;
}
.chat-collapsed {
  flex: 1;
  min-height: 0;
  padding: 18px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  cursor: pointer;
  color: #1769f6;
  font-size: 13px;
  transition: background .2s;
}
.chat-collapsed:hover { background: #f3f7ff; }
.chat-collapsed span {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  padding: 0 11px;
  border: 1px solid #d7e6fb;
  border-radius: 999px;
  background: #fff;
  font-weight: 800;
}
.chat-expanded { flex: 1; min-height: 0; display: flex; flex-direction: column; }
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
  min-height: 0;
  overflow-y: auto;
  padding: 12px 16px;
  font-size: 13px;
  line-height: 20px;
}
.chat-empty { color: #a8b2c1; text-align: center; padding: 32px 0; font-size: 13px; line-height: 22px; }
.msg { margin-bottom: 10px; padding: 8px 12px; border-radius: 6px; max-width: 90%; }
.msg--user { background: rgba(64,158,255,.08); color: #303133; margin-left: auto; }
.msg--assistant { background: #f5f7fa; color: #303133; }
.cursor-blink { animation: blink .8s infinite; font-weight: 700; color: #409eff; }
@keyframes blink { 50% { opacity: 0; } }
.chat-input-row {
  display: flex;
  gap: 6px;
  padding: 10px 12px;
  border-top: 1px solid #f0f4fa;
}

/* markdown 渲染样式 */
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3) {
  margin: 8px 0 4px;
  font-size: 14px;
  font-weight: 700;
}
.md-body :deep(p) { margin: 4px 0; }
.md-body :deep(ul), .md-body :deep(ol) {
  margin: 4px 0;
  padding-left: 18px;
}
.md-body :deep(li) { margin: 2px 0; }
.md-body :deep(code) {
  padding: 1px 5px;
  border-radius: 3px;
  background: #e8edf4;
  font-size: 12px;
  font-family: 'SF Mono', 'Cascadia Code', monospace;
}
.md-body :deep(pre) {
  margin: 6px 0;
  padding: 10px;
  border-radius: 6px;
  background: #1e293b;
  color: #e2e8f0;
  font-size: 12px;
  overflow-x: auto;
}
.md-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}
.md-body :deep(strong) { font-weight: 700; }
.md-body :deep(blockquote) {
  margin: 6px 0;
  padding: 6px 12px;
  border-left: 3px solid #409eff;
  background: #f0f6ff;
  color: #4a6fa5;
}
.md-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 6px 0;
  font-size: 12px;
}
.md-body :deep(th), .md-body :deep(td) {
  padding: 4px 8px;
  border: 1px solid #dce7f6;
  text-align: left;
}
.md-body :deep(th) { background: #f0f4fa; font-weight: 700; }
.md-body :deep(a) { color: #409eff; }
</style>
