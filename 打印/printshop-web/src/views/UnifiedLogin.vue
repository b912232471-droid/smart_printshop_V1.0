<template>
  <div class="login-container">
    <div class="login-preferences">
      <a-tooltip :title="preference.isDark ? '浅色模式' : '深色模式'">
        <a-button type="text" shape="circle" @click="preference.toggleThemeWithAnimation">
          <BulbOutlined v-if="preference.isDark" /><SkinOutlined v-else />
        </a-button>
      </a-tooltip>
    </div>

    <section class="login-left">
      <div class="login-filter"></div>
      <div class="login-brand"><img :src="logoUrl" alt="智慧打印" /><span>智慧在线打印平台</span></div>
      <div class="login-illustration"><img :src="illustrationUrl" alt="智慧打印工作台" /></div>
      <div class="login-slogan">
        <h2>{{ portal === 'admin' ? '统一管理打印业务' : '上传文件，到店直接取件' }}</h2>
        <p>{{ portal === 'admin' ? '订单、门店、服务与用户数据集中管理' : '在线选择打印参数，随时查看订单进度' }}</p>
      </div>
    </section>

    <section class="login-right">
      <div class="login-form-wrap">
        <div class="login-form-title"><h1>账号登录</h1><p>请选择要进入的平台，并使用对应账号登录</p></div>

        <div class="portal-switch" aria-label="选择登录平台">
          <button type="button" :class="{ active: portal === 'client' }" @click="setPortal('client')">
            <UserOutlined /><span><strong>前往用户端</strong><small>在线下单与查询订单</small></span>
          </button>
          <button type="button" :class="{ active: portal === 'admin' }" @click="setPortal('admin')">
            <SafetyCertificateOutlined /><span><strong>前往管理端</strong><small>管理平台业务数据</small></span>
          </button>
        </div>

        <a-segmented v-if="portal === 'client'" v-model:value="clientAction" block :options="clientActions" class="login-mode" />

        <a-form ref="formRef" :model="form" layout="vertical" autocomplete="on" @finish="handleSubmit">
          <a-form-item label="QQ号" name="qq" :rules="qqRules">
            <a-input v-model:value="form.qq" size="large" maxlength="11" placeholder="请输入QQ号" autocomplete="off" allow-clear><template #prefix><MailOutlined /></template><template #addonAfter>@qq.com</template></a-input>
          </a-form-item>
          <a-form-item v-if="portal === 'client' && clientAction === 'register'" label="显示名称" name="displayName">
            <a-input v-model:value="form.displayName" size="large" placeholder="请输入显示名称" />
          </a-form-item>
          <a-form-item :label="clientAction === 'forgot' ? '设置新密码' : (clientAction === 'register' ? '设置密码' : '密码')" name="password" :rules="[{ required: true, message: '请输入密码' }, { min: 8, message: '密码至少 8 位' }]">
            <a-input-password v-model:value="form.password" size="large" :placeholder="clientAction === 'forgot' ? '请输入新密码' : '请输入密码'" :autocomplete="clientAction === 'forgot' ? 'new-password' : 'current-password'"><template #prefix><LockOutlined /></template></a-input-password>
          </a-form-item>
          <a-form-item v-if="clientAction === 'register'" label="确认密码" name="confirmPassword" :rules="confirmPasswordRules">
            <a-input-password v-model:value="form.confirmPassword" size="large" placeholder="请再次输入密码" autocomplete="new-password"><template #prefix><LockOutlined /></template></a-input-password>
          </a-form-item>
          <a-form-item v-if="requiresCaptcha" label="验证码" name="captchaCode" :rules="[{ required: true, message: '请输入验证码' }, { len: 4, message: '请输入 4 位验证码' }]">
            <div class="captcha-row">
              <a-input v-model:value="form.captchaCode" size="large" maxlength="4" placeholder="请输入验证码" autocomplete="off" />
              <button class="captcha-image" type="button" :disabled="captchaLoading" title="刷新验证码" @click="loadCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="登录验证码" />
                <ReloadOutlined v-else :spin="captchaLoading" />
              </button>
            </div>
          </a-form-item>
          <a-form-item v-if="clientAction === 'forgot' || (portal === 'client' && clientAction === 'register')" label="邮箱验证码" name="emailCode" :rules="[{ required: true, message: '请输入邮箱验证码' }, { len: 6, message: '请输入6位验证码' }]">
            <div class="email-code-row"><a-input v-model:value="form.emailCode" size="large" maxlength="6" placeholder="6位验证码" /><a-button size="large" :loading="emailSending" :disabled="emailCountdown > 0" @click="sendEmailCode">{{ emailCountdown > 0 ? `${emailCountdown}s` : '发送验证码' }}</a-button></div>
          </a-form-item>
          <div class="login-options">
            <a-checkbox v-if="clientAction === 'login'" v-model:checked="remember">记住QQ号</a-checkbox><span v-else></span>
            <a-button v-if="clientAction === 'login'" type="link" size="small" @click="clientAction = 'forgot'">忘记密码</a-button>
            <span v-else>{{ portal === 'admin' ? '管理员入口' : '用户入口' }}</span>
          </div>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading" class="login-submit">
            {{ submitText }}
          </a-button>
        </a-form>
        <div class="login-security"><SafetyCertificateOutlined />账号凭证将通过安全通道传输</div>
      </div>
      <footer class="login-footer"><p>Copyright © 2026 智慧在线打印平台</p><p>用户端与管理端共用同一 Web 平台</p></footer>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, notification } from 'ant-design-vue'
import { BulbOutlined, LockOutlined, MailOutlined, ReloadOutlined, SafetyCertificateOutlined, SkinOutlined, UserOutlined } from '@ant-design/icons-vue'
import { adminApi } from '@admin/api'
import { authApi } from '@client/api'
import { useAuthStore } from '@client/stores/auth'
import { usePreferenceStore } from '@admin/stores/preference'
import logoUrl from '@admin/assets/logo.svg'
import illustrationUrl from '@admin/assets/login/admin-workspace.svg'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const preference = usePreferenceStore()
const portal = ref(route.query.mode === 'admin' ? 'admin' : 'client')
const clientAction = ref('login')
const loading = ref(false)
const captchaLoading = ref(false)
const captchaId = ref('')
const captchaImage = ref('')
const emailSending = ref(false)
const emailCountdown = ref(0)
const remember = ref(false)
const formRef = ref()
const form = reactive({ qq: '', password: '', displayName: '', captchaCode: '', emailCode: '', confirmPassword: '' })
const requiresCaptcha = computed(() => true)
const clientActions = [{ label: '用户登录', value: 'login' }, { label: '注册账号', value: 'register' }]
const qqRules = [{ required: true, message: '请输入QQ号' }, { pattern: /^[1-9]\d{4,10}$/, message: '请输入正确的QQ号（5-11位数字）' }]
const confirmPasswordRules = [{ required: true, message: '请再次输入密码' }, { validator: (rule, value) => !value || value === form.password ? Promise.resolve() : Promise.reject('两次输入的密码不一致'), trigger: 'change' }]
const emailAddress = computed(() => { const qq = form.qq.trim(); return qq ? `${qq}@qq.com` : '' })
const submitText = computed(() => clientAction.value === 'forgot'
  ? '重置密码'
  : portal.value === 'admin'
    ? '登录管理端'
    : ({ register: '注册并进入用户端', login: '登录用户端' }[clientAction.value]))
let countdownTimer

function loadRememberedName() {
  const key = portal.value === 'admin' ? 'remembered_admin_user' : 'remembered_client_user'
  const stored = localStorage.getItem(key) || ''
  form.qq = stored.replace(/@qq\.com$/i, '')
  form.password = ''
  form.confirmPassword = ''
  form.emailCode = ''
  remember.value = Boolean(form.qq)
}

function setPortal(value) {
  portal.value = value
  clientAction.value = 'login'
  router.replace({ path: '/login', query: { ...route.query, mode: value } })
  loadRememberedName()
  loadCaptcha()
}

watch(() => route.query.mode, value => {
  const next = value === 'admin' ? 'admin' : 'client'
  if (portal.value !== next) { portal.value = next; loadRememberedName() }
}, { immediate: true })

async function handleSubmit() {
  loading.value = true
  try {
    if (clientAction.value === 'forgot') await resetPassword()
    else if (portal.value === 'admin') await loginAdmin()
    else await loginClient()
  } catch (error) {
    if (!error.response) message.error(error.message || '登录失败')
    if (requiresCaptcha.value) await loadCaptcha()
  } finally {
    loading.value = false
  }
}

async function resetPassword() {
  await authApi.resetPassword({ email: emailAddress.value, emailCode: form.emailCode, newPassword: form.password })
  message.success('密码已重置，请使用新密码登录')
  clientAction.value = 'login'
  form.password = ''
  form.confirmPassword = ''
  form.emailCode = ''
  await loadCaptcha()
}

async function loginAdmin() {
  const res = await adminApi.login(emailAddress.value, form.password, captchaId.value, form.captchaCode)
  if (res.code !== 200 || !res.data?.token) throw new Error(res.message || '管理员登录失败')
  const admin = res.data.admin
  localStorage.setItem('admin_token', res.data.token)
  localStorage.setItem('admin_token_expires_at', String(Date.now() + res.data.expiresIn * 1000))
  localStorage.setItem('admin_user', admin.username)
  localStorage.setItem('admin_id', String(admin.id))
  localStorage.setItem('admin_role', admin.role)
  rememberName('remembered_admin_user')
  message.success('管理员登录成功')
  await router.replace(String(route.query.redirect || '/dashboard'))
  notification.success({ message: '欢迎回来', description: `${admin.username}，管理工作台已准备就绪。` })
}

async function loginClient() {
  const payload = {
    email: emailAddress.value,
    password: form.password,
    displayName: form.displayName.trim(),
    captchaId: captchaId.value,
    captchaCode: form.captchaCode,
    emailCode: form.emailCode
  }
  const res = clientAction.value === 'register' ? await authApi.register(payload) : await authApi.login(payload)
  if (res?.code !== 200 || !res.data?.token || !res.data?.user) throw new Error(res?.message || '用户登录失败')
  auth.setSession(res.data)
  rememberName('remembered_client_user')
  message.success(clientAction.value === 'register' ? '注册成功' : '用户登录成功')
  await router.replace(String(route.query.redirect || '/client'))
}

async function loadCaptcha() {
  form.captchaCode = ''
  captchaId.value = ''
  captchaImage.value = ''
  if (!requiresCaptcha.value) return
  captchaLoading.value = true
  try {
    const res = await authApi.captcha()
    if (res?.code !== 200 || !res.data?.captchaId || !res.data?.image) {
      throw new Error(res?.message || '验证码加载失败')
    }
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.image
  } catch (error) {
    message.error(error.message || '验证码加载失败')
  } finally {
    captchaLoading.value = false
  }
}

function rememberName(key) {
  if (remember.value) localStorage.setItem(key, emailAddress.value)
  else localStorage.removeItem(key)
}

async function sendEmailCode() {
  if (!qqRules[1].pattern.test(form.qq.trim())) return message.warning('请输入有效的QQ号')
  if (!form.captchaCode || form.captchaCode.length !== 4) return message.warning('请先输入图片验证码')
  emailSending.value = true
  try {
    const purpose = clientAction.value === 'forgot' ? 'reset_password' : 'register'
    await authApi.sendEmailCode({ email: emailAddress.value, purpose, captchaId: captchaId.value, captchaCode: form.captchaCode })
    message.success('验证码已发送，请查看QQ邮箱')
    emailCountdown.value = 60
    clearInterval(countdownTimer)
    countdownTimer = setInterval(() => {
      emailCountdown.value -= 1
      if (emailCountdown.value <= 0) clearInterval(countdownTimer)
    }, 1000)
  } finally {
    emailSending.value = false
  }
}

watch(clientAction, () => { form.confirmPassword = ''; loadCaptcha() })
watch(() => form.password, () => { if (form.confirmPassword) formRef.value?.validateFields(['confirmPassword']).catch(() => {}) })
loadRememberedName()
loadCaptcha()
onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<style scoped lang="less">
.login-container { width: 100%; height: 100vh; display: flex; overflow: hidden; background: #fff; }
.login-preferences { position: fixed; z-index: 20; top: 18px; right: 22px; display: flex; gap: 4px; }
.login-left { position: relative; flex: 1; overflow: hidden; background: #f3f7ff; }
.login-filter { position: absolute; inset: -15%; background: linear-gradient(154deg, rgba(255,255,255,.2) 25%, #d5e6ff 60%, rgba(255,255,255,.35) 100%); filter: blur(48px); }
.login-brand { position: absolute; z-index: 2; top: 34px; left: 42px; display: flex; align-items: center; gap: 10px; color: #1f2937; font-size: 18px; font-weight: 700; }
.login-brand img { width: 34px; height: 34px; }
.login-illustration { position: absolute; z-index: 1; inset: 12% 8% 17%; display: grid; place-items: center; }
.login-illustration img { width: min(570px, 72%); max-height: 78%; object-fit: contain; }
.login-slogan { position: absolute; z-index: 2; right: 0; bottom: 42px; left: 0; text-align: center; }
.login-slogan h2 { margin-bottom: 8px; color: #203c79; font-size: 22px; }
.login-slogan p { color: #6b7fa8; font-size: 14px; }
.login-right { display: flex; width: 38%; min-width: 460px; padding: 52px; flex-direction: column; background: #fff; }
.login-form-wrap { display: flex; width: 100%; max-width: 430px; margin: 0 auto; flex: 1; flex-direction: column; justify-content: center; }
.login-form-title { margin-bottom: 28px; }
.login-form-title h1 { margin-bottom: 10px; color: #1f1f1f; font-size: 30px; font-weight: 600; }
.login-form-title p { color: #8c8c8c; font-size: 14px; }
.login-mode { margin-bottom: 22px; }
.login-options { display: flex; align-items: center; justify-content: space-between; margin: -4px 0 24px; color: #8c8c8c; font-size: 13px; }
.captcha-row { display: grid; grid-template-columns: minmax(0, 1fr) 132px; gap: 10px; }
.captcha-image { display: grid; width: 132px; height: 42px; padding: 0; overflow: hidden; place-items: center; border: 1px solid #d9d9d9; border-radius: 6px; background: #f5f8ff; color: #597ef7; cursor: pointer; }
.captcha-image:hover { border-color: #4096ff; }
.captcha-image:disabled { cursor: wait; opacity: .7; }
.captcha-image img { display: block; width: 132px; height: 42px; object-fit: cover; }
.email-code-row { display: grid; grid-template-columns: minmax(0, 1fr) 120px; gap: 10px; }
.login-submit { height: 42px; font-size: 15px; }
.login-security { display: flex; align-items: center; justify-content: center; gap: 7px; margin-top: 24px; color: #a3a3a3; font-size: 12px; }
.login-footer { color: #a3a3a3; text-align: center; font-size: 12px; line-height: 21px; }
.dark .login-container, .dark .login-right { background: #18181c; }
.dark .login-left { background: #111a2c; }
.dark .login-filter { background: linear-gradient(154deg, rgba(9,15,28,.7) 25%, #192d52 60%, rgba(9,15,28,.85) 100%); }
.dark .login-brand, .dark .login-form-title h1 { color: rgba(255,255,255,.9); }
.dark .login-slogan h2 { color: #b7cdf8; }
.dark .login-slogan p { color: #829bc7; }
@media (max-width: 900px) {
  .login-left { display: none; }
  .login-right { width: 100%; min-width: 0; padding: 54px 24px 28px; }
  .login-form-wrap { max-width: 400px; }
}
</style>
