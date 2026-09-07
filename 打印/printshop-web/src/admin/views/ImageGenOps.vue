<template>
  <div class="imagegen-ops">
    <div class="page-heading">
      <div>
        <h1>AI 图片生成</h1>
        <p>运营配置、生成记录与成本台账</p>
      </div>
    </div>

    <section class="surface ops-card">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="config" tab="运营配置">
          <a-spin :spinning="configLoading">
            <a-form layout="vertical" class="config-form">
              <div class="config-grid">
                <a-form-item label="功能总开关">
                  <a-switch v-model:checked="form.enabledBool" checked-children="开" un-checked-children="关" />
                </a-form-item>
                <a-form-item label="默认模型">
                  <a-select v-model:value="form.defaultModel" :options="modelOptions" placeholder="选择默认模型" />
                </a-form-item>
                <a-form-item label="每用户每日生成张数">
                  <a-input-number v-model:value="form.dailyQuotaPerUser" :min="0" :max="100" class="full-width" />
                </a-form-item>
                <a-form-item label="提示词长度上限（字）">
                  <a-input-number v-model:value="form.maxPromptLength" :min="50" :max="2000" class="full-width" />
                </a-form-item>
                <a-form-item label="单日成本上限（元，0 为不限制）">
                  <a-input-number v-model:value="form.dailyCostCap" :min="0" :max="100000" :step="10" class="full-width" />
                </a-form-item>
                <a-form-item label="校园模板开关">
                  <a-switch v-model:checked="form.templatesBool" checked-children="开" un-checked-children="关" />
                </a-form-item>
                <a-form-item label="Prompt AI 润色">
                  <a-switch v-model:checked="form.polishBool" checked-children="开" un-checked-children="关" />
                </a-form-item>
                <a-form-item label="生成图内容审核（生产建议恒开）">
                  <a-switch v-model:checked="form.moderationBool" checked-children="开" un-checked-children="关" />
                </a-form-item>
                <a-form-item label="水印脚注">
                  <a-switch v-model:checked="form.watermarkBool" checked-children="开" un-checked-children="关" />
                </a-form-item>
                <a-form-item label="水印文案（不超过 64 字）">
                  <a-input v-model:value="form.watermarkText" :maxlength="64" placeholder="如：智慧打印·校园店" />
                </a-form-item>
              </div>
              <a-button v-permission="'photo:imagegen:manage'" type="primary" :loading="configSaving" @click="saveConfig">保存配置</a-button>
              <p v-if="lastUpdatedBy" class="config-meta">最后修改人 ID：{{ lastUpdatedBy }} · {{ form.updatedAt }}</p>
            </a-form>
          </a-spin>
        </a-tab-pane>

        <a-tab-pane key="templates" tab="模板管理">
          <div class="filter-row">
            <a-button v-permission="'photo:imagegen:manage'" type="primary" @click="openTemplateModal()">新增模板</a-button>
            <a-button @click="loadTemplates">刷新</a-button>
          </div>
          <a-table :data-source="templates" :columns="templateColumns" row-key="id" size="small" :loading="templatesLoading" :pagination="false">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'category'">
                <a-tag>{{ categoryText(record.category) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-switch
                  :checked="record.status === 1"
                  checked-children="启用"
                  un-checked-children="停用"
                  :disabled="!canManageTemplate"
                  @change="value => toggleTemplate(record, value)"
                />
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-space>
                  <a-button v-permission="'photo:imagegen:manage'" type="link" size="small" @click="openTemplateModal(record)">编辑</a-button>
                  <a-popconfirm title="确定删除该模板？客户端将不再展示" @confirm="removeTemplate(record)">
                    <a-button v-permission="'photo:imagegen:manage'" type="link" danger size="small">删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="records" tab="生成记录">
          <div class="filter-row">
            <a-input-number v-model:value="recordFilters.accountId" :min="1" placeholder="用户 ID" style="width: 120px" />
            <a-select v-model:value="recordFilters.modelId" :options="modelOptions" placeholder="模型" allow-clear style="width: 190px" />
            <a-select v-model:value="recordFilters.moderationStatus" :options="moderationOptions" placeholder="审核状态" allow-clear style="width: 130px" />
            <a-range-picker v-model:value="recordRange" value-format="YYYY-MM-DD" />
            <a-button type="primary" @click="searchRecords">查询</a-button>
          </div>
          <a-table :data-source="records" :columns="recordColumns" row-key="id" size="small" :loading="recordsLoading" :pagination="recordPagination" @change="onRecordTableChange">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'moderation'">
                <a-tag :color="moderationColor(record.moderationStatus)">{{ moderationText(record.moderationStatus) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'order'">
                <a-tag v-if="record.orderId" color="green">订单 {{ record.orderId }}</a-tag>
                <a-tag v-else>未打印</a-tag>
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-space>
                  <a-button type="link" size="small" @click="previewRecord(record)">预览</a-button>
                  <a-popconfirm title="确定删除该生成记录与图片文件？" @confirm="removeRecord(record)">
                    <a-button v-permission="'photo:imagegen:manage'" type="link" danger size="small">删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="usage" tab="成本台账">
          <div class="filter-row">
            <a-range-picker v-model:value="usageRange" value-format="YYYY-MM-DD" />
            <a-button type="primary" @click="loadUsage">查询</a-button>
          </div>
          <div class="usage-summary">
            <div class="usage-item"><span>生成张数</span><strong>{{ usageTotal.totalCount }}</strong></div>
            <div class="usage-item"><span>消耗 tokens</span><strong>{{ usageTotal.totalTokens }}</strong></div>
            <div class="usage-item"><span>合计成本</span><strong>¥{{ usageTotal.totalCost }}</strong></div>
          </div>
          <a-table :data-source="usageItems" :columns="usageColumns" row-key="key" size="small" :loading="usageLoading" :pagination="false">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'cost'">¥{{ Number(record.totalCost || 0).toFixed(2) }}</template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </section>

    <a-modal v-model:open="previewOpen" title="图片预览" :footer="null" width="640px">
      <div class="preview-wrap">
        <a-spin v-if="previewLoading" />
        <img v-else-if="previewUrl" :src="previewUrl" alt="预览" />
      </div>
    </a-modal>

    <a-modal v-model:open="templateModalOpen" :title="templateForm.id ? '编辑模板' : '新增模板'" width="680px" @ok="saveTemplate" :confirm-loading="templateSaving">
      <a-form layout="vertical">
        <div class="config-grid">
          <a-form-item label="模板标识（小写字母开头，创建后不可改）">
            <a-input v-model:value="templateForm.templateKey" :disabled="!!templateForm.id" placeholder="如：poster_weekly" />
          </a-form-item>
          <a-form-item label="模板标题">
            <a-input v-model:value="templateForm.title" :maxlength="100" placeholder="如：社团招新海报" />
          </a-form-item>
          <a-form-item label="分类">
            <a-select v-model:value="templateForm.category" :options="categoryOptions" />
          </a-form-item>
          <a-form-item label="推荐模型">
            <a-select v-model:value="templateForm.recommendedModel" :options="modelOptions" placeholder="选择模型" />
          </a-form-item>
          <a-form-item label="推荐尺寸（宽x高）">
            <a-select v-model:value="templateForm.recommendedSize" :options="sizeOptions" placeholder="选择尺寸" />
          </a-form-item>
          <a-form-item label="显示顺序（0-999）">
            <a-input-number v-model:value="templateForm.sortOrder" :min="0" :max="999" class="full-width" />
          </a-form-item>
        </div>
        <a-form-item label="提示词模板（{xxx} 为占位符，与下方字段对应）">
          <a-textarea v-model:value="templateForm.promptTemplate" :rows="4" :maxlength="600" show-count />
        </a-form-item>
        <a-form-item label="填空字段定义（JSON 数组，每项含 key 与 label）">
          <a-textarea v-model:value="templateForm.fieldsJson" :rows="4" :maxlength="500" placeholder='[{"key":"title","label":"标题"}]' />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { imageGenApi } from '@/api'
import { usePermissionStore } from '@/stores/permission'

const activeTab = ref('config')
const permissionStore = usePermissionStore()
const canManageTemplate = computed(() => permissionStore.hasPerm('photo:imagegen:manage'))

const form = reactive({
  enabledBool: true,
  defaultModel: '',
  dailyQuotaPerUser: 5,
  maxPromptLength: 500,
  dailyCostCap: 50,
  templatesBool: true,
  polishBool: true,
  moderationBool: true,
  watermarkBool: false,
  watermarkText: '',
  updatedAt: ''
})
const lastUpdatedBy = ref(null)
const configLoading = ref(false)
const configSaving = ref(false)
const models = ref([])

const records = ref([])
const recordsLoading = ref(false)
const recordTotal = ref(0)
const recordPage = ref(1)
const recordSize = ref(10)
const recordFilters = reactive({ accountId: null, modelId: null, moderationStatus: null })
const recordRange = ref(null)

const previewOpen = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')

const templates = ref([])
const templatesLoading = ref(false)
const templateModalOpen = ref(false)
const templateSaving = ref(false)
const templateForm = reactive({
  id: null, templateKey: '', title: '', category: 'poster', promptTemplate: '',
  recommendedModel: undefined, recommendedSize: undefined, fieldsJson: '', sortOrder: 0
})
const templateColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '标识', dataIndex: 'templateKey', key: 'templateKey', width: 150, ellipsis: true },
  { title: '标题', dataIndex: 'title', key: 'title', width: 140, ellipsis: true },
  { title: '分类', key: 'category', width: 90 },
  { title: '推荐模型', dataIndex: 'recommendedModel', key: 'recommendedModel', width: 160, ellipsis: true },
  { title: '推荐尺寸', dataIndex: 'recommendedSize', key: 'recommendedSize', width: 100 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 70 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 120 }
]
const categoryOptions = [
  { label: '手抄报', value: 'handout' },
  { label: '海报', value: 'poster' },
  { label: '通知', value: 'notice' },
  { label: '照片', value: 'photo' }
]

const usageItems = ref([])
const usageLoading = ref(false)
const usageRange = ref(null)
const usageTotal = reactive({ totalCount: 0, totalTokens: 0, totalCost: '0.00' })

const modelOptions = computed(() => models.value.map(item => ({ label: `${item.label}（${item.id}）`, value: item.id })))
const sizeOptions = computed(() => {
  const model = models.value.find(item => item.id === templateForm.recommendedModel)
  return (model?.sizes || []).map(size => ({ label: size, value: size }))
})
const moderationOptions = [
  { label: '未审核', value: 0 },
  { label: '通过', value: 1 },
  { label: '拒绝', value: 2 }
]
const recordColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '用户', dataIndex: 'accountId', key: 'accountId', width: 80 },
  { title: '模型', dataIndex: 'modelId', key: 'modelId', width: 170, ellipsis: true },
  { title: '尺寸', dataIndex: 'size', key: 'size', width: 100 },
  { title: '成本', dataIndex: 'costAmount', key: 'costAmount', width: 90 },
  { title: '审核', key: 'moderation', width: 80 },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '转订单', key: 'order', width: 100 },
  { title: '操作', key: 'actions', width: 130 }
]
const usageColumns = [
  { title: '日期', dataIndex: 'statDate', key: 'statDate' },
  { title: '模型', dataIndex: 'modelId', key: 'modelId', ellipsis: true },
  { title: '张数', dataIndex: 'genCount', key: 'genCount', width: 90 },
  { title: 'tokens', dataIndex: 'totalTokens', key: 'totalTokens', width: 110 },
  { title: '成本', key: 'cost', width: 100 }
]
const recordPagination = computed(() => ({
  current: recordPage.value,
  pageSize: recordSize.value,
  total: recordTotal.value,
  showSizeChanger: false
}))

onMounted(async () => {
  loadConfig()
  loadModels()
  loadTemplates()
  loadRecords()
  loadUsage()
})

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const response = await imageGenApi.templatesAll()
    templates.value = response?.data || []
  } catch (error) {
    message.error('模板列表加载失败')
  } finally {
    templatesLoading.value = false
  }
}

function categoryText(category) {
  return { handout: '手抄报', poster: '海报', notice: '通知', photo: '照片' }[category] || category
}

function openTemplateModal(record) {
  templateForm.id = record?.id || null
  templateForm.templateKey = record?.templateKey || ''
  templateForm.title = record?.title || ''
  templateForm.category = record?.category || 'poster'
  templateForm.promptTemplate = record?.promptTemplate || ''
  templateForm.recommendedModel = record?.recommendedModel || undefined
  templateForm.recommendedSize = record?.recommendedSize || undefined
  templateForm.fieldsJson = record?.fieldsJson || ''
  templateForm.sortOrder = record?.sortOrder ?? 0
  templateModalOpen.value = true
}

async function saveTemplate() {
  if (!templateForm.title?.trim()) return message.warning('请填写模板标题')
  if (!templateForm.recommendedModel) return message.warning('请选择推荐模型')
  if (!templateForm.recommendedSize) return message.warning('请选择推荐尺寸')
  try {
    JSON.parse(templateForm.fieldsJson)
  } catch (error) {
    return message.error('字段定义不是合法 JSON')
  }
  templateSaving.value = true
  try {
    const payload = {
      templateKey: templateForm.templateKey?.trim(),
      title: templateForm.title.trim(),
      category: templateForm.category,
      promptTemplate: templateForm.promptTemplate,
      recommendedModel: templateForm.recommendedModel,
      recommendedSize: templateForm.recommendedSize,
      fieldsJson: templateForm.fieldsJson,
      sortOrder: templateForm.sortOrder,
      status: 1
    }
    if (templateForm.id) {
      await imageGenApi.updateTemplate(templateForm.id, payload)
    } else {
      await imageGenApi.createTemplate(payload)
    }
    message.success('模板已保存')
    templateModalOpen.value = false
    loadTemplates()
  } catch (error) {
    message.error(error?.response?.data?.message || '保存失败')
  } finally {
    templateSaving.value = false
  }
}

async function toggleTemplate(record, value) {
  try {
    await imageGenApi.updateTemplate(record.id, { ...record, status: value ? 1 : 0 })
    message.success(value ? '已启用' : '已停用')
    loadTemplates()
  } catch (error) {
    message.error(error?.response?.data?.message || '操作失败')
  }
}

async function removeTemplate(record) {
  try {
    await imageGenApi.deleteTemplate(record.id)
    message.success('模板已删除')
    loadTemplates()
  } catch (error) {
    message.error(error?.response?.data?.message || '删除失败')
  }
}

async function loadModels() {
  try {
    const response = await imageGenApi.models()
    models.value = response?.data?.models || []
  } catch (error) { /* 模型目录加载失败不阻塞配置页 */ }
}

async function loadConfig() {
  configLoading.value = true
  try {
    const response = await imageGenApi.config()
    const data = response?.data || {}
    form.enabledBool = data.enabled === 1
    form.defaultModel = data.defaultModel || ''
    form.dailyQuotaPerUser = data.dailyQuotaPerUser ?? 5
    form.maxPromptLength = data.maxPromptLength ?? 500
    form.dailyCostCap = Number(data.dailyCostCap ?? 50)
    form.templatesBool = data.templatesEnabled === 1
    form.polishBool = data.polishEnabled === 1
    form.moderationBool = data.moderationEnabled === 1
    form.watermarkBool = data.watermarkEnabled === 1
    form.watermarkText = data.watermarkText || ''
    form.updatedAt = data.updatedAt || ''
    lastUpdatedBy.value = data.updatedBy || null
  } catch (error) {
    message.error('配置加载失败')
  } finally {
    configLoading.value = false
  }
}

async function saveConfig() {
  configSaving.value = true
  try {
    await imageGenApi.updateConfig({
      enabled: form.enabledBool ? 1 : 0,
      defaultModel: form.defaultModel,
      dailyQuotaPerUser: form.dailyQuotaPerUser,
      maxPromptLength: form.maxPromptLength,
      polishEnabled: form.polishBool ? 1 : 0,
      moderationEnabled: form.moderationBool ? 1 : 0,
      templatesEnabled: form.templatesBool ? 1 : 0,
      watermarkEnabled: form.watermarkBool ? 1 : 0,
      watermarkText: form.watermarkText || null,
      dailyCostCap: form.dailyCostCap
    })
    message.success('配置已保存，60 秒内生效')
    loadConfig()
  } catch (error) {
    message.error(error?.response?.data?.message || '保存失败')
  } finally {
    configSaving.value = false
  }
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const params = { page: recordPage.value, size: recordSize.value }
    if (recordFilters.accountId) params.accountId = recordFilters.accountId
    if (recordFilters.modelId) params.modelId = recordFilters.modelId
    if (recordFilters.moderationStatus !== null && recordFilters.moderationStatus !== undefined) params.moderationStatus = recordFilters.moderationStatus
    if (recordRange.value?.[0]) params.start = recordRange.value[0]
    if (recordRange.value?.[1]) params.end = recordRange.value[1]
    const response = await imageGenApi.records(params)
    const data = response?.data || {}
    records.value = data.items || []
    recordTotal.value = Number(data.total || 0)
  } catch (error) {
    message.error('生成记录加载失败')
  } finally {
    recordsLoading.value = false
  }
}

function searchRecords() {
  recordPage.value = 1
  loadRecords()
}

function onRecordTableChange(pagination) {
  recordPage.value = pagination.current
  recordSize.value = pagination.pageSize
  loadRecords()
}

function moderationText(status) {
  return { 0: '未审核', 1: '通过', 2: '拒绝' }[status] || '未知'
}

function moderationColor(status) {
  return { 0: 'default', 1: 'green', 2: 'red' }[status] || 'default'
}

async function previewRecord(record) {
  previewOpen.value = true
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

async function removeRecord(record) {
  try {
    await imageGenApi.deleteRecord(record.id)
    message.success('已删除')
    loadRecords()
  } catch (error) {
    message.error(error?.response?.data?.message || '删除失败')
  }
}

async function loadUsage() {
  usageLoading.value = true
  try {
    const params = {}
    if (usageRange.value?.[0]) params.start = usageRange.value[0]
    if (usageRange.value?.[1]) params.end = usageRange.value[1]
    const response = await imageGenApi.usage(params)
    const data = response?.data || {}
    usageItems.value = (data.items || []).map((item, index) => ({ ...item, key: `${item.statDate}-${item.modelId}-${index}` }))
    usageTotal.totalCount = Number(data.totalCount || 0)
    usageTotal.totalTokens = Number(data.totalTokens || 0)
    usageTotal.totalCost = Number(data.totalCost || 0).toFixed(2)
  } catch (error) {
    message.error('成本台账加载失败')
  } finally {
    usageLoading.value = false
  }
}
</script>

<style scoped>
.ops-card { padding: 8px 20px 20px; }
.config-form { max-width: 860px; margin-top: 12px; }
.config-grid { display: grid; grid-template-columns: repeat(2, 1fr); column-gap: 28px; row-gap: 2px; }
.full-width { width: 100%; }
.config-meta { margin-top: 12px; color: #a3a3a3; font-size: 12px; }
.filter-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.usage-summary { display: flex; gap: 32px; margin: 4px 0 16px; }
.usage-item { display: flex; flex-direction: column; gap: 4px; }
.usage-item span { color: #8c8c8c; font-size: 12px; }
.usage-item strong { font-size: 20px; color: #262626; }
.preview-wrap { display: flex; justify-content: center; min-height: 120px; }
.preview-wrap img { max-width: 100%; border-radius: 8px; }
@media (max-width: 760px) {
  .config-grid { grid-template-columns: 1fr; }
}
</style>
