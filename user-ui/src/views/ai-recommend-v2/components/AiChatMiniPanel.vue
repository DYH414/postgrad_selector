<template>
  <div class="chat-mini">
    <div class="chat-expanded">
      <div class="chat-top">
        <span>AI 择校助手</span>
        <span class="chat-mode">对话调整</span>
      </div>

      <div ref="msgBox" class="chat-msgs">
        <div v-if="messages.length === 0 && !props.streamingText" class="chat-empty">
          <strong>可以直接追问候选理由，或让 AI 调整草稿</strong>
          <p>我会结合当前草稿、分数画像和冲稳保档位解释推荐依据。</p>
          <div class="prompt-chips">
            <button type="button" @click="sendPreset('帮我解释当前稳妥档为什么这样排序')">解释稳妥档</button>
            <button type="button" @click="sendPreset('帮我检查草稿里风险最高的学校')">检查高风险</button>
            <button type="button" @click="sendPreset('帮我补一个更稳的保底选择')">补保底</button>
          </div>
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
          class="send-btn"
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

function sendPreset(text) {
  if (props.streaming) return
  emit('send', text)
}
</script>

<style scoped>
.chat-mini {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f7faff 0%, #f3f7ff 100%);
}
.chat-expanded { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.chat-top {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  border-bottom: 1px solid #f0f4fa;
  font-size: 14px;
  font-weight: 600;
}
.chat-mode {
  flex-shrink: 0;
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: #eef4fb;
  color: #607592;
  font-size: 12px;
  font-weight: 800;
}
.chat-msgs {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 20px;
}
.chat-empty {
  margin: 14px auto 0;
  max-width: 520px;
  padding: 16px;
  border: 1px dashed #cfe0f6;
  border-radius: 10px;
  background: rgba(255,255,255,.74);
  color: #71829a;
  text-align: center;
  font-size: 13px;
  line-height: 22px;
}
.chat-empty strong {
  display: block;
  color: #10213f;
  font-size: 15px;
  line-height: 22px;
}
.chat-empty p {
  margin: 6px 0 0;
}
.prompt-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 10px;
}
.prompt-chips button {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid #d7e6fb;
  border-radius: 999px;
  background: #fff;
  color: #1769f6;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
}
.prompt-chips button:hover {
  border-color: #9dc4ff;
  background: #f4f8ff;
}
.msg {
  margin-bottom: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  max-width: 90%;
}
.msg--user { background: #1769f6; color: #fff; margin-left: auto; }
.msg--assistant { border: 1px solid #e4edf8; background: #fff; color: #303133; }
.cursor-blink { animation: blink .8s infinite; font-weight: 700; color: #409eff; }
@keyframes blink { 50% { opacity: 0; } }
.chat-input-row {
  flex-shrink: 0;
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  border-top: 1px solid #f0f4fa;
  background: rgba(255,255,255,.9);
}
.send-btn {
  flex: none;
  width: 72px;
  font-weight: 800;
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
