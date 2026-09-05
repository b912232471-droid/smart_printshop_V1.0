<template>
  <div class="page-shell">
    <a-card class="content-card" :bordered="false">
      <div class="page-toolbar">
        <div class="page-toolbar__title"><h2>管理员管理</h2><p>维护后台账号、角色和启用状态</p></div>
        <a-button v-permission="'print:admin:add'" type="primary" @click="showRegisterDialog"><PlusOutlined />新增管理员</a-button>
      </div>
      <a-table :columns="columns" :data-source="admins" :loading="loading" row-key="id" :scroll="{ x: 980 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'username'"><div class="admin-cell"><a-avatar>{{ (record.email || record.username)?.slice(0, 1).toUpperCase() }}</a-avatar><div><strong>{{ record.email || record.username }}</strong><span>{{ record.realName || '未填写姓名' }}</span></div></div></template>
          <template v-else-if="column.key === 'contact'"><div class="contact-cell"><span>{{ record.phone || '未填写手机号' }}</span><small>{{ record.email || '未填写邮箱' }}</small></div></template>
          <template v-else-if="column.key === 'role'"><a-tag :color="getRoleColor(record.role)">{{ getRoleText(record.role) }}</a-tag></template>
          <template v-else-if="column.key === 'status'"><a-badge :status="record.status === 1 ? 'success' : 'error'" :text="record.status === 1 ? '启用' : '禁用'" /></template>
          <template v-else-if="column.key === 'action'"><div class="table-actions"><a-button type="link" size="small" :disabled="!canEdit(record)" @click="editAdmin(record)">编辑</a-button><a-button type="link" size="small" :disabled="!canChangePassword(record)" @click="changePassword(record)">改密</a-button><a-button type="link" danger size="small" :disabled="!canDelete(record)" @click="deleteAdmin(record)">删除</a-button></div></template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="dialogVisible" :title="isEdit ? '编辑管理员' : '新增管理员'" ok-text="保存" cancel-text="取消" @ok="submitForm">
      <a-form layout="vertical">
        <a-row :gutter="16"><a-col :span="12"><a-form-item label="QQ邮箱" required><a-input v-model:value="form.email" :disabled="isEdit" /></a-form-item></a-col><a-col :span="12"><a-form-item label="真实姓名"><a-input v-model:value="form.realName" /></a-form-item></a-col></a-row>
        <a-form-item v-if="!isEdit" label="初始密码" required><a-input-password v-model:value="form.password" /></a-form-item>
        <template v-if="!isEdit"><a-form-item label="图片验证码" required><div class="verify-row"><a-input v-model:value="form.captchaCode" maxlength="4" placeholder="4位验证码" /><button class="captcha-image" type="button" @click="loadCaptcha"><img v-if="captchaImage" :src="captchaImage" alt="验证码" /><span v-else>刷新</span></button></div></a-form-item><a-form-item label="邮箱验证码" required><div class="verify-row"><a-input v-model:value="form.emailCode" maxlength="6" placeholder="6位验证码" /><a-button :loading="emailSending" :disabled="countdown > 0" @click="sendAdminCode">{{ countdown > 0 ? `${countdown}s` : '发送验证码' }}</a-button></div></a-form-item></template>
        <a-form-item label="手机号"><a-input v-model:value="form.phone" /></a-form-item>
        <a-row :gutter="16"><a-col :span="12"><a-form-item label="角色"><a-select v-model:value="form.role" style="width: 100%"><a-select-option value="admin">管理员</a-select-option><a-select-option value="operator">操作员</a-select-option><a-select-option v-if="permissionStore.isSuperAdmin" value="superadmin">超级管理员</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="状态"><a-radio-group v-model:value="form.status"><a-radio :value="1">启用</a-radio><a-radio :value="0">禁用</a-radio></a-radio-group></a-form-item></a-col></a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="passwordDialogVisible" title="修改管理员密码" ok-text="确认修改" cancel-text="取消" @ok="submitPasswordChange">
      <a-form layout="vertical"><a-form-item label="当前密码" required><a-input-password v-model:value="passwordForm.oldPassword" /></a-form-item><a-form-item label="新密码" required><a-input-password v-model:value="passwordForm.newPassword" /></a-form-item><a-form-item label="确认新密码" required><a-input-password v-model:value="passwordForm.confirmPassword" /></a-form-item></a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { adminApi } from '@/api'
import { usePermissionStore } from '@/stores/permission'

const permissionStore = usePermissionStore()
const loading = ref(false); const admins = ref([]); const dialogVisible = ref(false); const passwordDialogVisible = ref(false); const isEdit = ref(false)
const captchaId = ref(''); const captchaImage = ref(''); const emailSending = ref(false); const countdown = ref(0); let timer
const currentAdminId = ref(Number(localStorage.getItem('admin_id') || 0))
const form = ref({ id: null, username: '', password: '', realName: '', phone: '', email: '', emailCode: '', captchaCode: '', role: 'admin', status: 1 })
const passwordForm = ref({ id: null, oldPassword: '', newPassword: '', confirmPassword: '' })
const columns = [{ title: '管理员', key: 'username', width: 210 }, { title: '联系方式', key: 'contact', width: 220 }, { title: '角色', key: 'role', width: 120 }, { title: '状态', key: 'status', width: 100 }, { title: '最后登录', dataIndex: 'lastLoginTime', key: 'lastLoginTime', width: 180 }, { title: '操作', key: 'action', width: 210 }]
const loadAdmins = async () => { loading.value = true; try { admins.value = await adminApi.getAll() || [] } finally { loading.value = false } }
const canDelete = row => row.id !== currentAdminId.value && permissionStore.hasPerm('print:admin:delete')
const canEdit = row => row.id !== currentAdminId.value && permissionStore.hasPerm('print:admin:update')
const canChangePassword = row => row.id === currentAdminId.value || permissionStore.hasPerm('print:admin:resetPwd')
const getRoleText = role => ({ admin: '管理员', operator: '操作员', superadmin: '超级管理员' }[role] || role)
const getRoleColor = role => ({ admin: 'blue', operator: 'default', superadmin: 'red' }[role] || 'default')
const showRegisterDialog = () => { isEdit.value = false; form.value = { id: null, username: '', password: '', realName: '', phone: '', email: '', emailCode: '', captchaCode: '', role: 'admin', status: 1 }; dialogVisible.value = true; loadCaptcha() }
const editAdmin = row => { if (!canEdit(row)) return message.warning('当前账号无权编辑该管理员'); isEdit.value = true; form.value = { ...row, password: '' }; dialogVisible.value = true }
const changePassword = row => { if (!canChangePassword(row)) return message.warning('当前账号无权修改该密码'); passwordForm.value = { id: row.id, oldPassword: '', newPassword: '', confirmPassword: '' }; passwordDialogVisible.value = true }
const deleteAdmin = row => Modal.confirm({ title: '删除管理员', content: `确定删除管理员“${row.username}”吗？`, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => { await adminApi.delete(row.id); message.success('删除成功'); loadAdmins() } })
const loadCaptcha = async () => { form.value.captchaCode = ''; const res = await adminApi.captcha(); captchaId.value = res.data.captchaId; captchaImage.value = res.data.image }
const sendAdminCode = async () => { if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}@qq\.com$/.test(form.value.email)) return message.warning('请输入有效QQ邮箱'); if (form.value.captchaCode.length !== 4) return message.warning('请先输入图片验证码'); emailSending.value = true; try { await adminApi.sendEmailCode({ email: form.value.email, purpose: 'admin_register', captchaId: captchaId.value, captchaCode: form.value.captchaCode }); message.success('验证码已发送'); countdown.value = 60; clearInterval(timer); timer = setInterval(() => { countdown.value -= 1; if (countdown.value <= 0) clearInterval(timer) }, 1000) } finally { emailSending.value = false } }
const submitForm = async () => { if (!form.value.email) return message.warning('请输入QQ邮箱'); if (!isEdit.value && (!form.value.password || form.value.emailCode.length !== 6)) return message.warning('请填写初始密码和邮箱验证码'); form.value.username = form.value.email; if (isEdit.value) await adminApi.update(form.value); else await adminApi.register(form.value); message.success('保存成功'); dialogVisible.value = false; loadAdmins() }
const submitPasswordChange = async () => { const data = passwordForm.value; if (!data.oldPassword || !data.newPassword) return message.warning('请填写完整'); if (data.newPassword !== data.confirmPassword) return message.warning('两次密码不一致'); await adminApi.changePassword(data.id, data.oldPassword, data.newPassword); message.success('密码修改成功'); passwordDialogVisible.value = false }
onMounted(loadAdmins)
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped lang="less">
.admin-cell { display: flex; align-items: center; gap: 10px; }.admin-cell .ant-avatar { background: #1677ff; }.admin-cell > div,.contact-cell { display: flex; flex-direction: column; }.admin-cell strong { color: #262626; }.admin-cell span,.contact-cell small { color: #8c8c8c; font-size: 11px; }
.verify-row { display: grid; grid-template-columns: minmax(0, 1fr) 132px; gap: 10px; }.captcha-image { display: grid; width: 132px; height: 32px; padding: 0; overflow: hidden; place-items: center; border: 1px solid #d9d9d9; background: #f5f8ff; }.captcha-image img { width: 132px; height: 32px; object-fit: cover; }
</style>
