import axios from 'axios'
import { message as antMessage } from 'ant-design-vue'
import router from '@web/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 可以在这里添加token
    const token = localStorage.getItem('admin_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '请求失败'
    // 403 是 RBAC 权限不足的预期业务态：仅提示并刷新权限，不清登录态（RBAC 审查 F-06）
    if (status === 403) {
      antMessage.warning('当前账号无权限执行该操作')
      // 权限接口自身的 403 不再触发刷新，避免自触发循环
      if (!error.config?.url?.includes('/auth/permissions')) {
        import('@/stores/permission').then(({ usePermissionStore }) => usePermissionStore().load(true)).catch(() => {})
      }
      return Promise.reject(error)
    }
    if (status === 401) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_token_expires_at')
      localStorage.removeItem('admin_user')
      localStorage.removeItem('admin_id')
      localStorage.removeItem('admin_role')
      import('@/stores/permission').then(({ usePermissionStore }) => usePermissionStore().reset()).catch(() => {})
      if (router.currentRoute.value.path !== '/login') {
        router.replace({ path: '/login', query: { mode: 'admin' } })
      }
    }
    antMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
