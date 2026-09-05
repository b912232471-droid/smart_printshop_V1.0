<template>
  <div class="app-layout" :class="{ 'is-collapsed': collapsed }">
    <aside class="app-side" :class="{ 'is-mobile-open': mobileMenuOpen }">
      <div class="app-logo" @click="router.push('/dashboard')">
        <img :src="logoUrl" alt="智慧打印" />
        <span v-show="!collapsed || isMobile">智慧打印管理平台</span>
      </div>

      <Simplebar class="menu-scroll">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          v-model:openKeys="openKeys"
          mode="inline"
          theme="dark"
          :inline-collapsed="collapsed && !isMobile"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </Simplebar>

      <button class="collapse-trigger" type="button" @click="collapsed = !collapsed">
        <MenuUnfoldOutlined v-if="collapsed" />
        <MenuFoldOutlined v-else />
        <span v-if="!collapsed">收起菜单</span>
      </button>
    </aside>

    <div v-if="mobileMenuOpen" class="mobile-mask" @click="mobileMenuOpen = false"></div>

    <section class="layout-main">
      <header class="app-header">
        <div class="header-left">
          <a-button type="text" class="header-action mobile-menu" @click="mobileMenuOpen = true">
            <MenuOutlined />
          </a-button>
          <a-button type="text" class="header-action desktop-collapse" @click="collapsed = !collapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </a-button>
          <a-breadcrumb class="breadcrumb">
            <a-breadcrumb-item>首页</a-breadcrumb-item>
            <a-breadcrumb-item>{{ pageTitle }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>

        <div class="header-right">
          <a-tooltip :title="preferenceStore.isDark ? '浅色模式' : '深色模式'">
            <a-button type="text" class="header-action" @click="preferenceStore.toggleThemeWithAnimation">
              <BulbOutlined v-if="preferenceStore.isDark" />
              <SkinOutlined v-else />
            </a-button>
          </a-tooltip>
          <a-dropdown trigger="click" placement="bottomRight">
            <a-tooltip title="语言">
              <a-button type="text" class="header-action"><TranslationOutlined /></a-button>
            </a-tooltip>
            <template #overlay>
              <a-menu :selected-keys="[preferenceStore.language]" @click="handleLanguageChange">
                <a-menu-item key="zh-cn">简体中文</a-menu-item>
                <a-menu-item key="en">English</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-tooltip title="刷新当前页">
            <a-button type="text" class="header-action" @click="refreshPage"><ReloadOutlined /></a-button>
          </a-tooltip>
          <a-tooltip title="全屏">
            <a-button type="text" class="header-action desktop-only" @click="toggleFullscreen"><FullscreenOutlined /></a-button>
          </a-tooltip>
          <a-dropdown trigger="click">
            <button type="button" class="user-trigger">
              <a-avatar :size="32" class="user-avatar">{{ username.slice(0, 1).toUpperCase() }}</a-avatar>
              <span class="user-info">
                <strong>{{ username }}</strong>
                <small>{{ roleLabel }}</small>
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
        </div>
      </header>

      <div class="tabbar">
        <button
          v-for="tab in visitedTabs"
          :key="tab.path"
          type="button"
          class="tabbar-item"
          :class="{ active: isTabActive(tab.path) }"
          @click="router.push(tab.path)"
        >
          <span>{{ tab.title }}</span>
          <CloseOutlined v-if="tab.path !== '/dashboard'" class="tab-close" @click.stop="closeTab(tab.path)" />
        </button>
      </div>

      <main class="layout-content">
        <router-view v-if="routerViewVisible" v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :is-mobile="isMobile" />
          </transition>
        </router-view>
      </main>
      <footer class="app-footer">Copyright © 2026 智慧在线打印平台</footer>
    </section>

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
import { computed, h, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import Simplebar from 'simplebar-vue'
import {
  AppstoreOutlined, BulbOutlined, CameraOutlined, CloseOutlined, CommentOutlined,
  DashboardOutlined, DownOutlined, EnvironmentOutlined, FileTextOutlined, FullscreenOutlined,
  HighlightOutlined, LockOutlined, LogoutOutlined, MessageOutlined, MenuFoldOutlined,
  MenuOutlined, MenuUnfoldOutlined, ReloadOutlined, SafetyCertificateOutlined, ScheduleOutlined,
  ShopOutlined, SkinOutlined, TeamOutlined, TranslationOutlined, UserOutlined, FileWordOutlined
} from '@ant-design/icons-vue'
import { adminApi } from '@/api'
import { usePreferenceStore } from '@/stores/preference'
import logoUrl from '@/assets/logo.svg'

const router = useRouter()
const route = useRoute()
const preferenceStore = usePreferenceStore()
const username = ref(localStorage.getItem('admin_user') || 'Admin')
const adminRole = ref(localStorage.getItem('admin_role') || 'admin')
const collapsed = ref(false)
const isMobile = ref(false)
const mobileMenuOpen = ref(false)
const routerViewVisible = ref(true)
const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()
const selectedKeys = ref(['/dashboard'])
const openKeys = ref(['print-business', 'extension-services', 'system-manage'])
const visitedTabs = ref([{ path: '/dashboard', title: '工作台' }])
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const icon = component => () => h(component)
const menuItems = [
  { key: '/dashboard', icon: icon(DashboardOutlined), label: '工作台' },
  {
    key: 'print-business', icon: icon(ShopOutlined), label: '打印业务', children: [
      { key: '/orders', icon: icon(FileTextOutlined), label: '订单管理' },
      { key: '/services', icon: icon(AppstoreOutlined), label: '服务管理' },
      { key: '/stores', icon: icon(EnvironmentOutlined), label: '门店管理' }
    ]
  },
  {
    key: 'extension-services', icon: icon(AppstoreOutlined), label: '拓展功能', children: [
      { key: '/photo', icon: icon(CameraOutlined), label: 'AI 证件照' },
      { key: '/schedule', icon: icon(ScheduleOutlined), label: '课表查询' },
      { key: '/ocr', icon: icon(FileWordOutlined), label: '图片转文档' }
    ]
  },
  { key: '/chat', icon: icon(MessageOutlined), label: '智能客服' },
  {
    key: 'system-manage', icon: icon(SafetyCertificateOutlined), label: '系统管理', children: [
      { key: '/profile', icon: icon(UserOutlined), label: '个人中心' },
      { key: '/users', icon: icon(TeamOutlined), label: '用户管理' },
      { key: '/knowledge', icon: icon(CommentOutlined), label: '客服知识库' },
      { key: '/admins', icon: icon(UserOutlined), label: '管理员管理' }
    ]
  }
]

const pageTitle = computed(() => route.meta.title || '管理后台')
const roleLabel = computed(() => ({ superadmin: '超级管理员', admin: '管理员', operator: '操作员' }[adminRole.value] || '管理员'))

const activePath = () => route.path.startsWith('/orders') ? '/orders' : route.path
const isTabActive = path => path === '/orders' ? route.path.startsWith('/orders') : route.path === path

const handleMenuClick = ({ key }) => {
  if (typeof key === 'string' && key.startsWith('/')) router.push(key)
  mobileMenuOpen.value = false
}

const syncViewport = () => {
  isMobile.value = window.innerWidth <= 900
  if (!isMobile.value) mobileMenuOpen.value = false
}

watch(() => route.fullPath, () => {
  const path = activePath()
  selectedKeys.value = [path]
  const title = path === '/orders' ? '订单管理' : pageTitle.value
  if (!visitedTabs.value.some(tab => tab.path === path)) visitedTabs.value.push({ path, title })
}, { immediate: true })

const closeTab = path => {
  const index = visitedTabs.value.findIndex(tab => tab.path === path)
  if (index < 0) return
  const current = isTabActive(path)
  visitedTabs.value.splice(index, 1)
  if (current) router.push(visitedTabs.value[Math.max(0, index - 1)]?.path || '/dashboard')
}

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
  if (key === 'profile') router.push('/profile')
  else if (key === 'password') {
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    passwordDialogVisible.value = true
  } else if (key === 'logout') handleLogout()
}

const handleLanguageChange = ({ key }) => preferenceStore.setLanguage(key)

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

const clearSession = () => {
  ['admin_token', 'admin_token_expires_at', 'admin_user', 'admin_id', 'admin_role'].forEach(key => localStorage.removeItem(key))
}

const submitPassword = async () => {
  try {
    await passwordFormRef.value?.validate()
    passwordSubmitting.value = true
    await adminApi.changePassword(Number(localStorage.getItem('admin_id')), passwordForm.oldPassword, passwordForm.newPassword)
    message.success('密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    clearSession()
    router.replace({ path: '/login', query: { mode: 'admin' } })
  } finally {
    passwordSubmitting.value = false
  }
}

const handleLogout = () => {
  Modal.confirm({
    title: '退出登录', content: '确定退出当前管理员账号吗？', okText: '退出', cancelText: '取消',
    onOk: () => { clearSession(); router.replace({ path: '/login', query: { mode: 'admin' } }) }
  })
}

onMounted(() => { syncViewport(); window.addEventListener('resize', syncViewport) })
onUnmounted(() => window.removeEventListener('resize', syncViewport))
</script>

<style scoped lang="less">
.app-layout { width: 100%; height: 100vh; color: #262626; background: #f5f7fa; }
.app-side { position: fixed; inset: 0 auto 0 0; z-index: 30; width: 224px; display: flex; flex-direction: column; overflow: hidden; background: #001529; transition: width .2s ease, transform .25s ease; }
.is-collapsed .app-side { width: 64px; }
.app-logo { min-height: 50px; display: flex; align-items: center; gap: 10px; padding: 0 16px; overflow: hidden; color: #fff; background: #001529; cursor: pointer; white-space: nowrap; }
.app-logo img { width: 30px; height: 30px; flex: 0 0 auto; }
.app-logo span { font-size: 16px; font-weight: 700; }
.menu-scroll { flex: 1; }
.menu-scroll :deep(.ant-menu) { border-inline-end: 0; }
.collapse-trigger { height: 44px; display: flex; align-items: center; gap: 12px; padding: 0 24px; border: 0; border-top: 1px solid rgba(255,255,255,.08); color: rgba(255,255,255,.65); background: transparent; cursor: pointer; white-space: nowrap; }
.collapse-trigger:hover { color: #fff; background: rgba(255,255,255,.06); }
.layout-main { height: 100vh; margin-left: 224px; display: flex; flex-direction: column; overflow: hidden; transition: margin-left .2s ease; }
.is-collapsed .layout-main { margin-left: 64px; }
.app-header { height: 50px; flex: 0 0 50px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px 0 8px; border-bottom: 1px solid #f0f0f0; background: #fff; }
.header-left, .header-right { display: flex; align-items: center; }
.header-right { gap: 2px; }
.header-action { width: 38px; height: 38px; padding: 0; color: #595959; font-size: 17px; }
.breadcrumb { margin-left: 8px; }
.user-trigger { height: 44px; display: flex; align-items: center; gap: 9px; margin-left: 4px; padding: 0 5px 0 8px; border: 0; color: #262626; background: transparent; cursor: pointer; }
.user-trigger:hover { background: #f5f5f5; }
.user-avatar { background: #1677ff; }
.user-info { min-width: 72px; display: flex; flex-direction: column; align-items: flex-start; line-height: 16px; }
.user-info strong { max-width: 120px; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.user-info small { color: #8c8c8c; font-size: 11px; }
.user-arrow { color: #8c8c8c; font-size: 11px; }
.tabbar { height: 36px; flex: 0 0 36px; display: flex; align-items: flex-end; gap: 4px; padding: 0 12px; overflow-x: auto; border-bottom: 1px solid #f0f0f0; background: #fff; }
.tabbar-item { height: 30px; min-width: 80px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 0 11px; border: 1px solid #e5e7eb; border-bottom: 0; border-radius: 3px 3px 0 0; color: #595959; background: #fafafa; font-size: 12px; cursor: pointer; white-space: nowrap; }
.tabbar-item.active { border-color: #1677ff; color: #fff; background: #1677ff; }
.tab-close { font-size: 10px; }
.layout-content { flex: 1; min-width: 0; padding: 16px; overflow: auto; }
.app-footer { height: 32px; flex: 0 0 32px; display: grid; place-items: center; color: #bfbfbf; font-size: 12px; }
.mobile-menu, .mobile-mask { display: none; }
.page-fade-enter-active, .page-fade-leave-active { transition: opacity .15s ease; }
.page-fade-enter-from, .page-fade-leave-to { opacity: 0; }

@media (max-width: 900px) {
  .app-side, .is-collapsed .app-side { width: 224px; transform: translateX(-100%); }
  .app-side.is-mobile-open { transform: translateX(0); }
  .layout-main, .is-collapsed .layout-main { margin-left: 0; }
  .mobile-mask { position: fixed; inset: 0; z-index: 20; display: block; background: rgba(0,0,0,.45); }
  .mobile-menu { display: inline-flex; }
  .desktop-collapse, .collapse-trigger, .breadcrumb, .desktop-only, .user-info, .user-arrow { display: none; }
  .layout-content { padding: 12px; }
}
</style>
