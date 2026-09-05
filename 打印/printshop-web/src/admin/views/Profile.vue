<template>
  <div class="profile-page page-shell">
    <div class="welcome-row">
      <div>
        <h2>个人中心</h2>
        <p>查看账号资料、维护登录安全设置</p>
      </div>
    </div>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="8">
        <a-card class="profile-card" :bordered="false">
          <div class="profile-avatar">
            <a-avatar :size="80" class="profile-avatar-circle">{{ username.slice(0, 1).toUpperCase() }}</a-avatar>
          </div>
          <h3 class="profile-name">{{ username }}</h3>
          <p class="profile-email">{{ profile.email || '尚未绑定邮箱' }}</p>
          <div class="profile-meta">
            <span class="meta-item"><SafetyCertificateOutlined /> {{ roleLabel }}</span>
          </div>
          <div class="profile-actions">
            <a-button block @click="router.push('/dashboard')"><DashboardOutlined /> 返回工作台</a-button>
            <a-button danger block @click="handleLogout"><LogoutOutlined /> 退出登录</a-button>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="16">
        <a-card class="profile-section" :bordered="false" title="基本资料">
          <template #extra>
            <a-button type="primary" :loading="saving" @click="saveProfile"><SaveOutlined /> 保存</a-button>
          </template>
          <a-form layout="vertical" class="profile-form">
            <a-form-item label="显示名称">
              <a-input v-model:value="profile.username" placeholder="请输入显示名称" />
            </a-form-item>
            <a-form-item label="手机号码">
              <a-input v-model:value="profile.phone" placeholder="请输入 11 位手机号" />
            </a-form-item>
            <a-form-item label="QQ 邮箱">
              <a-input v-model:value="profile.email" disabled placeholder="QQ 邮箱绑定后不可在个人中心修改" />
            </a-form-item>
          </a-form>
        </a-card>

        <a-card class="profile-section" :bordered="false" title="修改密码">
          <a-form ref="passwordFormRef" layout="vertical" :model="passwordForm" :rules="passwordRules" class="profile-form" @finish="changePassword">
            <a-form-item label="当前密码" name="oldPassword">
              <a-input-password v-model:value="passwordForm.oldPassword" autocomplete="current-password" placeholder="用于确认本人操作" />
            </a-form-item>
            <a-form-item label="新密码" name="newPassword">
              <a-input-password v-model:value="passwordForm.newPassword" autocomplete="new-password" placeholder="长度 8-72 位" />
            </a-form-item>
            <a-form-item label="确认新密码" name="confirmPassword">
              <a-input-password v-model:value="passwordForm.confirmPassword" autocomplete="new-password" placeholder="再次输入新密码" />
            </a-form-item>
            <a-button html-type="submit" :loading="changing">更新密码</a-button>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  DashboardOutlined, LogoutOutlined, SafetyCertificateOutlined, SaveOutlined
} from '@ant-design/icons-vue'
import { adminApi, userApi } from '@/api'

const router = useRouter()
const username = ref(localStorage.getItem('admin_user') || 'Admin')
const adminRole = ref(localStorage.getItem('admin_role') || 'admin')
const adminId = Number(localStorage.getItem('admin_id') || 0)
const saving = ref(false)
const changing = ref(false)
const passwordFormRef = ref()
const profile = reactive({ username: '', phone: '', email: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const roleLabel = computed(() => ({ superadmin: '超级管理员', admin: '管理员', operator: '操作员' }[adminRole.value] || '管理员'))

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [{ required: true, message: '请输入新密码' }, { min: 8, max: 72, message: '密码长度应为 8 到 72 位' }],
  confirmPassword: [{
    validator: async (_rule, value) => {
      if (!value) throw new Error('请再次输入新密码')
      if (value !== passwordForm.newPassword) throw new Error('两次输入的新密码不一致')
    }
  }]
}

onMounted(async () => {
  if (!adminId) return
  try {
    const data = await userApi.getById(adminId)
    profile.username = data.username || ''
    profile.phone = data.phone || ''
    profile.email = data.email || ''
  } catch (err) {
    message.error('账号信息加载失败')
  }
})

async function saveProfile() {
  if (!profile.username.trim()) return message.warning('显示名称不能为空')
  if (profile.phone && !/^1[3-9]\d{9}$/.test(profile.phone)) return message.warning('手机号格式不正确')
  saving.value = true
  try {
    await userApi.update({ id: adminId, username: profile.username.trim(), phone: profile.phone.trim() || null, avatarUrl: null })
    localStorage.setItem('admin_user', profile.username.trim())
    username.value = profile.username.trim()
    message.success('资料已保存')
  } catch (err) {
    message.error(err?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  await passwordFormRef.value?.validate()
  changing.value = true
  try {
    await adminApi.changePassword(adminId, passwordForm.oldPassword, passwordForm.newPassword)
    message.success('密码修改成功，请重新登录')
    clearSession()
    router.replace({ path: '/login', query: { mode: 'admin' } })
  } catch (err) {
    message.error(err?.response?.data?.message || '密码修改失败')
  } finally {
    changing.value = false
  }
}

function clearSession() {
  ['admin_token', 'admin_token_expires_at', 'admin_user', 'admin_id', 'admin_role'].forEach(key => localStorage.removeItem(key))
}

function handleLogout() {
  Modal.confirm({
    title: '退出登录',
    content: '确定退出当前管理员账号吗？',
    okText: '退出',
    cancelText: '取消',
    onOk: () => {
      clearSession()
      router.replace({ path: '/login', query: { mode: 'admin' } })
    }
  })
}
</script>

<style scoped>
.profile-card { text-align: center; padding: 8px 4px; }
.profile-avatar { display: flex; justify-content: center; margin-bottom: 12px; }
.profile-avatar-circle { background: #1677ff; color: #fff; font-size: 28px; font-weight: 600; }
.profile-name { margin: 0; font-size: 18px; color: #262626; }
.profile-email { margin: 4px 0 12px; color: #8c8c8c; font-size: 13px; }
.profile-meta { display: flex; justify-content: center; gap: 12px; margin-bottom: 20px; }
.meta-item { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 12px; background: #e6f4ff; color: #1677ff; font-size: 12px; }
.profile-actions { display: grid; gap: 8px; }
.profile-section { margin-bottom: 16px; }
.profile-form { max-width: 480px; }
</style>
