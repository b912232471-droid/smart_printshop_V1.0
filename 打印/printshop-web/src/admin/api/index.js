import request from '@/utils/request'

const PRINT_PREFIX = '/print'
const PRINT_API_PREFIX = '/api/print'

const printPath = (path) => `${PRINT_PREFIX}${path.startsWith('/') ? path : `/${path}`}`

export const toPrintApiUrl = (url = '') => {
  if (!url) return url
  if (url === '/api/print' || url.startsWith('/api/print/')) return url
  if (url === '/api') return PRINT_API_PREFIX
  if (url.startsWith('/api/')) return `${PRINT_API_PREFIX}/${url.slice('/api/'.length)}`
  if (url.startsWith('/')) return `${PRINT_API_PREFIX}${url}`
  return url
}

// 订单相关
export const orderApi = {
  // 获取所有订单
  getAll() {
    return request.get(printPath('/order/'))
  },
  // 获取订单详情
  getById(id) {
    return request.get(printPath(`/order/${id}`))
  },
  // 更新订单状态
  updateStatus(orderId, status, fetchCode = '') {
    return request.post(printPath(`/order/${orderId}/status`), null, {
      params: { status, fetchCode }
    })
  },
  // 按状态获取订单
  getByStatus(userId, status) {
    return request.get(printPath(`/order/user/${userId}/status/${status}`))
  },
  // 删除订单
  deleteOrder(orderId) {
    return request.delete(printPath(`/order/${orderId}`))
  }
}

// 服务相关
export const serviceApi = {
  getAll() {
    return request.get(printPath('/service/'))
  },
  getById(id) {
    return request.get(printPath(`/service/${id}`))
  },
  create(data) {
    return request.post(printPath('/service/'), data)
  },
  update(data) {
    return request.put(printPath('/service/'), data)
  },
  delete(id) {
    return request.delete(printPath(`/service/${id}`))
  }
}

// 用户相关
export const userApi = {
  getAll() {
    return request.get(printPath('/user/'))
  },
  getById(id) {
    return request.get(printPath(`/user/${id}`))
  },
  update(data) {
    return request.put(printPath('/user/'), data)
  },
  delete(id) {
    return request.delete(printPath(`/user/${id}`))
  }
}

// 文件相关
export const fileApi = {
  getByOrderId(orderId) {
    return request.get(printPath(`/file/order/${orderId}`))
  },
  // 获取文件下载URL（使用专门的下载接口）
  getDownloadUrl(fileUrl, fileId) {
    // 如果有 fileId，使用下载接口
    if (fileId) {
      return `/api/print/file/download/${fileId}`
    }
    // 否则使用静态资源路径（兼容旧数据）
    return toPrintApiUrl(fileUrl)
  },
  download(fileId) {
    return request.get(printPath(`/file/download/${fileId}`), { responseType: 'blob' })
  }
}

// 店铺相关
export const storeApi = {
  getAll() {
    return request.get(printPath('/store/'))
  },
  getById(id) {
    return request.get(printPath(`/store/${id}`))
  },
  getActive() {
    return request.get(printPath('/store/active'))
  },
  create(data) {
    return request.post(printPath('/store/'), data)
  },
  update(data) {
    return request.put(printPath('/store/'), data)
  },
  delete(id) {
    return request.delete(printPath(`/store/${id}`))
  }
}

// 管理员相关
export const adminApi = {
  captcha() {
    return request.get(printPath('/auth/captcha'))
  },
  sendEmailCode(data) {
    return request.post(printPath('/auth/email/send'), data)
  },
  getPermissions() {
    return request.get(printPath('/auth/permissions'))
  },
  login(email, password, captchaId, captchaCode) {
    return request.post(printPath('/admin/login'), { email, password, captchaId, captchaCode })
  },
  register(data) {
    return request.post(printPath('/admin/register'), data)
  },
  getAll() {
    return request.get(printPath('/admin/'))
  },
  getById(id) {
    return request.get(printPath(`/admin/${id}`))
  },
  update(data) {
    return request.put(printPath('/admin/'), data)
  },
  delete(id) {
    return request.delete(printPath(`/admin/${id}`))
  },
  changePassword(id, oldPassword, newPassword) {
    return request.post(printPath('/admin/change-password'), { id, oldPassword, newPassword })
  }
}

// AI 客服知识库 + 智能问答
export const chatApi = {
  ask(data) {
    return request.post('/chat/ask', data, { timeout: 90000 })
  },
  getKnowledge(params = {}) {
    return request.get('/chat/knowledge', { params })
  },
  createKnowledge(data) {
    return request.post('/chat/knowledge/create', data)
  },
  importKnowledge(data) {
    return request.post('/chat/knowledge/import', data, { timeout: 30000 })
  },
  updateKnowledge(id, data) {
    return request.put(`/chat/knowledge/${id}`, data)
  },
  deleteKnowledge(id) {
    return request.delete(`/chat/knowledge/${id}`)
  },
  status() {
    return request.get('/chat/status')
  },
  getSettings() {
    return request.get('/chat/settings')
  },
  updateSettings(data) {
    return request.put('/chat/settings', data)
  },
  overview() {
    return request.get('/chat/admin/overview')
  },
  getDocuments() {
    return request.get('/chat/documents')
  },
  importDocument(data) {
    return request.post('/chat/documents/import', data, { timeout: 120000 })
  },
  getDocumentChunks(id) {
    return request.get(`/chat/documents/${id}/chunks`)
  },
  setDocumentEnabled(id, enabled) {
    return request.post(`/chat/documents/${id}/enabled`, null, { params: { enabled } })
  },
  reindexDocument(id) {
    return request.post(`/chat/documents/${id}/reindex`, null, { timeout: 120000 })
  },
  replaceDocument(id, data) {
    return request.post(`/chat/documents/${id}/replace`, data, { timeout: 120000 })
  },
  testRetrieval(data) {
    return request.post('/chat/documents/retrieval-test', data, { timeout: 30000 })
  },
  deleteDocument(id) {
    return request.delete(`/chat/documents/${id}`)
  }
}

// AI 证件照（photo-service，photo-service 接受任何 Bearer Token）
export const photoApi = {
  generateIdPhoto(file, options = {}) {
    const form = new FormData()
    form.append('image', file)
    form.append('size', options.size || '1寸')
    form.append('bg_color', options.bgColor || '#FFFFFF')
    form.append('enhance', options.enhance === false ? 'false' : 'true')
    return request.post('/photo/generate-id-photo', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
  },
  changeBackground(file, options = {}) {
    const form = new FormData()
    form.append('image', file)
    form.append('bg_color', options.bgColor || '#FFFFFF')
    form.append('output_format', options.outputFormat || 'png')
    form.append('enhance', options.enhance === false ? 'false' : 'true')
    form.append('enhance_level', options.enhanceLevel || 'normal')
    form.append('gradient_bg', options.gradientBg === false ? 'false' : 'true')
    form.append('smooth_edge', options.smoothEdge === false ? 'false' : 'true')
    return request.post('/photo/change-background', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
  }
}

// 教务课表（schedule-service 限 type=user，管理员无法调用，前端会显示提示）
export const scheduleApi = {
  bind: data => request.post('/schedule/bind', data),
  bindStatus: () => request.get('/schedule/bind-status'),
  sync: data => request.post('/schedule/sync', data),
  list: params => request.get('/schedule', { params: params || {} }),
  terms: () => request.get('/schedule/terms')
}

export const ocrApi = {
  convert(file, format = 'both') {
    const form = new FormData()
    form.append('file', file)
    form.append('format', format)
    return request.post('/print/ocr/convert', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  },
  download(ownerId, token, format) {
    return request.get(`/print/ocr/download/${ownerId}/${token}/${format}`, { responseType: 'blob' })
  },
  status() {
    return request.get('/print/ocr/status')
  }
}

// AI 图片生成运营（配置/记录/成本台账，权限点 photo:imagegen:*）
export const imageGenApi = {
  models: () => request.get(printPath('/imagegen/models')),
  config: () => request.get(printPath('/imagegen/config')),
  updateConfig: data => request.put(printPath('/imagegen/config'), data),
  records: params => request.get(printPath('/imagegen/records/all'), { params: params || {} }),
  deleteRecord: id => request.delete(printPath(`/imagegen/records/${id}`)),
  download(ownerId, token) {
    return request.get(printPath(`/imagegen/download/${ownerId}/${token}`), { responseType: 'blob' })
  },
  usage: params => request.get(printPath('/imagegen/usage'), { params: params || {} })
}
