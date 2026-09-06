import request from '@client/utils/request'

const printPath = path => `/print${path}`

export const authApi = {
  captcha: () => request.get(printPath('/auth/captcha')),
  sendEmailCode: data => request.post(printPath('/auth/email/send'), data),
  login: data => request.post(printPath('/auth/login'), data),
  register: data => request.post(printPath('/auth/register'), data),
  resetPassword: data => request.post(printPath('/auth/password/reset'), data),
  bindEmail: data => request.post(printPath('/auth/email/bind'), data),
  changePassword: data => request.post(printPath('/auth/change-password'), data)
}

export const serviceApi = {
  getAll: () => request.get(printPath('/service/')),
  getByCategory: category => request.get(printPath(`/service/category/${encodeURIComponent(category)}`)),
  getById: id => request.get(printPath(`/service/${id}`))
}

export const storeApi = {
  getActive: () => request.get(printPath('/store/active')),
  getById: id => request.get(printPath(`/store/${id}`))
}

export const orderApi = {
  getByUserId: userId => request.get(printPath(`/order/user/${userId}`)),
  getById: id => request.get(printPath(`/order/${id}`)),
  create: data => request.post(printPath('/order/'), data),
  cancel: id => request.post(printPath(`/order/${id}/cancel`)),
  getQueueCount: () => request.get(printPath('/order/queue/count'))
}

export const fileApi = {
  getByOrderId: orderId => request.get(printPath(`/file/order/${orderId}`)),
  upload(file, orderId) {
    const form = new FormData()
    form.append('file', file)
    form.append('orderId', orderId)
    form.append('originalFileName', file.name)
    return request.post(printPath('/file/upload'), form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
  },
  download: id => request.get(printPath(`/file/download/${id}`), { responseType: 'blob' })
}

export const userApi = {
  getById: id => request.get(printPath(`/user/${id}`)),
  update: data => request.put(printPath('/user/'), data)
}

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

export const scheduleApi = {
  bind: data => request.post('/schedule/bind', data),
  bindStatus: () => request.get('/schedule/bind-status'),
  sync: data => request.post('/schedule/sync', data),
  list: params => request.get('/schedule', { params: params || {} }),
  terms: () => request.get('/schedule/terms')
}

export const chatApi = {
  ask: data => request.post('/chat/ask', data, { timeout: 90000 }),
  status: () => request.get('/chat/status'),
  conversations: () => request.get('/chat/conversations'),
  createConversation: data => request.post('/chat/conversations', data),
  conversationMessages: id => request.get(`/chat/conversations/${id}/messages`),
  deleteConversation: id => request.delete(`/chat/conversations/${id}`),
  feedback: data => request.post('/chat/feedback', data),
  async askStream(data, { signal, onEvent } = {}) {
    const token = localStorage.getItem('client_token')
    const response = await fetch('/api/chat/ask/stream', {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token || ''}` },
      body: JSON.stringify(data)
    })
    if (!response.ok || !response.body) {
      const error = await response.json().catch(() => ({}))
      throw new Error(error.message || `请求失败 (${response.status})`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const event = block.match(/^event:\s*(.+)$/m)?.[1] || 'message'
        const raw = block.match(/^data:\s*(.+)$/m)?.[1] || '{}'
        const payload = JSON.parse(raw)
        onEvent?.(event, payload)
      }
    }
  }
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

export const imageGenApi = {
  models: () => request.get(printPath('/imagegen/models')),
  templates: () => request.get(printPath('/imagegen/templates')),
  generate(data) {
    return request.post(printPath('/imagegen/generate'), data, { timeout: 120000 })
  },
  polish(data) {
    return request.post(printPath('/imagegen/polish'), data, { timeout: 30000 })
  },
  records(page = 1, size = 10) {
    return request.get(printPath('/imagegen/records'), { params: { page, size } })
  },
  deleteRecord: id => request.delete(printPath(`/imagegen/records/${id}`)),
  download(ownerId, token) {
    return request.get(printPath(`/imagegen/download/${ownerId}/${token}`), { responseType: 'blob' })
  },
  bindOrder: (id, orderId) => request.put(printPath(`/imagegen/records/${id}/order`), { orderId })
}
