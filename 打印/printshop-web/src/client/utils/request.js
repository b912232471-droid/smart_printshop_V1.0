import axios from 'axios'
import { message } from 'ant-design-vue'

const request = axios.create({ baseURL: '/api', timeout: 15000 })

request.interceptors.request.use(config => {
  const token = localStorage.getItem('client_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  response => response.data,
  error => {
    const status = error.response?.status
    if (status === 401 || status === 403) {
      localStorage.removeItem('client_user')
      localStorage.removeItem('client_token')
      localStorage.removeItem('client_token_expires_at')
      if (!location.pathname.endsWith('/login')) location.assign('/login?mode=client')
    }
    message.error(error.response?.data?.message || error.message || '网络请求失败')
    return Promise.reject(error)
  }
)

export default request
