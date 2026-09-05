<template>
  <div>
    <div class="page-heading">
      <div>
        <h1>教务课表查询</h1>
        <p>绑定教务账号后同步课程表，支持按学期筛选</p>
      </div>
    </div>

    <div v-if="loadingStatus" class="loading-block"><a-spin /></div>

    <section v-else-if="!bound" class="surface schedule-card">
      <div class="card-head"><CalendarOutlined /><h2>绑定教务账号</h2></div>
      <p class="card-tip">教务密码采用 AES-GCM 加密存储，仅用于同步课程表，不会以明文形式对外暴露</p>
      <a-form layout="vertical" class="bind-form">
        <a-form-item label="学号" required>
          <a-input v-model:value="bindForm.studentId" placeholder="请输入学号" />
        </a-form-item>
        <a-form-item label="教务系统密码" required>
          <a-input-password v-model:value="bindForm.jwPassword" placeholder="至少 6 位" />
        </a-form-item>
        <a-button type="primary" :loading="binding" @click="handleBind">保存绑定</a-button>
      </a-form>
      <div v-if="errorMsg" class="error-text">{{ errorMsg }}</div>
    </section>

    <section v-else class="surface schedule-card">
      <div class="card-head">
        <div><CalendarOutlined /><h2>当前绑定</h2></div>
        <a-button type="link" @click="toggleBindForm">{{ editingBind ? '取消修改' : '修改绑定' }}</a-button>
      </div>
      <div class="account-info">
        <p><span>学号</span><strong>{{ account.studentId || '-' }}</strong></p>
      </div>

      <div v-if="editingBind" class="bind-edit">
        <a-form layout="vertical">
          <a-form-item label="学号"><a-input v-model:value="bindForm.studentId" /></a-form-item>
          <a-form-item label="教务系统密码"><a-input-password v-model:value="bindForm.jwPassword" placeholder="重新输入密码以更换绑定" /></a-form-item>
          <a-button type="primary" :loading="binding" @click="handleBind">保存修改</a-button>
        </a-form>
      </div>

      <div class="term-row">
        <span>学年</span>
        <a-input v-model:value="xnm" placeholder="例如 2025-2026" class="year-input" />
        <span>学期</span>
        <a-select v-model:value="xqm" class="term-select" :options="termOptions" />
        <a-button @click="loadCourses" :loading="loadingCourses">查询</a-button>
        <a-button type="primary" ghost @click="handleSync" :loading="syncing"><SyncOutlined /> 同步</a-button>
      </div>

      <div v-if="errorMsg" class="error-text">{{ errorMsg }}</div>

      <a-table
        class="course-table"
        :columns="columns"
        :data-source="courseList"
        :loading="loadingCourses"
        :pagination="false"
        row-key="key"
        size="middle"
        :scroll="{ x: 720 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'weekday'">{{ record.weekdayLabel }}</template>
          <template v-else-if="column.key === 'term'">{{ record.termLabel }}</template>
          <template v-else-if="column.key === 'place'">{{ record.placeText }}</template>
          <template v-else-if="column.key === 'teacher'">{{ record.teacherText }}</template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { CalendarOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { scheduleApi } from '@client/api'

const WEEKDAY_NAMES = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
const TERM_OPTIONS = [
  { label: '第一学期', value: '1' },
  { label: '第二学期', value: '2' },
  { label: '第三学期', value: '3' }
]

const loadingStatus = ref(true)
const bound = ref(false)
const editingBind = ref(false)
const binding = ref(false)
const syncing = ref(false)
const loadingCourses = ref(false)
const errorMsg = ref('')
const account = reactive({ studentId: '' })
const bindForm = reactive({ studentId: '', jwPassword: '' })
const xnm = ref(defaultSchoolYear())
const xqm = ref('1')
const courseList = ref([])
const termOptions = TERM_OPTIONS
const columns = [
  { title: '课程名称', dataIndex: 'kcmc', key: 'kcmc' },
  { title: '学年', dataIndex: 'xnm', key: 'xnm', width: 110 },
  { title: '学期', key: 'term', width: 140 },
  { title: '星期', key: 'weekday', width: 80 },
  { title: '节次', dataIndex: 'jcs', key: 'jcs', width: 100 },
  { title: '地点', key: 'place', width: 140 },
  { title: '教师', key: 'teacher', width: 110 }
]

function defaultSchoolYear() {
  const year = new Date().getFullYear()
  return `${year - 1}-${year}`
}

function termName(xqm) {
  const option = TERM_OPTIONS.find(item => item.value === String(xqm || ''))
  return option ? option.label : `第${xqm || '-'}学期`
}

function extractMessage(err, fallback) {
  return err?.response?.data?.message || err?.message || fallback
}

onMounted(() => loadBindStatus())

async function loadBindStatus() {
  loadingStatus.value = true
  errorMsg.value = ''
  try {
    const res = await scheduleApi.bindStatus()
    bound.value = !!(res && res.bound)
    account.studentId = res?.studentId || ''
    bindForm.studentId = account.studentId
    bindForm.jwPassword = ''
    editingBind.value = !bound.value
    if (bound.value) await loadCourses()
  } catch (err) {
    errorMsg.value = extractMessage(err, '绑定状态加载失败')
  } finally {
    loadingStatus.value = false
  }
}

function toggleBindForm() {
  editingBind.value = !editingBind.value
  errorMsg.value = ''
}

async function handleBind() {
  const payload = {
    studentId: (bindForm.studentId || '').trim(),
    jwPassword: bindForm.jwPassword || ''
  }
  if (!payload.studentId || !payload.jwPassword) {
    message.warning('请完整填写绑定信息')
    return
  }
  if (payload.jwPassword.length < 6) {
    message.warning('教务密码至少 6 位')
    return
  }
  binding.value = true
  errorMsg.value = ''
  try {
    const res = await scheduleApi.bind(payload)
    account.studentId = res?.studentId || payload.studentId
    bound.value = true
    editingBind.value = false
    bindForm.jwPassword = ''
    message.success('绑定成功')
    await loadCourses()
  } catch (err) {
    errorMsg.value = extractMessage(err, '绑定失败，请稍后重试')
  } finally {
    binding.value = false
  }
}

async function loadCourses() {
  if (!bound.value) return
  loadingCourses.value = true
  errorMsg.value = ''
  try {
    const params = {}
    if (xnm.value) params.xnm = xnm.value
    if (xqm.value) params.xqm = xqm.value
    const res = await scheduleApi.list(params)
    courseList.value = (Array.isArray(res) ? res : []).map((item, index) => ({
      ...item,
      key: [item.xnm, item.xqm, item.xqj, item.jcs, item.kcmc, index].join('-'),
      weekdayLabel: WEEKDAY_NAMES[item.xqj] || `周${item.xqj || '-'}`,
      termLabel: `${item.xnm || '-'} ${termName(item.xqm)}`,
      placeText: item.cdmc || '地点待定',
      teacherText: item.xm || '教师待定'
    }))
  } catch (err) {
    errorMsg.value = extractMessage(err, '课表加载失败')
  } finally {
    loadingCourses.value = false
  }
}

async function handleSync() {
  if (syncing.value) return
  const payload = { xnm: (xnm.value || '').trim(), xqm: (xqm.value || '').trim() }
  if (!payload.xnm || !payload.xqm) {
    message.warning('请填写学年学期')
    return
  }
  syncing.value = true
  errorMsg.value = ''
  try {
    const res = await scheduleApi.sync(payload)
    message.success(`已同步 ${res?.imported || 0} 门课`)
    await loadCourses()
  } catch (err) {
    errorMsg.value = extractMessage(err, '同步失败，请稍后重试')
  } finally {
    syncing.value = false
  }
}
</script>

<style scoped>
.schedule-card { padding: 22px 20px; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-head h2 { margin: 0; font-size: 16px; color: #262626; }
.card-head > div { display: flex; align-items: center; gap: 8px; flex: 1; }
.card-tip { margin: 10px 0 18px; color: #8c8c8c; font-size: 12px; }
.bind-form { max-width: 420px; }
.account-info { display: flex; gap: 32px; margin: 14px 0; }
.account-info p { margin: 0; display: flex; flex-direction: column; gap: 4px; }
.account-info span { color: #8c8c8c; font-size: 12px; }
.account-info strong { color: #262626; font-size: 14px; }
.bind-edit { margin: 14px 0; padding: 16px; border: 1px dashed #d9d9d9; border-radius: 6px; }
.bind-edit .ant-form { max-width: 420px; }
.term-row { display: flex; align-items: center; gap: 8px; margin: 18px 0; flex-wrap: wrap; }
.term-row span { color: #595959; font-size: 13px; }
.year-input { width: 160px; }
.term-select { width: 140px; }
.course-table { margin-top: 8px; }
.error-text { margin: 10px 0; color: #cf1322; font-size: 13px; }
.dark .card-head h2, .dark .account-info strong { color: rgba(255,255,255,.88); }
@media (max-width: 600px) {
  .account-info { flex-direction: column; gap: 12px; }
  .year-input, .term-select { width: 100%; }
  .term-row { flex-direction: column; align-items: stretch; }
}
</style>
