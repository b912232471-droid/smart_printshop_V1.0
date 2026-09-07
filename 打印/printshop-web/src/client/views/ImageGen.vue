<template>
  <div class="imagegen-page">
    <div class="page-heading">
      <div>
        <h1>AI 图片生成</h1>
        <p>输入描述或使用校园模板生成图片，生成后可直接下单打印</p>
      </div>
    </div>

    <section class="surface gen-card">
      <div v-if="templates.length" class="tpl-row">
        <span class="option-label">校园模板</span>
        <div class="tpl-list">
          <button type="button" class="tpl-item" :class="{ active: !activeTemplate }" @click="clearTemplate">自由创作</button>
          <button v-for="tpl in templates" :key="tpl.templateKey" type="button" class="tpl-item" :class="{ active: activeTemplate === tpl.templateKey }" @click="applyTemplate(tpl)">{{ tpl.title }}</button>
        </div>
      </div>

      <div v-if="templateFields.length" class="tpl-fields">
        <a-input v-for="field in templateFields" :key="field.key" v-model:value="fieldValues[field.key]" :placeholder="field.placeholder ? `${field.label}，${field.placeholder}` : field.label" @input="composeTemplatePrompt" />
      </div>

      <a-textarea v-model:value="prompt" :rows="4" :maxlength="800" show-count placeholder="描述你想生成的图片，例如：大学社团招新海报，国潮风格，标题「摄影协会」，底部留报名方式区域" />

      <div class="option-row">
        <div class="option-item">
          <span class="option-label">模型</span>
          <a-select v-model:value="modelId" :options="modelOptions" style="width: 230px" />
        </div>
        <div class="option-item">
          <span class="option-label">尺寸</span>
          <a-select v-model:value="size" :options="sizeOptions" style="width: 170px" />
        </div>
        <a-button v-if="polishAvailable" :loading="polishing" @click="polishPrompt">AI 润色</a-button>
      </div>

      <div class="action-row">
        <a-button type="primary" size="large" :loading="generating" :disabled="!prompt.trim()" @click="generate">
          {{ generating ? `生成中 ${elapsed} 秒…` : '生成图片' }}
        </a-button>
        <span v-if="quotaRemaining !== null" class="quota-hint">今日剩余 {{ quotaRemaining }} 张</span>
      </div>
      <a-alert v-if="generating" class="gen-tip" type="info" show-icon message="AI 生成通常需要 10-60 秒，请勿关闭页面" />
    </section>

    <section v-if="result" class="surface result-card">
      <div class="result-layout">
        <img class="result-img" :src="result.previewUrl" alt="生成结果" />
        <div class="result-meta">
          <h2>生成结果</h2>
          <p>模型：{{ result.model }}</p>
          <p>尺寸：{{ result.size }}</p>
          <p v-if="result.polished">已使用 AI 润色后的描述</p>
          <p v-if="result.costAmount != null">本次成本：¥{{ Number(result.costAmount).toFixed(2) }}</p>
          <div class="result-actions">
            <a-button @click="downloadResult">下载图片</a-button>
            <a-button type="primary" @click="openPrintModalFromResult">去打印</a-button>
          </div>
        </div>
      </div>
    </section>

    <section class="surface history-card">
      <div class="history-header">
        <h2>历史生成</h2>
        <a-button size="small" @click="loadRecords">刷新</a-button>
      </div>
      <a-table :data-source="records" :columns="recordColumns" row-key="id" size="small" :pagination="recordPagination" :loading="recordsLoading" @change="onRecordTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'order'">
            <a-tag v-if="record.orderId" color="green">已转订单</a-tag>
            <a-tag v-else>未打印</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button type="link" size="small" @click="previewRecord(record)">预览</a-button>
              <a-button type="link" size="small" @click="downloadRecord(record)">下载</a-button>
              <a-button type="link" size="small" @click="printRecord(record)">去打印</a-button>
              <a-popconfirm title="确定删除这条生成记录？" @confirm="removeRecord(record)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </section>

    <a-modal v-model:open="printModalOpen" title="选择打印服务" ok-text="去预约" cancel-text="取消" :ok-button-props="{ disabled: !printServiceId }" :confirm-loading="printPreparing" @ok="goBooking">
      <p class="modal-tip">选择用于打印的服务项目，预约页会自动带入这张生成图。</p>
      <a-select v-model:value="printServiceId" style="width: 100%" :options="printServiceOptions" placeholder="请选择服务" />
    </a-modal>

    <a-modal v-model:open="previewOpen" :title="previewTitle" :footer="null" width="640px">
      <div class="preview-wrap">
        <a-spin v-if="previewLoading" />
        <img v-else-if="previewUrl" :src="previewUrl" alt="预览" />
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { imageGenApi, serviceApi } from '@client/api'
import { useImageGenDraftStore } from '@client/stores/imageGenDraft'

const router = useRouter()
const draftStore = useImageGenDraftStore()

const models = ref([])
const polishAvailable = ref(false)
const templates = ref([])
const activeTemplate = ref(null)
const templateFields = ref([])
const fieldValues = reactive({})
const prompt = ref('')
const modelId = ref(null)
const size = ref('')
const generating = ref(false)
const polishing = ref(false)
const elapsed = ref(0)
let timer = null
const quotaRemaining = ref(null)
const result = ref(null)

const records = ref([])
const recordsLoading = ref(false)
const recordTotal = ref(0)
const recordPage = ref(1)
const recordSize = ref(10)

const printModalOpen = ref(false)
const printServiceId = ref(null)
const printPreparing = ref(false)
const services = ref([])
const pendingPrint = ref(null)

const previewOpen = ref(false)
const previewTitle = ref('图片预览')
const previewLoading = ref(false)
const previewUrl = ref('')

const modelOptions = computed(() => models.value.map(item => ({ label: `${item.label}（¥${Number(item.pricePerImage || 0).toFixed(2)}/张）`, value: item.id })))
const sizeOptions = computed(() => {
  const current = models.value.find(item => item.id === modelId.value)
  return (current?.sizes || []).map(item => ({ label: item, value: item }))
})
const recordColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '模型', dataIndex: 'modelId', key: 'modelId', width: 170, ellipsis: true },
  { title: '尺寸', dataIndex: 'size', key: 'size', width: 110 },
  { title: '打印状态', key: 'order', width: 100 },
  { title: '操作', key: 'actions', width: 230 }
]
const recordPagination = computed(() => ({
  current: recordPage.value,
  pageSize: recordSize.value,
  total: recordTotal.value,
  showSizeChanger: false
}))
const printServiceOptions = computed(() => services.value.map(item => ({ label: `${item.name}（¥${Number(item.price || 0).toFixed(2)}）`, value: item.id })))

onMounted(async () => {
  await Promise.allSettled([loadModels(), loadTemplates(), loadRecords()])
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

async function loadModels() {
  try {
    const response = await imageGenApi.models()
    const data = response?.data || {}
    models.value = data.models || []
    polishAvailable.value = !!data.polishAvailable
    if (models.value.length && !modelId.value) {
      modelId.value = models.value[0].id
      if (sizeOptions.value.length) size.value = sizeOptions.value[0].value
    }
  } catch (error) {
    message.error('模型列表加载失败')
  }
}

async function loadTemplates() {
  try {
    const response = await imageGenApi.templates()
    templates.value = response?.data || []
  } catch (error) {
    // 模板加载失败不阻塞自由创作
  }
}

function applyTemplate(tpl) {
  activeTemplate.value = tpl.templateKey
  let fields = []
  try { fields = JSON.parse(tpl.fieldsJson || '[]') } catch (error) { fields = [] }
  templateFields.value = fields
  Object.keys(fieldValues).forEach(key => { delete fieldValues[key] })
  prompt.value = tpl.promptTemplate || ''
  if (tpl.recommendedModel && models.value.some(item => item.id === tpl.recommendedModel)) modelId.value = tpl.recommendedModel
  if (tpl.recommendedSize) size.value = tpl.recommendedSize
}

function clearTemplate() {
  activeTemplate.value = null
  templateFields.value = []
  Object.keys(fieldValues).forEach(key => { delete fieldValues[key] })
}

function composeTemplatePrompt() {
  const tpl = templates.value.find(item => item.templateKey === activeTemplate.value)
  if (!tpl) return
  let text = tpl.promptTemplate || ''
  templateFields.value.forEach(field => {
    const value = (fieldValues[field.key] || '').trim()
    text = text.split(`{${field.key}}`).join(value || `{${field.key}}`)
  })
  prompt.value = text
}

async function polishPrompt() {
  if (!prompt.value.trim()) return message.warning('请先输入图片描述')
  polishing.value = true
  try {
    const response = await imageGenApi.polish({ prompt: prompt.value })
    const polished = response?.data?.prompt
    if (polished) {
      prompt.value = polished
      message.success('润色完成，可修改后生成')
    }
  } catch (error) {
    polishAvailable.value = false
  } finally {
    polishing.value = false
  }
}

function startTimer() {
  elapsed.value = 0
  timer = setInterval(() => { elapsed.value += 1 }, 1000)
}

function stopTimer() {
  if (timer) { clearInterval(timer); timer = null }
}

async function generate() {
  if (!prompt.value.trim()) return message.warning('请输入图片描述')
  generating.value = true
  startTimer()
  try {
    const response = await imageGenApi.generate({
      model: modelId.value,
      prompt: prompt.value,
      size: size.value,
      templateKey: activeTemplate.value,
      polish: false
    })
    const data = response?.data
    if (!data || !data.previewUrl) throw new Error('生成失败')
    result.value = data
    quotaRemaining.value = typeof data.quotaRemaining === 'number' ? data.quotaRemaining : null
    message.success('生成完成')
    loadRecords()
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '生成失败，请稍后重试')
  } finally {
    stopTimer()
    generating.value = false
  }
}

function blobFromDataUrl(dataUrl) {
  return fetch(dataUrl).then(response => response.blob())
}

async function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadResult() {
  try {
    const blob = await blobFromDataUrl(result.value.previewUrl)
    await downloadBlob(blob, `ai-image-${result.value.id || Date.now()}.png`)
  } catch (error) {
    message.error('下载失败')
  }
}

function ensureServices() {
  if (services.value.length) return Promise.resolve()
  return serviceApi.getAll().then(list => { services.value = list || [] })
}

function openPrintModalFromResult() {
  pendingPrint.value = { genId: result.value.id, ownerId: null, token: result.value.token, fromResult: true }
  preparePrintModal()
}

function printRecord(record) {
  pendingPrint.value = { genId: record.id, ownerId: record.accountId, token: record.token, fromResult: false }
  preparePrintModal()
}

async function preparePrintModal() {
  printModalOpen.value = true
  try {
    await ensureServices()
    if (!printServiceId.value && services.value.length) {
      const photoService = services.value.find(item => (item.name || '').includes('照片'))
      printServiceId.value = photoService ? photoService.id : services.value[0].id
    }
  } catch (error) {
    message.error('服务列表加载失败')
  }
}

async function goBooking() {
  if (!printServiceId.value || !pendingPrint.value) return
  printPreparing.value = true
  try {
    let blob
    if (pendingPrint.value.fromResult && result.value) {
      blob = await blobFromDataUrl(result.value.previewUrl)
    } else {
      blob = await imageGenApi.download(pendingPrint.value.ownerId, pendingPrint.value.token)
    }
    draftStore.set(blob, `ai-image-${pendingPrint.value.genId}.png`, pendingPrint.value.genId)
    printModalOpen.value = false
    router.push(`/client/booking/${printServiceId.value}`)
  } catch (error) {
    message.error('图片准备失败，请重试')
  } finally {
    printPreparing.value = false
  }
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const response = await imageGenApi.records(recordPage.value, recordSize.value)
    const data = response?.data || {}
    records.value = data.items || []
    recordTotal.value = Number(data.total || 0)
  } catch (error) {
    // 历史列表加载失败不阻塞创作
  } finally {
    recordsLoading.value = false
  }
}

function onRecordTableChange(pagination) {
  recordPage.value = pagination.current
  recordSize.value = pagination.pageSize
  loadRecords()
}

async function previewRecord(record) {
  previewOpen.value = true
  previewTitle.value = `图片预览 · ${record.modelId}`
  previewLoading.value = true
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  try {
    const blob = await imageGenApi.download(record.accountId, record.token)
    previewUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    message.error('预览加载失败')
    previewOpen.value = false
  } finally {
    previewLoading.value = false
  }
}

async function downloadRecord(record) {
  try {
    const blob = await imageGenApi.download(record.accountId, record.token)
    await downloadBlob(blob, `ai-image-${record.id}.png`)
  } catch (error) {
    message.error('下载失败')
  }
}

async function removeRecord(record) {
  try {
    await imageGenApi.deleteRecord(record.id)
    message.success('已删除')
    loadRecords()
  } catch (error) {
    message.error(error?.response?.data?.message || '删除失败')
  }
}
</script>

<style scoped>
.gen-card { display: flex; flex-direction: column; gap: 16px; margin-bottom: 18px; }
.tpl-row { display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.tpl-list { display: flex; flex-wrap: wrap; gap: 8px; }
.tpl-item { border: 1px solid #e5e7eb; border-radius: 16px; background: #fff; padding: 6px 16px; cursor: pointer; font-size: 13px; color: #4b5563; transition: all .2s; }
.tpl-item:hover { border-color: #7c3aed; color: #7c3aed; }
.tpl-item.active { border-color: #7c3aed; color: #7c3aed; background: #f4eeff; }
.tpl-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; }
.option-row { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.option-item { display: flex; align-items: center; gap: 8px; }
.option-label { font-weight: 600; color: #262626; font-size: 13px; }
.action-row { display: flex; align-items: center; gap: 16px; }
.quota-hint { color: #8c8c8c; font-size: 13px; }
.gen-tip { margin-top: 2px; }
.result-card { margin-bottom: 18px; }
.result-layout { display: flex; gap: 20px; flex-wrap: wrap; }
.result-img { width: 320px; max-width: 100%; border-radius: 12px; box-shadow: 0 6px 18px rgba(0, 0, 0, .08); }
.result-meta h2 { margin: 0 0 10px; font-size: 18px; }
.result-meta p { margin: 0 0 6px; color: #595959; font-size: 13px; }
.result-actions { display: flex; gap: 10px; margin-top: 14px; }
.history-card { margin-bottom: 18px; }
.history-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.history-header h2 { margin: 0; font-size: 16px; }
.modal-tip { color: #8c8c8c; margin-bottom: 12px; }
.preview-wrap { display: flex; justify-content: center; min-height: 120px; }
.preview-wrap img { max-width: 100%; border-radius: 8px; }
@media (max-width: 640px) {
  .result-layout { flex-direction: column; }
  .result-img { width: 100%; }
}
</style>
