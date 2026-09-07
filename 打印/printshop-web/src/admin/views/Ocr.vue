<template>
  <div class="ocr-page">
    <div class="page-heading">
      <div>
        <h1>OCR 图片转文档</h1>
        <p>转换工具与运营管理（开关、配额、使用记录）</p>
      </div>
    </div>

    <section class="surface ops-card">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="convert" tab="图片转文档">
          <section class="surface status-card" v-if="serviceStatus">
            <strong>服务状态：</strong><a-tag :color="serviceStatus.configured ? 'green' : 'orange'">{{ serviceStatus.configured ? '已配置' : '待配置密钥' }}</a-tag>
            <a-tag v-if="serviceStatus.enabled === false" color="red">功能已停用</a-tag>
            <span>供应商：{{ serviceStatus.provider }} · 支持：{{ serviceStatus.formats.join('、') }} · 单图上限：{{ serviceStatus.maxFileSizeMb }}MB</span>
          </section>

          <section class="surface ocr-card">
            <a-alert type="info" show-icon message="建议上传清晰、正向、文字占画面较大的 JPG、PNG 或 BMP 图片，单张不超过 4MB。" />
            <a-upload-dragger
              v-model:file-list="fileList"
              accept=".jpg,.jpeg,.png,.bmp"
              :max-count="1"
              :before-upload="beforeUpload"
              @remove="clearFile"
            >
              <p class="ant-upload-drag-icon">⇧</p>
              <p class="ant-upload-text">点击或拖拽图片到这里</p>
              <p class="ant-upload-hint">支持 JPG、PNG、BMP；图片仅用于本次文字识别</p>
            </a-upload-dragger>
            <div class="format-row">
              <span>输出格式</span>
              <a-checkbox v-model:checked="wantDocx">Word（.docx）</a-checkbox>
              <a-checkbox v-model:checked="wantPdf">PDF</a-checkbox>
            </div>
            <a-button type="primary" size="large" :loading="loading" :disabled="!selectedFile || (!wantDocx && !wantPdf)" @click="convert">
              开始识别并生成文档
            </a-button>
          </section>

          <section v-if="result.text" class="surface result-card">
            <div class="result-header">
              <div>
                <h2>识别结果</h2>
                <p>请先检查文字内容，再下载文档。</p>
              </div>
              <div class="download-actions">
                <a-button v-if="result.files?.docx" @click="download('docx')">下载 Word</a-button>
                <a-button v-if="result.files?.pdf" type="primary" @click="download('pdf')">下载 PDF</a-button>
              </div>
            </div>
            <a-textarea v-model:value="result.text" :rows="14" />
          </section>
        </a-tab-pane>

        <a-tab-pane v-if="canManage || canQuery" key="ops" tab="运营管理">
          <a-spin :spinning="opsLoading">
            <div v-if="canManage" class="config-block">
              <h3 class="block-title">运营配置</h3>
              <a-form layout="inline" class="config-inline">
                <a-form-item label="功能总开关">
                  <a-switch v-model:checked="form.enabledBool" checked-children="开" un-checked-children="关" />
                </a-form-item>
                <a-form-item label="每用户每日识别次数（0-200）">
                  <a-input-number v-model:value="form.dailyQuotaPerUser" :min="0" :max="200" />
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" :loading="configSaving" @click="saveConfig">保存配置</a-button>
                </a-form-item>
              </a-form>
              <p class="config-meta">
                服务密钥：{{ configData?.configured ? '已配置' : '待配置 BAIDU_OCR_API_KEY/SECRET_KEY' }}
                · 单图上限 {{ configData?.maxFileSizeMb }}MB
                <template v-if="lastUpdatedBy">· 最后修改人 ID：{{ lastUpdatedBy }} · {{ configData?.updatedAt }}</template>
              </p>
            </div>

            <div v-if="canQuery" class="config-block">
              <h3 class="block-title">使用统计（近 30 天）</h3>
              <div class="usage-summary">
                <div class="usage-item"><span>转换次数</span><strong>{{ usage.totalCount }}</strong></div>
                <div class="usage-item"><span>成功</span><strong>{{ usage.successCount }}</strong></div>
                <div class="usage-item"><span>失败</span><strong>{{ usage.failedCount }}</strong></div>
                <div class="usage-item"><span>识别字符</span><strong>{{ usage.totalCharCount }}</strong></div>
                <div class="usage-item"><span>平均耗时</span><strong>{{ usage.avgDurationMs }}ms</strong></div>
              </div>
            </div>

            <div v-if="canQuery" class="config-block">
              <h3 class="block-title">使用记录</h3>
              <div class="filter-row">
                <a-input-number v-model:value="recordFilters.accountId" :min="1" placeholder="用户 ID" style="width: 120px" />
                <a-select v-model:value="recordFilters.status" :options="statusOptions" placeholder="状态" allow-clear style="width: 110px" />
                <a-range-picker v-model:value="recordRange" value-format="YYYY-MM-DD" />
                <a-button type="primary" @click="searchRecords">查询</a-button>
              </div>
              <a-table :data-source="records" :columns="recordColumns" row-key="id" size="small" :loading="recordsLoading" :pagination="recordPagination" @change="onRecordTableChange">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'status'">
                    <a-tag :color="record.status === 1 ? 'green' : 'red'">{{ record.status === 1 ? '成功' : '失败' }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'failure'">
                    <span :title="record.failureReason">{{ record.failureReason || '-' }}</span>
                  </template>
                </template>
              </a-table>
            </div>
          </a-spin>
        </a-tab-pane>
      </a-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ocrApi } from '@/api'
import { usePermissionStore } from '@/stores/permission'

const activeTab = ref('convert')
const permissionStore = usePermissionStore()
const canManage = computed(() => permissionStore.hasPerm('print:ocr:manage'))
const canQuery = computed(() => permissionStore.hasPerm('print:ocr:query'))

const fileList = ref([])
const selectedFile = ref(null)
const wantDocx = ref(true)
const wantPdf = ref(true)
const loading = ref(false)
const result = reactive({ text: '', files: {}, ownerId: null, token: '' })
const serviceStatus = ref(null)

const form = reactive({ enabledBool: true, dailyQuotaPerUser: 20 })
const configData = ref(null)
const lastUpdatedBy = ref(null)
const configSaving = ref(false)
const opsLoading = ref(false)

const records = ref([])
const recordsLoading = ref(false)
const recordTotal = ref(0)
const recordPage = ref(1)
const recordSize = ref(10)
const recordFilters = reactive({ accountId: null, status: null })
const recordRange = ref(null)
const usage = reactive({ totalCount: 0, successCount: 0, failedCount: 0, totalCharCount: 0, avgDurationMs: 0 })

const statusOptions = [
  { label: '成功', value: 1 },
  { label: '失败', value: 2 }
]
const recordColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '用户', key: 'username', width: 180, ellipsis: true },
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', width: 160, ellipsis: true },
  { title: '大小', key: 'size', width: 90 },
  { title: '字符数', dataIndex: 'charCount', key: 'charCount', width: 90 },
  { title: '耗时', key: 'duration', width: 90 },
  { title: '状态', key: 'status', width: 80 },
  { title: '失败原因', key: 'failure', ellipsis: true },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 }
]
const recordPagination = computed(() => ({
  current: recordPage.value,
  pageSize: recordSize.value,
  total: recordTotal.value,
  showSizeChanger: false
}))

onMounted(async () => {
  ocrApi.status().then(response => { if (response?.data) serviceStatus.value = response.data }).catch(() => {})
  await permissionStore.load()
  if (canManage.value || canQuery.value) {
    loadOps()
  }
})

function beforeUpload(file) {
  selectedFile.value = file
  fileList.value = [file]
  return false
}

function clearFile() {
  selectedFile.value = null
  fileList.value = []
}

async function convert() {
  if (!selectedFile.value) return message.warning('请先选择图片')
  const format = wantDocx.value && wantPdf.value ? 'both' : wantDocx.value ? 'docx' : 'pdf'
  loading.value = true
  try {
    const response = await ocrApi.convert(selectedFile.value, format)
    if (!response || ![0, 200].includes(response.code) || !response.data) throw new Error(response?.message || 'OCR 识别失败')
    Object.assign(result, response.data)
    message.success('识别完成，请检查结果后下载')
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || 'OCR 识别失败')
  } finally {
    loading.value = false
  }
}

async function download(format) {
  try {
    const blob = await ocrApi.download(result.ownerId, result.token, format)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${selectedFile.value?.name?.replace(/\.[^.]+$/, '') || 'ocr-result'}.${format}`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    message.error(error?.message || '下载失败')
  }
}

async function loadOps() {
  opsLoading.value = true
  try {
    if (canManage.value) await loadConfig()
    if (canQuery.value) {
      await Promise.all([loadUsage(), loadRecords()])
    }
  } finally {
    opsLoading.value = false
  }
}

async function loadConfig() {
  try {
    const response = await ocrApi.config()
    const data = response?.data || {}
    configData.value = data
    form.enabledBool = data.enabled === 1
    form.dailyQuotaPerUser = data.dailyQuotaPerUser ?? 20
    lastUpdatedBy.value = data.updatedBy || null
  } catch (error) {
    message.error('OCR 配置加载失败')
  }
}

async function saveConfig() {
  configSaving.value = true
  try {
    const response = await ocrApi.updateConfig({
      enabled: form.enabledBool ? 1 : 0,
      dailyQuotaPerUser: form.dailyQuotaPerUser
    })
    configData.value = response?.data || configData.value
    message.success('配置已保存，60 秒内生效')
  } catch (error) {
    message.error(error?.response?.data?.message || '保存失败')
  } finally {
    configSaving.value = false
  }
}

async function loadUsage() {
  try {
    const response = await ocrApi.usage()
    const data = response?.data || {}
    Object.assign(usage, {
      totalCount: Number(data.totalCount || 0),
      successCount: Number(data.successCount || 0),
      failedCount: Number(data.failedCount || 0),
      totalCharCount: Number(data.totalCharCount || 0),
      avgDurationMs: Number(data.avgDurationMs || 0)
    })
  } catch (error) {
    message.error('使用统计加载失败')
  }
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const params = { page: recordPage.value, size: recordSize.value }
    if (recordFilters.accountId) params.accountId = recordFilters.accountId
    if (recordFilters.status !== null && recordFilters.status !== undefined) params.status = recordFilters.status
    if (recordRange.value?.[0]) params.start = recordRange.value[0]
    if (recordRange.value?.[1]) params.end = recordRange.value[1]
    const response = await ocrApi.records(params)
    const data = response?.data || {}
    records.value = data.items || []
    recordTotal.value = Number(data.total || 0)
  } catch (error) {
    message.error('使用记录加载失败')
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
</script>

<style scoped>
.ops-card { padding: 8px 20px 20px; }
.status-card { display: flex; gap: 10px; align-items: center; margin-bottom: 18px; color: #595959; }
.ocr-card, .result-card { margin-bottom: 18px; }
.ocr-card { display: flex; flex-direction: column; gap: 18px; }
.format-row { display: flex; align-items: center; gap: 18px; color: #595959; }
.format-row > span { font-weight: 600; color: #262626; }
.result-header { display: flex; justify-content: space-between; gap: 18px; align-items: center; margin-bottom: 14px; }
.result-header h2 { margin: 0 0 4px; font-size: 18px; }
.result-header p { margin: 0; color: #8c8c8c; }
.download-actions { display: flex; gap: 8px; }
.ant-upload-drag-icon { margin: 8px 0; color: #7c3aed; font-size: 32px; }
.config-block { margin-bottom: 22px; }
.block-title { margin: 0 0 12px; font-size: 15px; }
.config-inline { display: flex; flex-wrap: wrap; gap: 8px 24px; }
.config-meta { margin: 10px 0 0; color: #a3a3a3; font-size: 12px; }
.usage-summary { display: flex; gap: 32px; margin: 4px 0 8px; flex-wrap: wrap; }
.usage-item { display: flex; flex-direction: column; gap: 4px; }
.usage-item span { color: #8c8c8c; font-size: 12px; }
.usage-item strong { font-size: 20px; color: #262626; }
.filter-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
@media (max-width: 640px) { .format-row, .result-header { align-items: flex-start; flex-direction: column; } }
</style>
