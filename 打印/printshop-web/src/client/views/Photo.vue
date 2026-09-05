<template>
  <div>
    <div class="page-heading">
      <div>
        <h1>AI 证件照</h1>
        <p>智能人像抠图与证件照生成，支持自定义底色与增强选项</p>
      </div>
    </div>

    <div class="photo-grid">
      <aside class="surface photo-side">
        <div class="mode-tabs">
          <button v-for="item in modeTabs" :key="item.value" type="button" :class="['mode-tab', { active: form.mode === item.value }]" @click="switchMode(item.value)">
            <component :is="item.icon" /><span>{{ item.label }}</span>
          </button>
        </div>

        <a-form layout="vertical" class="photo-form">
          <a-form-item v-if="form.mode === 'generate'" label="证件照尺寸">
            <a-select v-model:value="form.size" :options="sizeOptions" />
          </a-form-item>

          <a-form-item label="背景颜色">
            <div class="color-row">
              <button v-for="item in colorOptions" :key="item.value" type="button" class="color-dot" :class="{ active: form.bgColor === item.value }" :style="{ background: item.value }" :title="item.name" @click="form.bgColor = item.value" />
              <a-input v-model:value="form.bgColor" class="color-input" placeholder="#FFFFFF" />
            </div>
          </a-form-item>

          <a-form-item v-if="form.mode === 'background'" label="输出格式">
            <a-radio-group v-model:value="form.outputFormat" button-style="solid">
              <a-radio-button value="png">PNG</a-radio-button>
              <a-radio-button value="jpg">JPG</a-radio-button>
            </a-radio-group>
          </a-form-item>

          <a-form-item v-if="form.mode === 'background'" label="增强级别">
            <a-radio-group v-model:value="form.enhanceLevel" button-style="solid">
              <a-radio-button value="light">轻微</a-radio-button>
              <a-radio-button value="normal">标准</a-radio-button>
              <a-radio-button value="strong">增强</a-radio-button>
            </a-radio-group>
          </a-form-item>

          <a-form-item label="图像增强">
            <a-switch v-model:checked="form.enhance" />
          </a-form-item>

          <template v-if="form.mode === 'background'">
            <a-form-item label="渐变背景"><a-switch v-model:checked="form.gradientBg" /></a-form-item>
            <a-form-item label="边缘平滑"><a-switch v-model:checked="form.smoothEdge" /></a-form-item>
          </template>
        </a-form>

        <div class="upload-row">
          <input ref="fileInput" type="file" accept="image/*" hidden @change="onFileChange" />
          <a-button block @click="pickFile"><UploadOutlined /> 选择照片</a-button>
        </div>

        <div v-if="form.file" class="file-info">
          <FileImageOutlined /><span class="file-name">{{ form.file.name }}</span>
          <button type="button" class="file-clear" @click="clearFile"><CloseOutlined /></button>
        </div>

        <a-button type="primary" block size="large" :loading="loading" :disabled="!form.file" @click="handleProcess">
          <ThunderboltOutlined />{{ form.mode === 'generate' ? '生成证件照' : '换底色' }}
        </a-button>
      </aside>

      <section class="surface photo-result">
        <div class="result-head">
          <h2>处理结果</h2>
          <div v-if="result.url" class="result-actions">
            <a-button size="small" @click="downloadResult"><DownloadOutlined /> 下载</a-button>
          </div>
        </div>

        <div class="result-body">
          <div v-if="loading" class="result-loading"><a-spin tip="处理中..." /></div>
          <div v-else-if="errorMsg" class="result-error"><WarningOutlined /><p>{{ errorMsg }}</p></div>
          <div v-else-if="result.url" class="result-image">
            <img :src="result.url" alt="处理结果" />
            <div class="result-meta">
              <span v-if="result.width">尺寸 {{ result.width }}×{{ result.height }}</span>
              <span v-if="result.format">格式 {{ result.format.toUpperCase() }}</span>
              <span v-if="result.size">规格 {{ result.size }}</span>
            </div>
          </div>
          <div v-else class="result-empty"><PictureOutlined /><p>选择照片后点击处理按钮，结果会显示在这里</p></div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CloseOutlined, DownloadOutlined, FileImageOutlined, PictureOutlined,
  ThunderboltOutlined, UploadOutlined, WarningOutlined
} from '@ant-design/icons-vue'
import { photoApi } from '@client/api'

const modeTabs = [
  { label: '生成证件照', value: 'generate' },
  { label: '换底色', value: 'background' }
]
const sizeOptions = [
  { value: '1寸', label: '1寸' },
  { value: '小1寸', label: '小1寸' },
  { value: '大1寸', label: '大1寸' },
  { value: '2寸', label: '2寸' },
  { value: '小2寸', label: '小2寸' },
  { value: '驾照', label: '驾照' },
  { value: '签证(美国)', label: '签证(美国)' },
  { value: '签证(日本)', label: '签证(日本)' }
]
const colorOptions = [
  { name: '白', value: '#FFFFFF' },
  { name: '蓝', value: '#0000FF' },
  { name: '红', value: '#FF0000' },
  { name: '浅蓝', value: '#87CEEB' },
  { name: '粉', value: '#FFC0CB' },
  { name: '灰', value: '#F3F4F6' }
]

const fileInput = ref(null)
const loading = ref(false)
const errorMsg = ref('')
const result = reactive({ url: '', width: 0, height: 0, format: '', size: '' })
const form = reactive({
  mode: 'generate',
  size: '1寸',
  bgColor: '#FFFFFF',
  outputFormat: 'png',
  enhanceLevel: 'normal',
  enhance: true,
  gradientBg: true,
  smoothEdge: true,
  file: null
})

function switchMode(mode) {
  if (!mode || form.mode === mode) return
  form.mode = mode
  resetResult()
}

function resetResult() {
  result.url = ''
  result.width = 0
  result.height = 0
  result.format = ''
  result.size = ''
  errorMsg.value = ''
}

function pickFile() {
  fileInput.value?.click()
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    return
  }
  form.file = file
  resetResult()
  event.target.value = ''
}

function clearFile() {
  form.file = null
  resetResult()
}

function toDataUrl(base64Value, format) {
  const clean = String(base64Value || '').replace(/^data:image\/\w+;base64,/, '')
  const ext = format === 'jpg' || format === 'jpeg' ? 'jpeg' : 'png'
  return `data:image/${ext};base64,${clean}`
}

async function handleProcess() {
  if (!form.file) {
    message.warning('请先选择照片')
    return
  }
  loading.value = true
  resetResult()
  try {
    const options = {
      size: form.size,
      bgColor: form.bgColor,
      outputFormat: form.outputFormat,
      enhance: form.enhance,
      enhanceLevel: form.enhanceLevel,
      gradientBg: form.gradientBg,
      smoothEdge: form.smoothEdge
    }
    const res = form.mode === 'generate'
      ? await photoApi.generateIdPhoto(form.file, options)
      : await photoApi.changeBackground(form.file, options)
    const data = (res && res.data) || {}
    if (!data.image) throw new Error('服务未返回图片结果')
    result.url = toDataUrl(data.image, data.format || form.outputFormat)
    result.width = data.width || 0
    result.height = data.height || 0
    result.format = data.format || form.outputFormat
    result.size = data.size || form.size
    message.success('处理完成')
  } catch (err) {
    errorMsg.value = err?.response?.data?.detail || err?.message || '图片处理失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function downloadResult() {
  if (!result.url) return
  const link = document.createElement('a')
  link.href = result.url
  link.download = `photo-${Date.now()}.${result.format || 'png'}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
</script>

<style scoped>
.photo-grid { display: grid; grid-template-columns: 360px minmax(0, 1fr); gap: 14px; align-items: start; }
.photo-side { padding: 22px 20px; display: flex; flex-direction: column; gap: 16px; }
.mode-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.mode-tab { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 10px; border: 1px solid #d9d9d9; border-radius: 8px; background: #fff; color: #595959; cursor: pointer; }
.mode-tab.active { border-color: #1677ff; color: #1677ff; background: #e6f4ff; }
.photo-form .color-row { display: flex; align-items: center; gap: 8px; }
.color-dot { width: 28px; height: 28px; border: 1px solid rgba(0,0,0,.15); border-radius: 50%; cursor: pointer; padding: 0; }
.color-dot.active { box-shadow: 0 0 0 2px #1677ff; }
.color-input { flex: 1; }
.upload-row { display: flex; }
.file-info { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px dashed #d9d9d9; border-radius: 6px; color: #595959; font-size: 13px; }
.file-info .file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-clear { border: 0; background: transparent; color: #8c8c8c; cursor: pointer; padding: 0; }
.photo-result { padding: 20px; min-height: 360px; }
.result-head { display: flex; align-items: center; justify-content: space-between; }
.result-head h2 { margin: 0; font-size: 16px; color: #262626; }
.result-body { margin-top: 14px; display: grid; place-items: center; min-height: 280px; }
.result-loading, .result-error, .result-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; color: #8c8c8c; font-size: 13px; }
.result-error { color: #cf1322; }
.result-image { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.result-image img { max-width: 100%; max-height: 360px; border: 1px solid #f0f0f0; border-radius: 8px; }
.result-meta { display: flex; gap: 12px; color: #8c8c8c; font-size: 12px; }
.dark .mode-tab { background: transparent; color: rgba(255,255,255,.65); border-color: rgba(255,255,255,.15); }
.dark .mode-tab.active { background: rgba(22,119,255,.14); color: #69b1ff; border-color: #1677ff; }
.dark .result-head h2 { color: rgba(255,255,255,.88); }
@media (max-width: 900px) { .photo-grid { grid-template-columns: 1fr; } }
</style>
