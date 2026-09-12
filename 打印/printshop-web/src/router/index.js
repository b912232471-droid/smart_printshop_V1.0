import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { translateText } from '@admin/lang'

NProgress.configure({ showSpinner: false })

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'UnifiedLogin', component: () => import('@web/views/UnifiedLogin.vue'), meta: { title: '登录' } },
  {
    path: '/client',
    component: () => import('@client/views/Layout.vue'),
    children: [
      { path: '', name: 'ClientHome', component: () => import('@client/views/Home.vue'), meta: { title: '首页', client: true } },
      { path: 'services', name: 'ClientServices', component: () => import('@client/views/Services.vue'), meta: { title: '打印服务', client: true } },
      { path: 'booking/:serviceId', name: 'ClientBooking', component: () => import('@client/views/Booking.vue'), meta: { title: '预约下单', client: true, clientAuth: true } },
      { path: 'orders', name: 'ClientOrders', component: () => import('@client/views/Orders.vue'), meta: { title: '我的订单', client: true, clientAuth: true } },
      { path: 'orders/:id', name: 'ClientOrderDetail', component: () => import('@client/views/OrderDetail.vue'), meta: { title: '订单详情', client: true, clientAuth: true } },
      { path: 'stores', name: 'ClientStores', component: () => import('@client/views/Stores.vue'), meta: { title: '服务门店', client: true } },
      { path: 'photo', name: 'ClientPhoto', component: () => import('@client/views/Photo.vue'), meta: { title: 'AI 证件照', client: true, clientAuth: true } },
      { path: 'schedule', name: 'ClientSchedule', component: () => import('@client/views/Schedule.vue'), meta: { title: '课表查询', client: true, clientAuth: true } },
      { path: 'ocr', name: 'ClientOcr', component: () => import('@client/views/Ocr.vue'), meta: { title: '图片转文档', client: true, clientAuth: true } },
      { path: 'imagegen', name: 'ClientImageGen', component: () => import('@client/views/ImageGen.vue'), meta: { title: 'AI 图片生成', client: true, clientAuth: true } },
      { path: 'chat', name: 'ClientChat', component: () => import('@client/views/Chat.vue'), meta: { title: '智能客服', client: true, clientAuth: true } },
      { path: 'profile', name: 'ClientProfile', component: () => import('@client/views/Profile.vue'), meta: { title: '个人中心', client: true, clientAuth: true } }
    ]
  },
  {
    path: '/',
    component: () => import('@admin/views/Layout.vue'),
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@admin/views/Dashboard.vue'), meta: { title: '工作台', adminAuth: true, permission: 'print:dashboard:view' } },
      { path: 'orders', name: 'OrderList', component: () => import('@admin/views/OrderList.vue'), meta: { title: '订单管理', adminAuth: true, permission: 'print:order:list' } },
      { path: 'orders/:id', name: 'OrderDetail', component: () => import('@admin/views/OrderDetail.vue'), meta: { title: '订单详情', adminAuth: true, permission: 'print:order:query' } },
      { path: 'services', name: 'ServiceList', component: () => import('@admin/views/ServiceList.vue'), meta: { title: '服务管理', adminAuth: true, permission: 'print:service:list' } },
      { path: 'users', name: 'UserList', component: () => import('@admin/views/UserList.vue'), meta: { title: '账户管理', adminAuth: true, permission: 'print:user:list' } },
      { path: 'roles', name: 'RoleList', component: () => import('@admin/views/RoleList.vue'), meta: { title: '角色管理', adminAuth: true, permission: 'print:role:list' } },
      { path: 'stores', name: 'StoreList', component: () => import('@admin/views/StoreList.vue'), meta: { title: '店铺管理', adminAuth: true, permission: 'print:store:list' } },
      { path: 'knowledge', name: 'KnowledgeBase', component: () => import('@admin/views/KnowledgeBase.vue'), meta: { title: '客服知识库', adminAuth: true, permission: 'chat:knowledge:manage' } },
      { path: 'photo', name: 'AdminPhoto', component: () => import('@admin/views/Photo.vue'), meta: { title: 'AI 证件照', adminAuth: true, permission: 'photo:idphoto:view' } },
      { path: 'schedule', name: 'AdminSchedule', component: () => import('@admin/views/Schedule.vue'), meta: { title: '课表查询', adminAuth: true, permission: 'schedule:query:view' } },
      { path: 'ocr', name: 'AdminOcr', component: () => import('@admin/views/Ocr.vue'), meta: { title: 'OCR 文档转换', adminAuth: true, permission: 'print:ocr:view' } },
      { path: 'chat', name: 'AdminChat', component: () => import('@admin/views/Chat.vue'), meta: { title: '智能客服', adminAuth: true, permission: 'chat:ask:view' } },
      { path: 'imagegen', name: 'ImageGenOps', component: () => import('@admin/views/ImageGenOps.vue'), meta: { title: 'AI 图片生成', adminAuth: true, permission: 'photo:imagegen:view' } },
      { path: 'profile', name: 'AdminProfile', component: () => import('@admin/views/Profile.vue'), meta: { title: '个人中心', adminAuth: true, permission: 'print:profile:view' } },
      { path: 'admins', name: 'AdminList', component: () => import('@admin/views/AdminList.vue'), meta: { title: '管理员管理', adminAuth: true, permission: 'print:admin:list' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({ history: createWebHistory('/'), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach(async to => {
  NProgress.start()
  document.title = translateText(`${to.meta.title || '智慧打印'} - 智慧在线打印平台`)

  if (to.meta.adminAuth) {
    const token = localStorage.getItem('admin_token')
    const expiresAt = Number(localStorage.getItem('admin_token_expires_at') || 0)
    if (!token || (expiresAt && Date.now() >= expiresAt)) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_token_expires_at')
      return { path: '/login', query: { mode: 'admin', redirect: to.fullPath } }
    }
    const { usePermissionStore } = await import('@admin/stores/permission')
    const permissionStore = usePermissionStore()
    try {
      await permissionStore.load()
    } catch (error) {
      return false
    }
    const required = to.meta.permission
    if (required && !permissionStore.hasPerm(required)) {
      const fallback = permissionStore.firstMenuPath
      if (to.path !== fallback) return { path: fallback }
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_token_expires_at')
      permissionStore.reset()
      return { path: '/login', query: { mode: 'admin' } }
    }
  }

  if (to.meta.clientAuth) {
    const token = localStorage.getItem('client_token')
    const expiresAt = Number(localStorage.getItem('client_token_expires_at') || 0)
    if (!token || (expiresAt && Date.now() >= expiresAt)) {
      localStorage.removeItem('client_token')
      localStorage.removeItem('client_token_expires_at')
      localStorage.removeItem('client_user')
      return { path: '/login', query: { mode: 'client', redirect: to.fullPath } }
    }
  }
})

router.afterEach(() => NProgress.done())
router.onError(() => NProgress.done())

export default router
