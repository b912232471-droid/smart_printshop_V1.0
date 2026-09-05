<template>
  <div class="ocr-page">
    <div class="page-heading">
      <div>
        <h1>图片转文档</h1>
        <p>使用百度 OCR 识别图片文字，生成可编辑 Word 文档或 PDF 文件</p>
      </div>
    </div>

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
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ocrApi } from '@client/api'

const fileList = ref([])
const selectedFile = ref(null)
const wantDocx = ref(true)
const wantPdf = ref(true)
const loading = ref(false)
const result = reactive({ text: '', files: {}, ownerId: null, token: '' })

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
</script>

<style scoped>
.ocr-card, .result-card { margin-bottom: 18px; }
.ocr-card { display: flex; flex-direction: column; gap: 18px; }
.format-row { display: flex; align-items: center; gap: 18px; color: #595959; }
.format-row > span { font-weight: 600; color: #262626; }
.result-header { display: flex; justify-content: space-between; gap: 18px; align-items: center; margin-bottom: 14px; }
.result-header h2 { margin: 0 0 4px; font-size: 18px; }
.result-header p { margin: 0; color: #8c8c8c; }
.download-actions { display: flex; gap: 8px; }
.ant-upload-drag-icon { margin: 8px 0; color: #7c3aed; font-size: 32px; }
@media (max-width: 640px) { .format-row, .result-header { align-items: flex-start; flex-direction: column; } }
</style>
