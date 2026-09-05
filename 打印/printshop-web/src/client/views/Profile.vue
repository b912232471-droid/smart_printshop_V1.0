<template>
  <div>
    <div class="page-heading"><div><h1>个人中心</h1><p>维护账号资料、QQ邮箱和登录安全设置</p></div></div>
    <div class="profile-grid">
      <aside class="surface profile-card">
        <a-avatar :size="72" :src="auth.user?.avatarUrl"><UserOutlined /></a-avatar>
        <h2>{{ auth.user?.username || '用户' }}</h2>
        <p>{{ profile.email || '尚未绑定QQ邮箱' }}</p>
        <div class="profile-actions"><a-button block @click="router.push('/client/orders')"><FileTextOutlined /> 我的订单</a-button><a-button danger block @click="logout"><LogoutOutlined /> 退出登录</a-button></div>
      </aside>

      <div class="profile-main">
        <section class="surface profile-section">
          <div class="section-head"><div><h2>基本资料</h2><p>这些信息会用于订单联系和个人展示</p></div><a-button type="primary" :loading="saving" @click="saveProfile"><SaveOutlined /> 保存</a-button></div>
          <a-form layout="vertical" class="profile-form"><a-form-item label="显示名称"><a-input v-model:value="profile.username" placeholder="请输入显示名称" /></a-form-item><a-form-item label="手机号码"><a-input v-model:value="profile.phone" placeholder="请输入 11 位手机号" /></a-form-item></a-form>
        </section>

        <section class="surface profile-section">
          <div class="section-head"><div><h2>绑定或更换QQ邮箱</h2><p>绑定后只能使用该QQ邮箱登录</p></div></div>
          <a-form layout="vertical" class="profile-form" @finish="bindEmail">
            <a-form-item label="QQ邮箱" required><a-input v-model:value="emailForm.email" placeholder="例如 123456@qq.com"><template #prefix><MailOutlined /></template></a-input></a-form-item>
            <a-form-item label="当前密码" required><a-input-password v-model:value="emailForm.password" placeholder="用于确认本人操作" /></a-form-item>
            <a-form-item label="图片验证码" required><div class="captcha-row"><a-input v-model:value="emailForm.captchaCode" maxlength="4" placeholder="4位验证码" /><button class="captcha-image" type="button" @click="loadCaptcha"><img v-if="captchaImage" :src="captchaImage" alt="验证码" /><ReloadOutlined v-else /></button></div></a-form-item>
            <a-form-item label="邮箱验证码" required><div class="code-row"><a-input v-model:value="emailForm.emailCode" maxlength="6" placeholder="6位验证码" /><a-button :loading="emailSending" :disabled="countdown > 0" @click="sendBindCode">{{ countdown > 0 ? `${countdown}s` : '发送验证码' }}</a-button></div></a-form-item>
            <a-button html-type="submit" :loading="binding">确认绑定</a-button>
          </a-form>
        </section>

        <section class="surface profile-section">
          <div class="section-head"><div><h2>修改密码</h2><p>建议定期更新密码，长度至少 8 位</p></div></div>
          <a-form layout="vertical" class="profile-form" @finish="changePassword"><a-form-item label="当前密码"><a-input-password v-model:value="password.oldPassword" /></a-form-item><a-form-item label="新密码"><a-input-password v-model:value="password.newPassword" /></a-form-item><a-form-item><a-button html-type="submit" :loading="changing">更新密码</a-button></a-form-item></a-form>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { FileTextOutlined, LogoutOutlined, MailOutlined, ReloadOutlined, SaveOutlined, UserOutlined } from '@ant-design/icons-vue'
import { authApi, userApi } from '@client/api'
import { useAuthStore } from '@client/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const saving = ref(false)
const changing = ref(false)
const binding = ref(false)
const emailSending = ref(false)
const countdown = ref(0)
const captchaId = ref('')
const captchaImage = ref('')
const profile = reactive({ username: '', phone: '', email: '' })
const password = reactive({ oldPassword: '', newPassword: '' })
const emailForm = reactive({ email: '', password: '', captchaCode: '', emailCode: '' })
let timer

onMounted(async () => {
  const data = await userApi.getById(auth.user.id)
  Object.assign(profile, { username: data.username || '', phone: data.phone || '', email: data.email || '' })
  emailForm.email = data.email || ''
  auth.updateUser({ ...auth.user, ...data })
  loadCaptcha()
})

async function saveProfile() {
  if (!profile.username.trim()) return message.warning('显示名称不能为空')
  if (profile.phone && !/^1[3-9]\d{9}$/.test(profile.phone)) return message.warning('手机号格式不正确')
  saving.value = true
  try {
    await userApi.update({ id: auth.user.id, username: profile.username.trim(), phone: profile.phone.trim() || null, avatarUrl: auth.user.avatarUrl || null })
    auth.updateUser({ ...auth.user, username: profile.username.trim(), phone: profile.phone.trim() || null })
    message.success('资料已保存')
  } finally { saving.value = false }
}

async function loadCaptcha() {
  emailForm.captchaCode = ''
  const res = await authApi.captcha()
  captchaId.value = res.data.captchaId
  captchaImage.value = res.data.image
}

async function sendBindCode() {
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}@qq\.com$/.test(emailForm.email.trim())) return message.warning('请输入有效的QQ邮箱')
  if (emailForm.captchaCode.length !== 4) return message.warning('请先输入图片验证码')
  emailSending.value = true
  try {
    await authApi.sendEmailCode({ email: emailForm.email.trim(), purpose: 'bind_email', captchaId: captchaId.value, captchaCode: emailForm.captchaCode })
    message.success('验证码已发送')
    countdown.value = 60
    clearInterval(timer)
    timer = setInterval(() => { countdown.value -= 1; if (countdown.value <= 0) clearInterval(timer) }, 1000)
  } finally { emailSending.value = false }
}

async function bindEmail() {
  if (!emailForm.password || emailForm.emailCode.length !== 6) return message.warning('请填写当前密码和邮箱验证码')
  binding.value = true
  try {
    await authApi.bindEmail({ email: emailForm.email.trim(), emailCode: emailForm.emailCode, password: emailForm.password })
    profile.email = emailForm.email.trim().toLowerCase()
    auth.updateUser({ ...auth.user, email: profile.email })
    emailForm.password = ''
    emailForm.emailCode = ''
    message.success('QQ邮箱绑定成功')
  } finally { binding.value = false }
}

async function changePassword() {
  if (password.oldPassword.length < 8 || password.newPassword.length < 8) return message.warning('密码长度至少 8 位')
  changing.value = true
  try {
    await authApi.changePassword(password)
    password.oldPassword = ''
    password.newPassword = ''
    message.success('密码修改成功')
  } finally { changing.value = false }
}

function logout() {
  Modal.confirm({ title: '确认退出登录？', content: '退出后需要重新输入QQ邮箱和密码。', okText: '退出', okType: 'danger', cancelText: '取消', onOk() { auth.clearSession(); router.replace('/login?mode=client') } })
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.profile-grid{display:grid;grid-template-columns:250px minmax(0,1fr);gap:14px;align-items:start}.profile-card{padding:26px 20px;text-align:center}.profile-card h2{margin-top:13px;color:#262626;font-size:17px}.profile-card>p{margin-top:5px;color:#8c8c8c;font-size:12px}.profile-actions{display:grid;margin-top:24px;gap:8px}.profile-main{display:grid;gap:12px}.profile-section{padding:20px}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:18px}.section-head h2{color:#262626;font-size:16px}.section-head p{margin-top:4px;color:#8c8c8c;font-size:12px}.profile-form{max-width:600px}.captcha-row,.code-row{display:grid;grid-template-columns:minmax(0,1fr) 132px;gap:10px}.captcha-image{display:grid;width:132px;height:32px;padding:0;overflow:hidden;place-items:center;border:1px solid #d9d9d9;background:#f5f8ff}.captcha-image img{width:132px;height:32px;object-fit:cover}.dark .profile-card h2,.dark .section-head h2{color:rgba(255,255,255,.88)}@media(max-width:750px){.profile-grid{grid-template-columns:1fr}.profile-card{display:grid;grid-template-columns:auto 1fr;column-gap:14px;text-align:left}.profile-card h2{align-self:end;margin:0}.profile-card>p{align-self:start}.profile-actions{grid-column:1/-1;grid-template-columns:1fr 1fr}.section-head{align-items:flex-start;flex-direction:column}.section-head>.ant-btn{align-self:flex-end}}
</style>
