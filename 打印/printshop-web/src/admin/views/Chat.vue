<template>
  <div class="chat-page page-shell">
    <div class="welcome-row">
      <div>
        <h2>智能客服</h2>
        <p>体验问答效果。会话直接调用 chat-service /api/chat/ask，使用知识库 + AI大模型 双引擎</p>
      </div>
    </div>

    <a-card class="chat-card" :bordered="false">
      <div class="chat-shell">
        <header class="chat-head">
          <div class="assistant-avatar"><span>AI</span></div>
          <div class="assistant-meta"><strong>智慧打印助手</strong><small>{{ sessionId ? '会话进行中' : '随时为你解答' }}</small></div>
        </header>

        <div ref="scrollRef" class="chat-body">
          <div v-for="item in messages" :key="item.id" :class="['message-row', item.role === 'user' ? 'from-user' : 'from-assistant']">
            <div v-if="item.role !== 'user'" class="message-avatar">AI</div>
            <div class="message-content">
              <div class="message-bubble"><span>{{ item.text }}</span></div>
              <div v-if="item.sources && item.sources.length" class="source-list">
                <span class="source-label">参考来源</span>
                <a-tag v-for="src in item.sources" :key="src.id" color="blue">{{ src.category || src.question }}</a-tag>
              </div>
            </div>
          </div>
        </div>

        <div class="quick-row">
          <button v-for="q in quickQuestions" :key="q" type="button" class="quick-chip" :disabled="sending" @click="onQuickQuestion(q)">{{ q }}</button>
        </div>

        <footer class="chat-input">
          <a-input v-model:value="inputValue" placeholder="输入你想了解的问题" allow-clear @pressEnter="sendMessage" />
          <a-button type="primary" :loading="sending" @click="sendMessage"><SendOutlined /> 发送</a-button>
        </footer>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { SendOutlined } from '@ant-design/icons-vue'
import { chatApi } from '@/api'

const quickQuestions = [
  '如何上传文件并下单打印？',
  '证件照怎么换底色？',
  '课表同步失败怎么办？',
  '怎么联系人工客服？'
]

const messages = ref([
  { id: 'welcome', role: 'assistant', text: '你好，我是智慧打印 AI 客服。可以问我下单、证件照、课表、订单和门店相关问题。', sources: [] }
])
const inputValue = ref('')
const sending = ref(false)
const sessionId = ref('')
const scrollRef = ref(null)

onMounted(() => scrollToBottom())

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

function onQuickQuestion(question) {
  if (sending.value || !question) return
  inputValue.value = question
  sendMessage()
}

async function sendMessage() {
  const question = (inputValue.value || '').trim()
  if (!question) {
    antMessage.warning('请输入问题')
    return
  }
  if (sending.value) return

  const userMessage = { id: `u-${Date.now()}`, role: 'user', text: question }
  const loadingMessage = { id: `a-${Date.now()}`, role: 'assistant', text: '正在整理答案...', sources: [] }
  messages.value = [...messages.value, userMessage, loadingMessage]
  inputValue.value = ''
  sending.value = true
  scrollToBottom()

  try {
    const res = await chatApi.ask({ question, sessionId: sessionId.value || undefined })
    const answer = res?.answer || '暂时没有找到答案，请稍后再试。'
    sessionId.value = res?.sessionId || sessionId.value
    messages.value = messages.value.map(item => item.id === loadingMessage.id
      ? { ...item, text: answer, source: res?.source || '', sources: res?.sources || [] }
      : item)
  } catch (err) {
    const errText = err?.response?.data?.detail || err?.message || '客服服务暂不可用，请稍后再试。'
    messages.value = messages.value.map(item => item.id === loadingMessage.id
      ? { ...item, text: errText }
      : item)
  } finally {
    sending.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.chat-page {
  --chat-border: #f0f0f0;
  --chat-primary: #1677ff;
  --chat-primary-hover: #4096ff;
  --chat-primary-soft: #e6f4ff;
  --chat-primary-text: #69b1ff;
  --chat-text-primary: #262626;
  --chat-text-muted: #8c8c8c;
  --chat-assistant-bg: #f5f5f5;
  --chat-chip-bg: #fff;
  --chat-chip-border: #d9d9d9;
}
.dark .chat-page {
  --chat-border: rgba(255,255,255,.09);
  --chat-primary: #1668dc;
  --chat-primary-hover: #3c89e8;
  --chat-primary-soft: rgba(22,119,255,.18);
  --chat-primary-text: #8abbf8;
  --chat-text-primary: rgba(255,255,255,.9);
  --chat-text-muted: rgba(255,255,255,.58);
  --chat-assistant-bg: #202026;
  --chat-chip-bg: #202026;
  --chat-chip-border: #3d3d46;
}
.chat-card { padding: 0; }
.chat-shell { display: flex; flex-direction: column; height: calc(100vh - 220px); min-height: 480px; padding: 0; overflow: hidden; }
.chat-head { display: flex; align-items: center; gap: 12px; padding: 16px 20px; border-bottom: 1px solid var(--chat-border); }
.assistant-avatar { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 50%; color: #fff; background: linear-gradient(135deg, var(--chat-primary), #69b1ff); font-weight: 600; }
.assistant-meta { display: flex; flex-direction: column; }
.assistant-meta strong { color: var(--chat-text-primary); font-size: 14px; }
.assistant-meta small { color: var(--chat-text-muted); font-size: 12px; }
.chat-body { flex: 1; padding: 18px 20px; overflow-y: auto; scrollbar-color: #c1c7d0 transparent; }
.dark .chat-body { scrollbar-color: #3d3d46 transparent; }
.chat-body::-webkit-scrollbar { width: 7px; }
.chat-body::-webkit-scrollbar-thumb { border-radius: 6px; background: #c1c7d0; }
.dark .chat-body::-webkit-scrollbar-thumb { background: #3d3d46; }
.message-row { display: flex; gap: 10px; margin-bottom: 14px; }
.message-row.from-user { flex-direction: row-reverse; }
.message-avatar { width: 32px; height: 32px; flex: 0 0 32px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: linear-gradient(135deg, var(--chat-primary), #69b1ff); font-size: 12px; font-weight: 600; }
.message-content { max-width: 76%; display: flex; flex-direction: column; gap: 8px; }
.from-user .message-content { align-items: flex-end; }
.message-bubble { padding: 10px 14px; border-radius: 12px; line-height: 1.6; word-break: break-word; }
.from-assistant .message-bubble { color: var(--chat-text-primary); background: var(--chat-assistant-bg); border-top-left-radius: 2px; }
.dark .from-assistant .message-bubble { border: 1px solid rgba(255,255,255,.07); }
.from-user .message-bubble { color: #fff; background: linear-gradient(135deg, var(--chat-primary), var(--chat-primary-hover)); border-top-right-radius: 2px; box-shadow: 0 4px 14px rgba(22,119,255,.18); }
.source-list { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.source-label { color: var(--chat-text-muted); font-size: 12px; }
.quick-row { display: flex; gap: 8px; padding: 10px 20px; overflow-x: auto; border-top: 1px solid var(--chat-border); scrollbar-width: none; }
.quick-row::-webkit-scrollbar { display: none; }
.quick-chip { padding: 6px 12px; border: 1px solid var(--chat-chip-border); border-radius: 16px; background: var(--chat-chip-bg); color: rgba(0,0,0,.65); font-size: 12px; cursor: pointer; white-space: nowrap; transition: color .2s ease, border-color .2s ease, background-color .2s ease, transform .2s ease; }
.dark .quick-chip { color: rgba(255,255,255,.68); }
.quick-chip:hover:not(:disabled), .quick-chip:focus-visible:not(:disabled) { border-color: var(--chat-primary); color: var(--chat-primary-text); background: var(--chat-primary-soft); }
.quick-chip:active:not(:disabled) { transform: scale(.97); }
.quick-chip:disabled { color: rgba(0,0,0,.25); cursor: not-allowed; }
.dark .quick-chip:disabled { color: rgba(255,255,255,.28); border-color: #303038; background: #1b1b20; }
.chat-input { display: flex; gap: 8px; padding: 12px 20px 16px; border-top: 1px solid var(--chat-border); }
@media (max-width: 600px) {
  .chat-shell { height: calc(100vh - 200px); }
  .message-content { max-width: 88%; }
}
</style>
