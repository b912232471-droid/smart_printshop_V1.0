<template>
  <div class="client-layout">
    <aside class="client-side">
      <button class="brand" type="button" @click="router.push('/client')">
        <span class="brand-mark"><PrinterOutlined /></span>
        <span><strong>智慧打印</strong><small>在线打印服务平台</small></span>
      </button>
      <nav class="side-nav" aria-label="主导航">
        <button v-for="item in mainNav" :key="item.path" :class="{ active: activePath === item.path }" @click="router.push(item.path)">
          <component :is="item.icon" /><span>{{ item.label }}</span>
        </button>
        <button type="button" class="nav-group-toggle" :class="{ expanded: extensionExpanded }" :aria-expanded="extensionExpanded" @click="extensionExpanded = !extensionExpanded">
          <AppstoreOutlined /><span>拓展功能</span><DownOutlined class="toggle-arrow" />
        </button>
        <div v-show="extensionExpanded" class="nav-group-body">
          <button v-for="item in extensionNav" :key="item.path" :class="{ active: activePath === item.path }" @click="router.push(item.path)">
            <component :is="item.icon" /><span>{{ item.label }}</span>
          </button>
        </div>
      </nav>
      <div class="side-service">
        <CustomerServiceOutlined />
        <div><strong>需要帮助？</strong><span>客服热线 400-778-1811</span></div>
      </div>
    </aside>

    <section class="client-main">
      <header class="client-header">
        <div class="header-title"><span>{{ pageTitle }}</span><small>{{ todayText }}</small></div>
        <div class="header-actions">
          <a-tooltip :title="preference.isDark ? '切换浅色模式' : '切换深色模式'">
            <a-button type="text" class="icon-button" @click="preference.toggleThemeWithAnimation">
              <BulbOutlined v-if="preference.isDark" /><SkinOutlined v-else />
            </a-button>
          </a-tooltip>
          <a-dropdown trigger="click" placement="bottomRight">
            <a-tooltip title="语言">
              <a-button type="text" class="icon-button"><TranslationOutlined /></a-button>
            </a-tooltip>
            <template #overlay>
              <a-menu :selected-keys="[preference.language]" @click="handleLanguageChange">
                <a-menu-item key="zh-cn">简体中文</a-menu-item>
                <a-menu-item key="en">English</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-tooltip title="刷新当前页">
            <a-button type="text" class="icon-button" @click="refreshPage"><ReloadOutlined /></a-button>
          </a-tooltip>
          <a-tooltip title="全屏">
            <a-button type="text" class="icon-button desktop-only" @click="toggleFullscreen"><FullscreenOutlined /></a-button>
          </a-tooltip>
          <a-dropdown v-if="auth.isAuthenticated" trigger="click" placement="bottomRight">
            <button type="button" class="user-trigger">
              <a-avatar :size="32" :src="auth.user?.avatarUrl">{{ (auth.user?.username || 'U').slice(0, 1).toUpperCase() }}</a-avatar>
              <span class="user-info">
                <strong>{{ auth.user?.username || '用户' }}</strong>
                <small>普通用户</small>
              </span>
              <DownOutlined class="user-arrow" />
            </button>
            <template #overlay>
              <a-menu @click="handleUserCommand">
                <a-menu-item key="profile"><UserOutlined />个人中心</a-menu-item>
                <a-menu-item key="password"><LockOutlined />修改密码</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout"><LogoutOutlined />退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-button v-else type="primary" @click="router.push('/login?mode=client')">登录 / 注册</a-button>
        </div>
      </header>

      <main class="client-content">
        <router-view v-if="routerViewVisible" v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
      <footer class="client-footer">智慧打印 · 让文件交付更简单</footer>
    </section>

    <nav class="bottom-nav" aria-label="移动端导航">
      <button v-for="item in mobileNav" :key="item.path" :class="{ active: activePath === item.path }" @click="router.push(item.path)">
        <component :is="item.icon" /><span>{{ item.label }}</span>
      </button>
    </nav>

    <a-modal
      v-model:open="passwordDialogVisible"
      title="修改登录密码"
      :confirm-loading="passwordSubmitting"
      ok-text="保存修改"
      cancel-text="取消"
      @ok="submitPassword"
    >
      <a-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" layout="vertical">
        <a-form-item label="当前密码" name="oldPassword">
          <a-input-password v-model:value="passwordForm.oldPassword" autocomplete="current-password" />
        </a-form-item>
        <a-form-item label="新密码" name="newPassword">
          <a-input-password v-model:value="passwordForm.newPassword" autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="确认新密码" name="confirmPassword">
          <a-input-password v-model:value="passwordForm.confirmPassword" autocomplete="new-password" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  AppstoreOutlined, BulbOutlined, CameraOutlined, CommentOutlined, CustomerServiceOutlined,
  DownOutlined, EnvironmentOutlined, FileTextOutlined, FullscreenOutlined, HomeOutlined,
  LockOutlined, LogoutOutlined, PrinterOutlined, ReloadOutlined, ScheduleOutlined,
  SkinOutlined, ShoppingOutlined, TranslationOutlined, UserOutlined, HighlightOutlined, FileWordOutlined,
  PictureOutlined
} from '@ant-design/icons-vue'
import { authApi } from '@client/api'
import { useAuthStore } from '@client/stores/auth'
import { usePreferenceStore } from '@client/stores/preference'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const preference = usePreferenceStore()
const extensionExpanded = ref(true)
const routerViewVisible = ref(true)
const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const mainNav = [
  { path: '/client', label: '首页', icon: HomeOutlined },
  { path: '/client/services', label: '打印服务', icon: PrinterOutlined },
  { path: '/client/chat', label: '智能客服', icon: CommentOutlined },
  { path: '/client/orders', label: '我的订单', icon: FileTextOutlined },
  { path: '/client/stores', label: '服务门店', icon: EnvironmentOutlined },
  { path: '/client/profile', label: '个人中心', icon: UserOutlined }
]
const extensionNav = [
  { path: '/client/photo', label: 'AI 证件照', icon: CameraOutlined },
  { path: '/client/schedule', label: '课表查询', icon: ScheduleOutlined },
  { path: '/client/ocr', label: '图片转文档', icon: FileWordOutlined },
  { path: '/client/imagegen', label: 'AI 图片生成', icon: PictureOutlined }
]
const navItems = [...mainNav.slice(0, 2), ...extensionNav, ...mainNav.slice(2)]
const mobileNav = [
  navItems[0],
  navItems[1],
  { ...navItems.find(item => item.path === '/client/orders'), icon: ShoppingOutlined },
  navItems.find(item => item.path === '/client/profile')
]
const pageTitle = computed(() => route.meta.title || '智慧打印')
const activePath = computed(() => {
  if (route.path.startsWith('/client/orders')) return '/client/orders'
  if (route.path.startsWith('/client/booking') || route.path === '/client/services') return '/client/services'
  return route.path
})
const todayText = dayjs().format('YYYY年MM月DD日')

const handleLanguageChange = ({ key }) => preference.setLanguage(key)

const refreshPage = async () => {
  routerViewVisible.value = false
  await nextTick()
  routerViewVisible.value = true
}

const toggleFullscreen = async () => {
  if (!document.fullscreenElement) await document.documentElement.requestFullscreen()
  else await document.exitFullscreen()
}

const handleUserCommand = ({ key }) => {
  if (key === 'profile') router.push('/client/profile')
  else if (key === 'password') {
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    passwordDialogVisible.value = true
  } else if (key === 'logout') handleLogout()
}

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

const submitPassword = async () => {
  try {
    await passwordFormRef.value?.validate()
    passwordSubmitting.value = true
    await authApi.changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    message.success('密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    auth.clearSession()
    router.replace({ path: '/login', query: { mode: 'client' } })
  } finally {
    passwordSubmitting.value = false
  }
}

const handleLogout = () => {
  Modal.confirm({
    title: '退出登录',
    content: '确定退出当前账号吗？',
    okText: '退出',
    cancelText: '取消',
    onOk: () => {
      auth.clearSession()
      router.replace({ path: '/login', query: { mode: 'client' } })
    }
  })
}
</script>
