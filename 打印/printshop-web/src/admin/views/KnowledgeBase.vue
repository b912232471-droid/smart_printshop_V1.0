<template>
  <div class="page-shell knowledge-page">
    <div class="page-toolbar page-head">
      <div class="page-toolbar__title">
        <h2>AI 客服运营</h2>
        <a-space>
          <a-badge :status="settings.enabled ? 'success' : 'default'" :text="settings.enabled ? '已启用' : '已停用'" />
          <a-tag :color="settings.mode === 'agent' ? 'blue' : 'default'">{{ settings.mode === 'agent' ? 'Agent' : '知识库模式' }}</a-tag>
        </a-space>
      </div>
      <a-button :loading="pageLoading" @click="loadAll"><ReloadOutlined />刷新</a-button>
    </div>

    <div class="metric-grid">
      <div class="metric"><span>会话</span><strong>{{ overview.conversations }}</strong></div>
      <div class="metric"><span>消息</span><strong>{{ overview.messages }}</strong></div>
      <div class="metric"><span>知识文档</span><strong>{{ overview.readyDocuments }}/{{ overview.documents }}</strong></div>
      <div class="metric"><span>回答反馈</span><strong class="feedback"><span class="positive">{{ overview.feedbackPositive }}</span><i>/</i><span class="negative">{{ overview.feedbackNegative }}</span></strong></div>
      <div class="metric"><span>知识命中率</span><strong>{{ overview.knowledgeHitRate }}%</strong></div>
      <div class="metric"><span>无答案</span><strong>{{ overview.fallbackAnswers }}/{{ overview.asks }}</strong></div>
    </div>

    <a-tabs v-model:active-key="activeTab" class="knowledge-tabs">
      <a-tab-pane key="settings" tab="运行策略">
        <div class="settings-layout">
          <a-form class="settings-form" layout="vertical">
            <a-row :gutter="20">
              <a-col :xs="24" :md="12">
                <a-form-item label="客服状态">
                  <a-switch v-model:checked="settings.enabled" checked-children="启用" un-checked-children="停用" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="运行模式">
                  <a-segmented v-model:value="settings.mode" :options="modeOptions" block />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="AI大模型">
                  <a-switch v-model:checked="settings.deepseekEnabled" checked-children="启用" un-checked-children="停用" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="模型">
                  <a-input v-model:value="settings.model" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="温度">
                  <a-slider v-model:value="settings.temperature" :min="0" :max="1.5" :step="0.1" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="最大输出 Token">
                  <a-input-number v-model:value="settings.maxTokens" :min="128" :max="4000" :step="128" style="width:100%" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="Agent 工具">
              <a-checkbox-group v-model:value="settings.allowedTools" :options="toolOptions" class="tool-grid" />
            </a-form-item>
            <a-form-item label="系统提示词">
              <a-textarea v-model:value="settings.systemPrompt" :rows="7" :maxlength="6000" show-count />
            </a-form-item>
            <a-row :gutter="20">
              <a-col :xs="24" :md="12"><a-form-item label="系统提示词版本"><a-input v-model:value="settings.systemPromptVersion" placeholder="例如 v1" :maxlength="80" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item label="人工服务时间"><a-input v-model:value="settings.serviceHours" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item label="人工客服电话"><a-input v-model:value="settings.hotline" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item label="人工转接提示语"><a-input v-model:value="settings.handoffMessage" :maxlength="500" show-count /></a-form-item></a-col>
            </a-row>
            <a-button type="primary" :loading="settingsSaving" @click="saveSettings"><SaveOutlined />保存策略</a-button>
          </a-form>

          <section class="run-panel">
            <div class="section-head"><h3>最近运行</h3></div>
            <a-empty v-if="!overview.recentRuns.length" :image="simpleImage" />
            <div v-else class="run-list">
              <div v-for="run in overview.recentRuns" :key="run.id" class="run-row">
                <div><strong>#{{ run.id }}</strong><span>{{ run.mode }} · {{ run.model }}</span></div>
                <div class="run-meta"><a-badge :status="run.status === 'completed' ? 'success' : run.status === 'running' ? 'processing' : 'error'" :text="run.status" /><span>{{ run.durationMs }}ms</span></div>
              </div>
            </div>
          </section>
        </div>
      </a-tab-pane>

      <a-tab-pane key="documents" tab="Markdown 文档">
        <div class="section-head document-head">
          <div><h3>文档知识库</h3><span>共 {{ documents.length }} 篇</span></div>
          <a-space>
            <a-button @click="openRetrievalTest"><SearchOutlined />测试检索</a-button>
            <a-upload accept=".md,.markdown" :show-upload-list="false" :before-upload="handleMarkdownUpload">
              <a-button type="primary" :loading="documentImporting"><UploadOutlined />导入 Markdown</a-button>
            </a-upload>
          </a-space>
        </div>
        <a-table :columns="documentColumns" :data-source="documents" :loading="documentsLoading" row-key="id" :scroll="{ x: 1100 }" :pagination="{ pageSize: 10 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'document'">
              <div class="document-cell"><FileTextOutlined /><div><strong>{{ record.title }}</strong><span>{{ record.filename }} · v{{ record.version }}</span></div></div>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-badge :status="documentStatus(record.status).status" :text="documentStatus(record.status).text" />
              <div v-if="record.ingestionStatus || record.ingestionProgress" class="document-progress">
                任务 {{ record.ingestionStatus || '-' }} · {{ record.ingestionProgress || 0 }}%
              </div>
              <div v-if="record.ingestionError || record.errorMessage" class="document-error">{{ record.ingestionError || record.errorMessage }}</div>
            </template>
            <template v-else-if="column.key === 'enabled'"><a-switch :checked="record.enabled" :disabled="!['ready', 'keyword_only'].includes(record.status)" size="small" @change="value => toggleDocument(record, value)" /></template>
            <template v-else-if="column.key === 'action'">
              <a-space>
                <a-tooltip title="查看知识块"><a-button type="text" shape="circle" @click="previewChunks(record)"><EyeOutlined /></a-button></a-tooltip>
                <a-upload accept=".md,.markdown" :show-upload-list="false" :before-upload="file => handleReplaceDocument(record, file)">
                  <a-tooltip title="替换文档"><a-button type="text" shape="circle"><UploadOutlined /></a-button></a-tooltip>
                </a-upload>
                <a-tooltip :title="record.status === 'failed' || record.ingestionStatus === 'failed' ? '重试' : '重建索引'">
                  <a-button type="text" shape="circle" @click="reindex(record)"><ReloadOutlined /></a-button>
                </a-tooltip>
                <a-tooltip title="删除文档"><a-button type="text" danger shape="circle" @click="removeDocument(record)"><DeleteOutlined /></a-button></a-tooltip>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="faq" tab="人工 FAQ">
        <div class="faq-toolbar">
          <div class="toolbar-actions">
            <a-input v-model:value="filters.keyword" placeholder="搜索问题或答案" allow-clear @press-enter="loadKnowledge"><template #prefix><SearchOutlined /></template></a-input>
            <a-input v-model:value="filters.category" placeholder="分类" allow-clear @press-enter="loadKnowledge" />
            <a-checkbox v-model:checked="filters.includeDisabled">包含已停用</a-checkbox>
            <a-button @click="loadKnowledge">查询</a-button>
          </div>
          <div class="toolbar-actions">
            <a-upload accept=".csv,.xlsx" :show-upload-list="false" :before-upload="handleImportFile"><a-button :loading="importing"><UploadOutlined />批量导入</a-button></a-upload>
            <a-button type="primary" @click="openCreate"><PlusOutlined />新增问答</a-button>
          </div>
        </div>
        <a-table :columns="faqColumns" :data-source="items" :loading="loading" row-key="id" :scroll="{ x: 1050 }" :pagination="{ pageSize: 10 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'question'"><div class="question-cell"><strong>{{ record.question }}</strong><span>{{ record.answer }}</span></div></template>
            <template v-else-if="column.key === 'category'"><a-tag color="blue">{{ record.category || '通用' }}</a-tag></template>
            <template v-else-if="column.key === 'tags'"><a-space wrap><a-tag v-for="tag in record.tags || []" :key="tag">{{ tag }}</a-tag><span v-if="!record.tags?.length" class="muted">无</span></a-space></template>
            <template v-else-if="column.key === 'enabled'"><a-badge :status="record.enabled ? 'success' : 'default'" :text="record.enabled ? '启用' : '停用'" /></template>
            <template v-else-if="column.key === 'action'"><a-space><a-button type="link" size="small" @click="openEdit(record)">编辑</a-button><a-button type="link" danger size="small" @click="remove(record)">删除</a-button></a-space></template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="dialogVisible" :title="isEdit ? '编辑问答' : '新增问答'" :confirm-loading="saving" ok-text="保存" cancel-text="取消" width="680px" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="问题" required><a-input v-model:value="form.question" /></a-form-item>
        <a-form-item label="答案" required><a-textarea v-model:value="form.answer" :rows="6" /></a-form-item>
        <a-row :gutter="16"><a-col :span="12"><a-form-item label="分类"><a-input v-model:value="form.category" /></a-form-item></a-col><a-col :span="12"><a-form-item label="标签"><a-input v-model:value="form.tagsText" /></a-form-item></a-col></a-row>
        <a-form-item label="状态"><a-switch v-model:checked="form.enabled" checked-children="启用" un-checked-children="停用" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="chunksVisible" :title="chunkDocument?.title || '知识块'" width="860px" :footer="null">
      <a-spin :spinning="chunksLoading">
        <a-empty v-if="!chunksLoading && !chunks.length" />
        <div v-else class="chunk-list">
          <article v-for="chunk in chunks" :key="chunk.id" class="chunk-row">
            <header><strong>#{{ chunk.chunkIndex + 1 }} {{ chunk.heading }}</strong><a-tag>{{ chunk.vectorStatus }}</a-tag></header>
            <p>{{ chunk.content }}</p>
          </article>
        </div>
      </a-spin>
    </a-modal>

    <a-modal v-model:open="retrievalVisible" title="测试检索效果" width="760px" :footer="null">
      <a-form layout="vertical">
        <a-form-item label="检索问题" required>
          <a-input v-model:value="retrievalQuery" placeholder="输入要测试的问题或关键词" allow-clear @press-enter="runRetrievalTest" />
        </a-form-item>
        <a-button type="primary" :loading="retrievalLoading" @click="runRetrievalTest"><SearchOutlined />开始检索</a-button>
      </a-form>
      <a-spin :spinning="retrievalLoading">
        <a-empty v-if="!retrievalLoading && retrievalSources.length === 0 && retrievalQueried" description="没有命中结果" />
        <div v-else class="retrieval-list">
          <article v-for="(item, index) in retrievalSources" :key="`${item.sourceType}-${item.id}-${index}`" class="retrieval-row">
            <header>
              <strong>{{ item.title || item.question }}</strong>
              <a-space>
                <a-tag color="blue">{{ item.sourceType }}</a-tag>
                <a-tag>{{ Number(item.score || 0).toFixed(3) }}</a-tag>
              </a-space>
            </header>
            <p>{{ item.question }}</p>
          </article>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { Empty, message, Modal } from 'ant-design-vue'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { DeleteOutlined, EyeOutlined, FileTextOutlined, PlusOutlined, ReloadOutlined, SaveOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { chatApi } from '@/api'

const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE
const activeTab = ref('settings')
const pageLoading = ref(false)
const settingsSaving = ref(false)
const documentImporting = ref(false)
const documentsLoading = ref(false)
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const chunksLoading = ref(false)
const items = ref([])
const total = ref(0)
const documents = ref([])
const chunks = ref([])
const chunkDocument = ref(null)
const chunksVisible = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const retrievalVisible = ref(false)
const retrievalLoading = ref(false)
const retrievalQuery = ref('')
const retrievalSources = ref([])
const retrievalQueried = ref(false)
let pollTimer = null

const overview = reactive({ conversations: 0, messages: 0, documents: 0, readyDocuments: 0, feedbackPositive: 0, feedbackNegative: 0, asks: 0, knowledgeHitRate: 0, fallbackAnswers: 0, recentRuns: [] })
const settings = reactive({ enabled: true, mode: 'agent', deepseekEnabled: true, allowedTools: [], systemPrompt: '', systemPromptVersion: 'v1', model: 'deepseek-chat', temperature: 0.2, maxTokens: 800, hotline: '400-778-1811', serviceHours: '9:00-22:00', handoffMessage: '当前问题需要人工客服进一步处理。' })
const modeOptions = [{ label: '知识库', value: 'knowledge_only' }, { label: 'Agent', value: 'agent' }]
const toolOptions = [
  { label: '知识检索', value: 'search_knowledge' }, { label: '指定订单', value: 'get_my_order' },
  { label: '最近订单', value: 'list_my_orders' }, { label: '门店', value: 'get_store_info' },
  { label: '服务价格', value: 'get_service_price' }, { label: '课表状态', value: 'get_schedule_status' },
  { label: '证件照状态', value: 'get_photo_service_status' }, { label: '转人工', value: 'handoff_to_human' }
]
const filters = ref({ keyword: '', category: '', includeDisabled: false })
const form = ref({ id: null, question: '', answer: '', category: '通用', tagsText: '', enabled: true })
const faqColumns = [{ title: '问答内容', key: 'question', width: 430 }, { title: '分类', key: 'category', width: 110 }, { title: '标签', key: 'tags', width: 220 }, { title: '状态', key: 'enabled', width: 100 }, { title: '操作', key: 'action', width: 130 }]
const documentColumns = [{ title: '文档', key: 'document', width: 340 }, { title: '分类', dataIndex: 'category', width: 120 }, { title: '索引状态', key: 'status', width: 220 }, { title: '知识块', dataIndex: 'chunkCount', width: 90 }, { title: '启用', key: 'enabled', width: 90 }, { title: '操作', key: 'action', width: 190 }]

function applySettings(value) { Object.assign(settings, value || {}) }
function applyOverview(value) { Object.assign(overview, value || {}) }
async function loadAll() {
  pageLoading.value = true
  try {
    const [policy, stats] = await Promise.all([chatApi.getSettings(), chatApi.overview()])
    applySettings(policy); applyOverview(stats)
    await Promise.all([loadDocuments(), loadKnowledge()])
  } finally { pageLoading.value = false }
}
async function saveSettings() {
  settingsSaving.value = true
  try {
    applySettings(await chatApi.updateSettings({
      enabled: settings.enabled, mode: settings.mode, deepseekEnabled: settings.deepseekEnabled,
      allowedTools: settings.allowedTools, systemPrompt: settings.systemPrompt,
      systemPromptVersion: settings.systemPromptVersion, model: settings.model,
      temperature: settings.temperature, maxTokens: settings.maxTokens, hotline: settings.hotline,
      serviceHours: settings.serviceHours, handoffMessage: settings.handoffMessage
    }))
    message.success('运行策略已保存')
  } finally { settingsSaving.value = false }
}
async function loadDocuments() {
  documentsLoading.value = true
  try { const res = await chatApi.getDocuments(); documents.value = res.items || [] }
  finally { documentsLoading.value = false }
}
async function loadKnowledge() {
  loading.value = true
  try { const res = await chatApi.getKnowledge(filters.value); items.value = res.items || []; total.value = res.total || 0 }
  finally { loading.value = false }
}
const documentStatus = status => ({
  pending: { status: 'default', text: '等待处理' }, processing: { status: 'processing', text: '正在索引' },
  ready: { status: 'success', text: '向量索引完成' }, keyword_only: { status: 'warning', text: '关键词索引' },
  failed: { status: 'error', text: '索引失败' }
}[status] || { status: 'default', text: status })
async function toggleDocument(record, enabled) { await chatApi.setDocumentEnabled(record.id, enabled); record.enabled = enabled; message.success(enabled ? '文档已启用' : '文档已停用') }
async function reindex(record) {
  await chatApi.reindexDocument(record.id)
  message.success(record.status === 'failed' || record.ingestionStatus === 'failed' ? '已提交重试任务' : '已提交重建任务')
  loadDocuments()
}
async function previewChunks(record) { chunkDocument.value = record; chunks.value = []; chunksVisible.value = true; chunksLoading.value = true; try { chunks.value = await chatApi.getDocumentChunks(record.id) } finally { chunksLoading.value = false } }
function removeDocument(record) { Modal.confirm({ title: '删除文档', content: record.title, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => { await chatApi.deleteDocument(record.id); message.success('文档已删除'); loadDocuments() } }) }
function openRetrievalTest() {
  retrievalVisible.value = true
  retrievalQuery.value = ''
  retrievalSources.value = []
  retrievalQueried.value = false
}
async function runRetrievalTest() {
  const query = retrievalQuery.value.trim()
  if (!query) return message.warning('请输入检索问题')
  retrievalLoading.value = true
  try {
    const result = await chatApi.testRetrieval({ query, limit: 6 })
    retrievalSources.value = result.sources || []
    retrievalQueried.value = true
  } finally { retrievalLoading.value = false }
}
function handleReplaceDocument(record, file) {
  ;(async () => {
    if (file.size > 5 * 1024 * 1024) return message.warning('文件不能超过 5MB')
    documentImporting.value = true
    try {
      const document = await chatApi.replaceDocument(record.id, {
        filename: file.name,
        title: file.name.replace(/\.(md|markdown)$/i, '') || record.title,
        category: record.category || '项目文档',
        contentBase64: arrayBufferToBase64(await file.arrayBuffer())
      })
      message.success(`已替换为 ${document.title} · v${document.version}`)
      loadDocuments()
    } finally { documentImporting.value = false }
  })()
  return false
}
function openCreate() { isEdit.value = false; form.value = { id: null, question: '', answer: '', category: '通用', tagsText: '', enabled: true }; dialogVisible.value = true }
function openEdit(row) { isEdit.value = true; form.value = { id: row.id, question: row.question || '', answer: row.answer || '', category: row.category || '通用', tagsText: (row.tags || []).join(','), enabled: Boolean(row.enabled) }; dialogVisible.value = true }
const faqPayload = () => ({ question: form.value.question.trim(), answer: form.value.answer.trim(), category: form.value.category.trim() || '通用', tags: form.value.tagsText.split(/[,，]/).map(tag => tag.trim()).filter(Boolean), enabled: form.value.enabled })
async function save() { const data = faqPayload(); if (!data.question || !data.answer) return message.warning('请填写问题和答案'); saving.value = true; try { if (isEdit.value) await chatApi.updateKnowledge(form.value.id, data); else await chatApi.createKnowledge(data); message.success('保存成功'); dialogVisible.value = false; loadKnowledge() } finally { saving.value = false } }
function remove(row) { Modal.confirm({ title: '删除问答', content: row.question, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => { await chatApi.deleteKnowledge(row.id); message.success('删除成功'); loadKnowledge() } }) }
const arrayBufferToBase64 = buffer => { const bytes = new Uint8Array(buffer); let binary = ''; for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000)); return btoa(binary) }
function handleImportFile(file) { (async () => { const ext = file.name.split('.').pop().toLowerCase(); if (!['csv', 'xlsx'].includes(ext)) return message.warning('仅支持 CSV 或 XLSX'); if (file.size > 2 * 1024 * 1024) return message.warning('文件不能超过 2MB'); importing.value = true; try { const result = await chatApi.importKnowledge({ filename: file.name, contentBase64: arrayBufferToBase64(await file.arrayBuffer()) }); message.success(`新增 ${result.imported} 条，跳过 ${result.skipped} 条，失败 ${result.failed} 条`); loadKnowledge() } finally { importing.value = false } })(); return false }
function handleMarkdownUpload(file) { (async () => { if (file.size > 5 * 1024 * 1024) return message.warning('文件不能超过 5MB'); documentImporting.value = true; try { const document = await chatApi.importDocument({ filename: file.name, title: file.name.replace(/\.(md|markdown)$/i, ''), category: '项目文档', contentBase64: arrayBufferToBase64(await file.arrayBuffer()) }); message.success(`已导入 ${document.title}`); loadDocuments() } finally { documentImporting.value = false } })(); return false }

onMounted(() => { loadAll(); pollTimer = window.setInterval(() => { if (documents.value.some(item => ['pending', 'processing'].includes(item.status))) loadDocuments() }, 5000) })
onBeforeUnmount(() => { if (pollTimer) window.clearInterval(pollTimer) })
</script>

<style scoped lang="less">
.knowledge-page { min-width: 0; }.page-head { margin-bottom: 18px; }.page-toolbar__title { display:flex; flex-direction:column; gap:7px; }.page-toolbar__title h2, .section-head h3 { margin:0; }.metric-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); border:1px solid #f0f0f0; border-radius:6px; background:#fff; }.metric { min-width:0; padding:18px 20px; display:flex; flex-direction:column; gap:6px; border-right:1px solid #f0f0f0; border-bottom:1px solid #f0f0f0; }.metric:nth-child(3n) { border-right:0; }.metric:nth-last-child(-n+3) { border-bottom:0; }.metric span { color:#8c8c8c; font-size:12px; }.metric strong { color:#262626; font-size:24px; }.feedback { display:flex; align-items:center; gap:8px; }.feedback i { color:#d9d9d9; font-style:normal; }.feedback .positive { color:#389e0d; font-size:24px; }.feedback .negative { color:#cf1322; font-size:24px; }.knowledge-tabs { margin-top:18px; }.settings-layout { display:grid; grid-template-columns:minmax(0,1fr) 320px; gap:28px; }.settings-form { max-width:900px; }.tool-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; }.run-panel { min-width:0; padding-left:24px; border-left:1px solid #f0f0f0; }.section-head { min-height:40px; display:flex; align-items:center; justify-content:space-between; }.run-list { display:flex; flex-direction:column; }.run-row { padding:13px 0; display:flex; align-items:center; justify-content:space-between; gap:12px; border-bottom:1px solid #f5f5f5; }.run-row > div { min-width:0; display:flex; flex-direction:column; gap:3px; }.run-row span { color:#8c8c8c; font-size:12px; }.run-meta { align-items:flex-end; }.document-head, .faq-toolbar { margin-bottom:16px; }.document-head > div { display:flex; align-items:baseline; gap:10px; }.document-head span { color:#8c8c8c; }.document-cell { display:flex; align-items:center; gap:12px; }.document-cell > span { color:#1677ff; font-size:20px; }.document-cell div { min-width:0; display:flex; flex-direction:column; }.document-cell strong, .document-cell span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.document-cell span { color:#8c8c8c; font-size:12px; }.document-progress { max-width:220px; margin-top:4px; color:#8c8c8c; font-size:11px; }.document-error { max-width:220px; margin-top:4px; color:#cf1322; font-size:11px; word-break:break-word; }.retrieval-list { max-height:48vh; overflow-y:auto; }.retrieval-row { padding:12px 0; border-bottom:1px solid #f0f0f0; }.retrieval-row header { display:flex; align-items:center; justify-content:space-between; gap:12px; }.retrieval-row p { margin:8px 0 0; color:#595959; line-height:1.6; }.faq-toolbar { display:flex; align-items:center; justify-content:space-between; gap:16px; }.toolbar-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }.toolbar-actions .ant-input-affix-wrapper { width:230px; }.toolbar-actions > .ant-input { width:140px; }.question-cell { display:flex; flex-direction:column; }.question-cell span { max-width:420px; margin-top:4px; overflow:hidden; color:#8c8c8c; font-size:12px; text-overflow:ellipsis; white-space:nowrap; }.chunk-list { max-height:65vh; overflow-y:auto; }.chunk-row { padding:14px 0; border-bottom:1px solid #f0f0f0; }.chunk-row header { display:flex; align-items:center; justify-content:space-between; gap:12px; }.chunk-row p { margin:8px 0 0; color:#595959; line-height:1.7; white-space:pre-wrap; word-break:break-word; }
@media (max-width: 1000px) { .metric-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.metric, .metric:nth-child(3n) { border-right:1px solid #f0f0f0; border-bottom:1px solid #f0f0f0; }.metric:nth-child(2n) { border-right:0; }.metric:nth-last-child(-n+2) { border-bottom:0; }.settings-layout { grid-template-columns:1fr; }.run-panel { padding-left:0; border-left:0; border-top:1px solid #f0f0f0; }.tool-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.faq-toolbar { align-items:flex-start; flex-direction:column; } }
@media (max-width: 600px) { .metric-grid { grid-template-columns:1fr 1fr; }.metric { padding:14px; }.tool-grid { grid-template-columns:1fr; } }
</style>
