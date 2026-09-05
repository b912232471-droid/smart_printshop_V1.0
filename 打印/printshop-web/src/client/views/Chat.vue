<template>
  <div class="chat-page">
    <div class="page-heading chat-title-row">
      <div><h1>智能客服</h1><p>{{ statusText }}</p></div>
      <a-button type="primary" :disabled="!status.enabled" @click="newConversation"><PlusOutlined />新会话</a-button>
    </div>

    <div class="agent-workspace surface">
      <aside class="conversation-panel">
        <div class="panel-title"><HistoryOutlined /><span>历史会话</span></div>
        <a-spin :spinning="conversationsLoading">
          <a-empty v-if="!conversationsLoading && !conversations.length" :image="simpleImage" />
          <button v-for="item in conversations" :key="item.id" type="button" :class="['conversation-row', { active: item.id === sessionId }]" @click="openConversation(item)">
            <span>{{ item.title }}</span>
            <a-tooltip title="删除会话"><DeleteOutlined class="delete-conversation" @click.stop="removeConversation(item)" /></a-tooltip>
          </button>
        </a-spin>
      </aside>

      <section class="chat-shell">
        <header class="chat-head">
          <div class="assistant-avatar"><span>AI</span><i :class="['online-dot', { offline: !status.enabled }]" /></div>
          <div class="assistant-meta"><strong>智慧打印 Agent</strong><small>{{ status.enabled ? (status.mode === 'agent' ? 'Agent 模式' : '知识库模式') : '服务已停用' }}</small></div>
          <a-tag v-if="status.deepseekEnabled" color="green">AI大模型</a-tag>
        </header>

        <div ref="scrollRef" class="chat-body">
          <div v-for="item in messages" :key="item.id" :class="['message-row', item.role === 'user' ? 'from-user' : 'from-assistant']">
            <div v-if="item.role !== 'user'" class="message-avatar">AI</div>
            <div class="message-content">
              <div class="message-bubble"><span>{{ item.text }}</span></div>
              <div v-if="item.toolCalls?.length" class="tool-list">
                <div v-for="tool in item.toolCalls" :key="tool.id || tool.tool" class="tool-row">
                  <ToolOutlined /><span>{{ tool.summary || tool.tool }}</span><a-badge :status="tool.status === 'completed' ? 'success' : 'error'" />
                </div>
              </div>
              <div v-if="item.sources?.length" class="source-list">
                <span class="source-label">参考来源</span>
                <a-tooltip v-for="src in item.sources" :key="`${src.sourceType}-${src.id}`" :title="src.question"><a-tag color="blue">{{ src.title || src.category || src.question }}</a-tag></a-tooltip>
              </div>
              <div v-if="item.role === 'assistant' && item.messageId && !item.loading" class="feedback-actions">
                <a-tooltip title="有帮助"><button type="button" :class="{ selected: item.feedback === 1 }" @click="sendFeedback(item, 1)"><LikeOutlined /></button></a-tooltip>
                <a-tooltip title="没帮助"><button type="button" :class="{ selected: item.feedback === -1 }" @click="sendFeedback(item, -1)"><DislikeOutlined /></button></a-tooltip>
              </div>
            </div>
          </div>
        </div>

        <div class="quick-row">
          <button v-for="q in quickQuestions" :key="q" type="button" class="quick-chip" :disabled="sending || !status.enabled" @click="onQuickQuestion(q)">{{ q }}</button>
        </div>

        <footer class="chat-input">
          <a-input v-model:value="inputValue" :disabled="!status.enabled" :placeholder="status.enabled ? '输入你想了解的问题' : 'AI 客服当前未开放'" allow-clear @press-enter="sendMessage" />
          <a-tooltip v-if="sending" title="停止生成"><a-button danger shape="circle" @click="stopGeneration"><StopOutlined /></a-button></a-tooltip>
          <a-button v-else type="primary" shape="circle" :disabled="!status.enabled" @click="sendMessage"><SendOutlined /></a-button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { Empty, message as antMessage, Modal } from 'ant-design-vue'
import { DeleteOutlined, DislikeOutlined, HistoryOutlined, LikeOutlined, PlusOutlined, SendOutlined, StopOutlined, ToolOutlined } from '@ant-design/icons-vue'
import { chatApi } from '@client/api'

const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE
const quickQuestions = ['查询我最近的订单', '附近有哪些营业门店？', '证件照怎么换底色？', '课表同步失败怎么办？']
const welcome = () => ({ id: 'welcome', role: 'assistant', text: '你好，我是智慧打印 Agent。可以查询订单、门店、服务价格，也可以解答平台使用问题。', sources: [] })
const status = ref({ enabled: true, mode: 'agent', deepseekEnabled: false, vectorEnabled: false, serviceHours: '', hotline: '' })
const conversations = ref([])
const conversationsLoading = ref(false)
const messages = ref([welcome()])
const inputValue = ref('')
const sending = ref(false)
const sessionId = ref('')
const scrollRef = ref(null)
let controller = null

const statusText = computed(() => status.value.enabled ? `在线 · 人工服务 ${status.value.serviceHours || '9:00-22:00'}` : '服务暂未开放')

onMounted(async () => {
  try { status.value = await chatApi.status() } catch { status.value.enabled = false }
  await loadConversations()
  scrollToBottom()
})

async function loadConversations() {
  conversationsLoading.value = true
  try { conversations.value = await chatApi.conversations() || [] } finally { conversationsLoading.value = false }
}
async function newConversation() {
  const item = await chatApi.createConversation({ title: '新会话' })
  conversations.value = [item, ...conversations.value]
  sessionId.value = item.id
  messages.value = [welcome()]
}
async function openConversation(item) {
  if (sending.value) stopGeneration()
  const result = await chatApi.conversationMessages(item.id)
  sessionId.value = item.id
  messages.value = result.messages.length ? result.messages.map(message => ({ id: message.id, messageId: message.id, role: message.role, text: message.content, source: message.source, sources: message.sources || [] })) : [welcome()]
  scrollToBottom()
}
function removeConversation(item) {
  Modal.confirm({ title: '删除会话', content: item.title, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => { await chatApi.deleteConversation(item.id); conversations.value = conversations.value.filter(row => row.id !== item.id); if (sessionId.value === item.id) { sessionId.value = ''; messages.value = [welcome()] } } })
}
function scrollToBottom() { nextTick(() => { if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight }) }
function onQuickQuestion(question) { if (!sending.value && question) { inputValue.value = question; sendMessage() } }
async function sendFeedback(item, rating) {
  try { await chatApi.feedback({ messageId: item.messageId, rating, comment: '' }); item.feedback = rating; antMessage.success('反馈已记录') } catch { antMessage.error('反馈提交失败') }
}
function stopGeneration() { controller?.abort(); controller = null; sending.value = false; const loading = messages.value.find(item => item.loading); if (loading) { loading.loading = false; loading.text = '已停止生成。' } }

async function sendMessage() {
  const question = (inputValue.value || '').trim()
  if (!question) return antMessage.warning('请输入问题')
  if (sending.value || !status.value.enabled) return
  const userMessage = { id: `u-${Date.now()}`, role: 'user', text: question }
  const loadingMessage = { id: `a-${Date.now()}`, role: 'assistant', text: '正在分析问题...', sources: [], toolCalls: [], loading: true }
  messages.value = [...messages.value, userMessage, loadingMessage]
  inputValue.value = ''
  sending.value = true
  controller = new AbortController()
  scrollToBottom()
  try {
    await chatApi.askStream({ question, sessionId: sessionId.value || undefined }, {
      signal: controller.signal,
      onEvent(event, payload) {
        if (event === 'status') loadingMessage.text = '正在分析问题...'
        if (event === 'tool') { loadingMessage.text = '正在查询站内信息...'; loadingMessage.toolCalls.push(payload) }
        if (event === 'delta') {
          const chunk = payload.content || ''
          if (loadingMessage.loading && (loadingMessage.text === '正在分析问题...' || loadingMessage.text === '正在查询站内信息...')) {
            loadingMessage.text = chunk
          } else {
            loadingMessage.text = (loadingMessage.text || '') + chunk
          }
        }
        if (event === 'message') {
          loadingMessage.text = payload.answer || loadingMessage.text || '暂时没有找到答案。'
          loadingMessage.source = payload.source || ''
          loadingMessage.sources = payload.sources || []
          loadingMessage.toolCalls = payload.toolCalls || loadingMessage.toolCalls
          loadingMessage.messageId = payload.messageId
          sessionId.value = payload.sessionId || sessionId.value
        }
        if (event === 'error') throw new Error(payload.message || '客服服务暂不可用')
        scrollToBottom()
      }
    })
    loadingMessage.loading = false
    await loadConversations()
  } catch (error) {
    if (error.name !== 'AbortError') { loadingMessage.text = error.message || '客服服务暂不可用，请稍后再试。'; loadingMessage.loading = false }
  } finally { sending.value = false; controller = null; scrollToBottom() }
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
  --chat-text-secondary: #595959;
  --chat-text-muted: #8c8c8c;
  --chat-assistant-bg: #f5f5f5;
  --chat-tool-bg: #fafafa;
  --chat-tool-border: #d9d9d9;
  --chat-chip-bg: #fff;
  --chat-chip-border: #d9d9d9;
  --chat-avatar-border: #fff;
  min-width: 0;
}
.dark .chat-page {
  --chat-border: rgba(255,255,255,.09);
  --chat-primary: #1668dc;
  --chat-primary-hover: #3c89e8;
  --chat-primary-soft: rgba(22,119,255,.18);
  --chat-primary-text: #8abbf8;
  --chat-text-primary: rgba(255,255,255,.9);
  --chat-text-secondary: rgba(255,255,255,.66);
  --chat-text-muted: rgba(255,255,255,.56);
  --chat-assistant-bg: #202026;
  --chat-tool-bg: #1d1d23;
  --chat-tool-border: rgba(255,255,255,.12);
  --chat-chip-bg: #202026;
  --chat-chip-border: #3d3d46;
  --chat-avatar-border: #18181c;
}
.chat-title-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.chat-page :deep(.page-heading p) { color: var(--chat-text-muted); }
.agent-workspace { height: calc(100vh - 180px); min-height: 520px; padding: 0; display: grid; grid-template-columns: 240px minmax(0,1fr); overflow: hidden; }
.conversation-panel { min-width: 0; padding: 14px 10px; overflow-y: auto; border-right: 1px solid var(--chat-border); scrollbar-color: #c1c7d0 transparent; }
.dark .conversation-panel { background: rgba(0,0,0,.16); scrollbar-color: #3d3d46 transparent; }
.conversation-panel::-webkit-scrollbar { width: 7px; }
.conversation-panel::-webkit-scrollbar-thumb { border-radius: 6px; background: #c1c7d0; }
.dark .conversation-panel::-webkit-scrollbar-thumb { background: #3d3d46; }
.panel-title { height: 38px; padding: 0 10px; display: flex; align-items: center; gap: 8px; color: var(--chat-text-secondary); font-weight: 600; }
.conversation-row { width: 100%; min-height: 42px; padding: 8px 10px; display: flex; align-items: center; justify-content: space-between; gap: 8px; border: 0; border-radius: 6px; background: transparent; color: var(--chat-text-secondary); cursor: pointer; text-align: left; transition: color .2s ease, background-color .2s ease; }
.conversation-row > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-row:hover, .conversation-row.active { color: var(--chat-primary); background: var(--chat-primary-soft); }
.dark .conversation-row.active { color: #fff; }
.conversation-row:focus-visible { outline: 2px solid var(--chat-primary); outline-offset: 2px; }
.delete-conversation { flex: 0 0 auto; opacity: 0; }
.conversation-row:hover .delete-conversation, .conversation-row:focus-within .delete-conversation { opacity: 1; }
.chat-shell { min-width: 0; display: flex; flex-direction: column; overflow: hidden; }
.chat-head { min-height: 68px; display: flex; align-items: center; gap: 12px; padding: 12px 20px; border-bottom: 1px solid var(--chat-border); }
.assistant-avatar { position: relative; width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: linear-gradient(135deg, var(--chat-primary), #69b1ff); font-weight: 600; }
.online-dot { position: absolute; right: 0; bottom: 0; width: 10px; height: 10px; border: 2px solid var(--chat-avatar-border); border-radius: 50%; background: #52c41a; }
.online-dot.offline { background: #8f8f99; }
.assistant-meta { min-width: 0; flex: 1; display: flex; flex-direction: column; }
.assistant-meta strong { color: var(--chat-text-primary); }
.assistant-meta small { color: var(--chat-text-muted); font-size: 12px; }
.chat-body { min-height: 0; flex: 1; padding: 20px; overflow-y: auto; scrollbar-color: #c1c7d0 transparent; }
.dark .chat-body { scrollbar-color: #3d3d46 transparent; }
.chat-body::-webkit-scrollbar { width: 7px; }
.chat-body::-webkit-scrollbar-thumb { border-radius: 6px; background: #c1c7d0; }
.dark .chat-body::-webkit-scrollbar-thumb { background: #3d3d46; }
.message-row { display: flex; gap: 10px; margin-bottom: 16px; }
.message-row.from-user { flex-direction: row-reverse; }
.message-avatar { width: 32px; height: 32px; flex: 0 0 32px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: linear-gradient(135deg, var(--chat-primary), #69b1ff); font-size: 12px; font-weight: 600; }
.message-content { max-width: min(76%,760px); display: flex; flex-direction: column; gap: 8px; }
.from-user .message-content { align-items: flex-end; }
.message-bubble { padding: 10px 14px; border-radius: 8px; line-height: 1.7; word-break: break-word; white-space: pre-wrap; }
.from-assistant .message-bubble { color: var(--chat-text-primary); background: var(--chat-assistant-bg); }
.dark .from-assistant .message-bubble { border: 1px solid rgba(255,255,255,.07); }
.from-user .message-bubble { color: #fff; background: linear-gradient(135deg, var(--chat-primary), var(--chat-primary-hover)); box-shadow: 0 4px 14px rgba(22,119,255,.18); }
.source-list { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.source-label { color: var(--chat-text-muted); font-size: 12px; }
.tool-list { width: 100%; display: flex; flex-direction: column; border: 1px solid var(--chat-tool-border); border-radius: 6px; background: var(--chat-tool-bg); overflow: hidden; }
.tool-row { padding: 7px 10px; display: flex; align-items: center; gap: 8px; color: var(--chat-text-secondary); font-size: 12px; border-bottom: 1px solid var(--chat-border); }
.tool-row:last-child { border-bottom: 0; }
.tool-row span { flex: 1; }
.feedback-actions { display: flex; gap: 4px; }
.feedback-actions button { width: 28px; height: 28px; display: grid; place-items: center; border: 0; border-radius: 4px; color: var(--chat-text-muted); background: transparent; cursor: pointer; transition: color .2s ease, background-color .2s ease; }
.feedback-actions button:hover, .feedback-actions button.selected { color: var(--chat-primary-text); background: var(--chat-primary-soft); }
.quick-row { display: flex; gap: 8px; padding: 10px 20px; overflow-x: auto; border-top: 1px solid var(--chat-border); scrollbar-width: none; }
.quick-row::-webkit-scrollbar { display: none; }
.quick-chip { padding: 6px 12px; border: 1px solid var(--chat-chip-border); border-radius: 16px; background: var(--chat-chip-bg); color: var(--chat-text-secondary); font-size: 12px; cursor: pointer; white-space: nowrap; transition: color .2s ease, border-color .2s ease, background-color .2s ease, transform .2s ease; }
.quick-chip:hover:not(:disabled), .quick-chip:focus-visible:not(:disabled) { border-color: var(--chat-primary); color: var(--chat-primary-text); background: var(--chat-primary-soft); }
.quick-chip:active:not(:disabled) { transform: scale(.97); }
.quick-chip:disabled { color: rgba(0,0,0,.25); cursor: not-allowed; }
.dark .quick-chip:disabled { color: rgba(255,255,255,.28); border-color: #303038; background: #1b1b20; }
.chat-input { min-height: 66px; display: flex; align-items: center; gap: 10px; padding: 10px 20px; border-top: 1px solid var(--chat-border); }
@media (max-width: 800px) {
  .chat-title-row { align-items: flex-start; flex-direction: row; }
  .agent-workspace { height: calc(100dvh - 222px); min-height: 0; grid-template-columns: 1fr; }
  .conversation-panel { display: none; }
  .message-content { max-width: 88%; }
  .chat-body { padding: 14px; }
  .chat-head, .quick-row, .chat-input { padding-right: 14px; padding-left: 14px; }
}
</style>
