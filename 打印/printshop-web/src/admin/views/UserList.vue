<template>
  <div class="page-shell">
    <a-card class="content-card" :bordered="false">
      <div class="page-toolbar">
        <div class="page-toolbar__title"><h2>账户管理</h2><p>维护注册账户的角色、状态、密码与功能额度</p></div>
        <div class="toolbar-actions">
          <a-input-search v-model:value="filters.keyword" placeholder="邮箱或昵称" allow-clear style="width: 210px" @search="onSearch" />
          <a-select v-model:value="filters.accountType" style="width: 110px" @change="onSearch">
            <a-select-option value="">全部类型</a-select-option>
            <a-select-option value="user">用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
          <a-select v-model:value="filters.role" style="width: 130px" @change="onSearch">
            <a-select-option value="">全部角色</a-select-option>
            <a-select-option v-for="role in roles" :key="role.roleKey" :value="role.roleKey">{{ role.roleName }}</a-select-option>
          </a-select>
          <a-select v-model:value="filters.status" style="width: 100px" @change="onSearch">
            <a-select-option :value="''">全部状态</a-select-option>
            <a-select-option :value="1">启用</a-select-option>
            <a-select-option :value="0">禁用</a-select-option>
          </a-select>
          <a-button type="primary" @click="onSearch"><SearchOutlined />查询</a-button>
          <a-button @click="resetFilters">重置</a-button>
        </div>
      </div>

      <a-table
        class="desktop-table"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        row-key="id"
        :scroll="{ x: 1180 }"
        :pagination="pagination"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'account'">
            <div class="user-cell">
              <a-avatar :src="getAvatarUrl(record.avatarUrl)">{{ initial(record.displayName || record.username) }}</a-avatar>
              <div><strong>{{ record.displayName || '未设置' }}</strong><span>{{ record.username }}</span></div>
            </div>
          </template>
          <template v-else-if="column.key === 'accountType'">
            <a-tag :color="record.accountType === 'admin' ? 'orange' : 'blue'">{{ record.accountType === 'admin' ? '管理员' : '用户' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'role'">
            <a-tag :color="getRoleColor(record.role)">{{ getRoleName(record.role) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-badge :status="record.status === 1 ? 'success' : 'error'" :text="record.status === 1 ? '启用' : '禁用'" />
          </template>
          <template v-else-if="column.key === 'quota'">
            <div class="quota-cell">
              <span>生图：{{ quotaText(record.imagegenDailyLimit, defaults.imagegenDailyLimit) }}</span>
              <span>OCR：{{ quotaText(record.ocrDailyLimit, defaults.ocrDailyLimit) }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="table-actions">
              <a-button v-permission="'print:user:update'" type="link" size="small" :disabled="!canOperate(record)" @click="openRoleModal(record)">分配角色</a-button>
              <a-popconfirm :title="record.status === 1 ? '禁用后该账户将无法登录，确定？' : '确定恢复该账户？'" ok-text="确定" cancel-text="取消" @confirm="toggleStatus(record)">
                <a-button v-permission="'print:user:update'" type="link" size="small" :disabled="!canOperate(record) || isSelf(record)">{{ record.status === 1 ? '禁用' : '启用' }}</a-button>
              </a-popconfirm>
              <a-button v-permission="'print:user:resetPwd'" type="link" size="small" :disabled="!canOperate(record)" @click="openResetPwdModal(record)">重置密码</a-button>
              <a-button v-permission="'print:user:quota'" type="link" size="small" @click="openQuotaModal(record)">额度</a-button>
              <a-button type="link" size="small" @click="viewUserOrders(record)">订单</a-button>
              <a-button v-if="record.accountType === 'user'" v-permission="'print:user:delete'" type="link" danger size="small" @click="deleteAccount(record)">删除</a-button>
            </div>
          </template>
        </template>
      </a-table>

      <div class="mobile-list">
        <a-card v-for="record in records" :key="record.id" size="small" class="mobile-account-card">
          <div class="user-cell">
            <a-avatar :size="42" :src="getAvatarUrl(record.avatarUrl)">{{ initial(record.displayName || record.username) }}</a-avatar>
            <div>
              <strong>{{ record.displayName || '未设置' }}</strong>
              <span>{{ record.username }}</span>
            </div>
            <a-tag :color="getRoleColor(record.role)" class="mobile-role">{{ getRoleName(record.role) }}</a-tag>
          </div>
          <div class="mobile-meta">
            <span>{{ record.accountType === 'admin' ? '管理员' : '用户' }}</span>
            <span>{{ record.status === 1 ? '启用' : '禁用' }}</span>
            <span>生图 {{ quotaText(record.imagegenDailyLimit, defaults.imagegenDailyLimit) }}</span>
          </div>
          <div class="mobile-actions">
            <a-button v-permission="'print:user:update'" size="small" :disabled="!canOperate(record)" @click="openRoleModal(record)">角色</a-button>
            <a-button v-permission="'print:user:update'" size="small" :disabled="!canOperate(record) || isSelf(record)" @click="toggleStatus(record)">{{ record.status === 1 ? '禁用' : '启用' }}</a-button>
            <a-button v-permission="'print:user:resetPwd'" size="small" :disabled="!canOperate(record)" @click="openResetPwdModal(record)">改密</a-button>
            <a-button v-permission="'print:user:quota'" size="small" @click="openQuotaModal(record)">额度</a-button>
          </div>
        </a-card>
      </div>
    </a-card>

    <a-modal v-model:open="roleModal.visible" title="分配角色" ok-text="保存" cancel-text="取消" :confirm-loading="roleModal.saving" @ok="submitRole">
      <a-form layout="vertical">
        <a-form-item label="账户"><a-input :value="roleModal.accountLabel" disabled /></a-form-item>
        <a-form-item label="角色" required extra="角色变更后该账户需重新登录生效；仅超级管理员可授予 superadmin 角色">
          <a-select v-model:value="roleModal.role" style="width: 100%">
            <a-select-option v-for="role in assignableRoles" :key="role.roleKey" :value="role.roleKey" :disabled="role.roleKey === 'superadmin' && !permissionStore.isSuperAdmin">
              {{ role.roleName }}（{{ role.roleKey }}）
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="resetPwdModal.visible" title="重置密码" ok-text="确认重置" cancel-text="取消" :confirm-loading="resetPwdModal.saving" @ok="submitResetPwd">
      <a-form layout="vertical">
        <a-form-item label="账户"><a-input :value="resetPwdModal.accountLabel" disabled /></a-form-item>
        <a-form-item label="新密码" required extra="8-72 位，字母/数字/符号至少两类，不含空白">
          <a-input-password v-model:value="resetPwdModal.newPassword" />
        </a-form-item>
        <a-form-item label="确认新密码" required><a-input-password v-model:value="resetPwdModal.confirmPassword" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="quotaModal.visible" title="功能额度分配" ok-text="保存" cancel-text="取消" :confirm-loading="quotaModal.saving" @ok="submitQuota">
      <a-form layout="vertical">
        <a-form-item label="账户"><a-input :value="quotaModal.accountLabel" disabled /></a-form-item>
        <a-form-item extra="留空跟随全局默认；0 表示对该账户禁用该功能">
          <template #label>AI 图片生成每日额度</template>
          <a-input-number v-model:value="quotaModal.imagegenDailyLimit" :min="0" :max="9999" style="width: 100%" :placeholder="`跟随全局默认（${defaults.imagegenDailyLimit ?? '-'} 张/日）`" />
        </a-form-item>
        <a-form-item extra="留空跟随全局默认；0 表示对该账户禁用该功能">
          <template #label>OCR 识别每日额度</template>
          <a-input-number v-model:value="quotaModal.ocrDailyLimit" :min="0" :max="9999" style="width: 100%" :placeholder="`跟随全局默认（${defaults.ocrDailyLimit ?? '-'} 次/日）`" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { accountApi, toPrintApiUrl, userApi } from '@/api'
import { usePermissionStore } from '@/stores/permission'

const permissionStore = usePermissionStore()
const router = useRouter()
const loading = ref(false)
const records = ref([])
const roles = ref([])
const defaults = ref({ imagegenDailyLimit: null, ocrDailyLimit: null })
const total = ref(0)
const filters = reactive({ keyword: '', accountType: '', role: '', status: '' })
const pagination = reactive({ current: 1, pageSize: 10, total: 0, showSizeChanger: true, showTotal: t => `共 ${t} 个账户` })
const currentAdminId = ref(Number(localStorage.getItem('admin_id') || 0))

const roleModal = reactive({ visible: false, saving: false, id: null, accountLabel: '', role: 'user' })
const resetPwdModal = reactive({ visible: false, saving: false, id: null, accountLabel: '', newPassword: '', confirmPassword: '' })
const quotaModal = reactive({ visible: false, saving: false, id: null, accountLabel: '', imagegenDailyLimit: null, ocrDailyLimit: null })

const columns = [
  { title: '账户', key: 'account', width: 230 },
  { title: '类型', key: 'accountType', width: 90 },
  { title: '角色', key: 'role', width: 110 },
  { title: '状态', key: 'status', width: 90 },
  { title: '功能额度', key: 'quota', width: 180 },
  { title: '注册时间', dataIndex: 'createdAt', width: 170 },
  { title: '最后登录', dataIndex: 'lastLoginAt', width: 170 },
  { title: '操作', key: 'action', width: 320, fixed: 'right' }
]

const assignableRoles = computed(() => roles.value.filter(role => role.status === 1))

const getRoleName = roleKey => roles.value.find(role => role.roleKey === roleKey)?.roleName || roleKey || '未分配'
const getRoleColor = roleKey => ({ superadmin: 'red', admin: 'orange', operator: 'cyan', user: 'blue' }[roleKey] || 'default')
const quotaText = (override, globalDefault) => override == null ? `默认 ${globalDefault ?? '-'}` : `自定义 ${override}`
const initial = name => String(name || 'U').slice(0, 1).toUpperCase()
const isSelf = record => record.id === currentAdminId.value
const canOperate = record => !isSuperadminRow(record) || permissionStore.isSuperAdmin
const isSuperadminRow = record => record.role === 'superadmin'
const getAvatarUrl = avatarUrl => {
  if (!avatarUrl) return ''
  if (avatarUrl.startsWith('http')) {
    try {
      const url = new URL(avatarUrl)
      return url.pathname.startsWith('/api/') ? toPrintApiUrl(url.pathname) : avatarUrl
    } catch {
      return avatarUrl
    }
  }
  return avatarUrl.startsWith('/api/') ? toPrintApiUrl(avatarUrl) : avatarUrl
}

const loadAccounts = async () => {
  loading.value = true
  try {
    const payload = (await accountApi.list({
      keyword: filters.keyword || undefined,
      accountType: filters.accountType || undefined,
      role: filters.role || undefined,
      status: filters.status === '' ? undefined : filters.status,
      pageNum: pagination.current,
      pageSize: pagination.pageSize
    }))?.data || {}
    records.value = (payload.records || []).map(item => ({ ...item, avatarUrl: getAvatarUrl(item.avatarUrl) }))
    roles.value = payload.roles || []
    defaults.value = payload.defaults || { imagegenDailyLimit: null, ocrDailyLimit: null }
    total.value = Number(payload.total || 0)
    pagination.total = total.value
  } finally {
    loading.value = false
  }
}

const onSearch = () => { pagination.current = 1; loadAccounts() }
const resetFilters = () => {
  filters.keyword = ''
  filters.accountType = ''
  filters.role = ''
  filters.status = ''
  onSearch()
}
const onTableChange = pager => {
  pagination.current = pager.current
  pagination.pageSize = pager.pageSize
  loadAccounts()
}

const accountLabel = record => `${record.displayName || record.username}（${record.username}）`

const openRoleModal = record => {
  roleModal.id = record.id
  roleModal.accountLabel = accountLabel(record)
  roleModal.role = record.role || 'user'
  roleModal.visible = true
}
const submitRole = async () => {
  roleModal.saving = true
  try {
    await accountApi.updateRole(roleModal.id, roleModal.role)
    message.success('角色已更新，该账户重新登录后生效')
    roleModal.visible = false
    loadAccounts()
  } finally {
    roleModal.saving = false
  }
}

const toggleStatus = async record => {
  const next = record.status === 1 ? 0 : 1
  await accountApi.updateStatus(record.id, next)
  message.success(next === 1 ? '账户已启用' : '账户已禁用')
  loadAccounts()
}

const openResetPwdModal = record => {
  resetPwdModal.id = record.id
  resetPwdModal.accountLabel = accountLabel(record)
  resetPwdModal.newPassword = ''
  resetPwdModal.confirmPassword = ''
  resetPwdModal.visible = true
}
const submitResetPwd = async () => {
  const { newPassword, confirmPassword } = resetPwdModal
  if (!newPassword) return message.warning('请输入新密码')
  if (newPassword !== confirmPassword) return message.warning('两次输入的密码不一致')
  if (/\s/.test(newPassword) || newPassword.length < 8 || newPassword.length > 72) {
    return message.warning('密码须为 8-72 位且不含空白字符')
  }
  if (!(/[a-zA-Z]/.test(newPassword) && (/[0-9]/.test(newPassword) || /[^a-zA-Z0-9]/.test(newPassword)))) {
    return message.warning('密码须包含字母、数字或符号中的至少两类')
  }
  resetPwdModal.saving = true
  try {
    await accountApi.resetPassword(resetPwdModal.id, newPassword)
    message.success('密码已重置，请通知账户使用新密码登录')
    resetPwdModal.visible = false
  } finally {
    resetPwdModal.saving = false
  }
}

const openQuotaModal = async record => {
  quotaModal.id = record.id
  quotaModal.accountLabel = accountLabel(record)
  quotaModal.imagegenDailyLimit = record.imagegenDailyLimit ?? null
  quotaModal.ocrDailyLimit = record.ocrDailyLimit ?? null
  quotaModal.visible = true
  try {
    const data = await accountApi.quota(record.id)
    quotaModal.imagegenDailyLimit = data?.data?.imagegenDailyLimit ?? null
    quotaModal.ocrDailyLimit = data?.data?.ocrDailyLimit ?? null
    if (data?.data?.defaults) defaults.value = data.data.defaults
  } catch {
    // 保留列表行数据作为初始值
  }
}
const submitQuota = async () => {
  quotaModal.saving = true
  try {
    await accountApi.setQuota(quotaModal.id, {
      imagegenDailyLimit: quotaModal.imagegenDailyLimit ?? null,
      ocrDailyLimit: quotaModal.ocrDailyLimit ?? null
    })
    message.success('额度配置已保存')
    quotaModal.visible = false
    loadAccounts()
  } finally {
    quotaModal.saving = false
  }
}

const viewUserOrders = record => router.push({ path: '/orders', query: { userId: record.id } })

const deleteAccount = record => {
  Modal.confirm({
    title: '删除账户',
    content: `确定删除账户“${record.displayName || record.username}”吗？该账户的角色授权将被清理，此操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await userApi.delete(record.id)
      message.success('删除成功')
      loadAccounts()
    }
  })
}

onMounted(loadAccounts)
</script>

<style scoped lang="less">
.user-cell { display: flex; align-items: center; gap: 11px; }
.user-cell > div { min-width: 0; display: flex; flex-direction: column; }
.user-cell strong { overflow: hidden; color: #262626; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.user-cell span { color: #8c8c8c; font-size: 11px; }
.quota-cell { display: flex; flex-direction: column; font-size: 12px; color: #595959; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.mobile-account-card { border: 1px solid #e5e7eb; }
.mobile-role { margin-left: auto; }
.mobile-meta { display: flex; gap: 12px; margin-top: 8px; color: #8c8c8c; font-size: 12px; }
.mobile-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; padding-top: 10px; border-top: 1px solid #f0f0f0; }
@media (min-width: 901px) {
  .mobile-list { display: none; }
}
@media (max-width: 900px) {
  .desktop-table { display: none; }
}
</style>
