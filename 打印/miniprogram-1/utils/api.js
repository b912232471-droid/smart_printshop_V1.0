// 小程序直接请求 Gateway，由 Gateway 转发到后端服务。
const config = require('./config.js');

const PRINT_PREFIX = '/print';
const PRINT_API_PREFIX = '/api/print';

function printPath(url) {
  const path = String(url || '');
  return PRINT_PREFIX + (path.startsWith('/') ? path : '/' + path);
}

function printRequest(url, method = 'GET', data = {}) {
  return request(printPath(url), method, data);
}

function toPrintApiPath(value) {
  const path = String(value || '');
  if (!path) return path;
  if (path === '/api/print' || path.startsWith('/api/print/')) return path;
  if (path === '/api') return PRINT_API_PREFIX;
  if (path.startsWith('/api/')) return `${PRINT_API_PREFIX}/${path.slice('/api/'.length)}`;
  if (path === PRINT_PREFIX) return PRINT_API_PREFIX;
  if (path.startsWith(`${PRINT_PREFIX}/`)) return `/api${path}`;
  if (path.startsWith('/')) return `${PRINT_API_PREFIX}${path}`;
  return path;
}

function toPrintApiUrl(value) {
  const raw = String(value || '');
  if (!raw) return raw;
  const absoluteMatch = raw.match(/^https?:\/\/[^/]+(\/.*)$/i);
  if (absoluteMatch) {
    const path = absoluteMatch[1];
    if (path === '/api' || path.startsWith('/api/') || path === PRINT_PREFIX || path.startsWith(`${PRINT_PREFIX}/`)) {
      return config.getUploadBase() + toPrintApiPath(path);
    }
    return raw;
  }
  if (
    raw === '/api' ||
    raw.startsWith('/api/') ||
    raw === PRINT_PREFIX ||
    raw.startsWith(`${PRINT_PREFIX}/`) ||
    raw.startsWith('/public-files/') ||
    raw.startsWith('/file/')
  ) {
    return config.getUploadBase() + toPrintApiPath(raw);
  }
  return raw;
}

function request(url, method = 'GET', data = {}) {
  const apiBase = config.getApiBase();
  const token = getValidToken();
  if (!isPublicRequest(url, method) && !token) {
    return Promise.reject(handleUnauthorized('登录已失效，请重新进入小程序'));
  }
  const header = {
    'content-type': 'application/json'
  };
  if (token) {
    header.Authorization = 'Bearer ' + token;
  }
  
  return new Promise((resolve, reject) => {
    wx.request({
      url: apiBase + url,
      method: method,
      data: data,
      header: header,
      success(res) {
        if (res.statusCode === 200) {
          resolve(res.data)
        } else {
          if (res.statusCode === 401) {
            handleUnauthorized('登录已失效，请重新进入小程序');
          }
          reject(res)
        }
      },
      fail(err) {
        wx.showToast({
          title: '网络请求失败',
          icon: 'none'
        })
        reject(err)
      }
    })
  })
}

function clearAuthCache() {
  wx.removeStorageSync('userId');
  wx.removeStorageSync('openid');
  wx.removeStorageSync('userInfo');
  wx.removeStorageSync('userToken');
  wx.removeStorageSync('userTokenExpiresAt');
}

function handleUnauthorized(message) {
  clearAuthCache();
  wx.showToast({
    title: message,
    icon: 'none'
  });
  const pages = getCurrentPages();
  const currentRoute = pages.length ? pages[pages.length - 1].route : '';
  if (currentRoute !== 'pages/login/login') {
    setTimeout(() => wx.reLaunch({ url: '/pages/login/login' }), 300);
  }
  return { code: 401, message };
}

function getValidToken() {
  const token = wx.getStorageSync('userToken');
  const tokenExpiresAt = Number(wx.getStorageSync('userTokenExpiresAt') || 0);
  if (!token || !tokenExpiresAt || Date.now() >= tokenExpiresAt) {
    return '';
  }
  return token;
}

function requireAuthToken() {
  const token = getValidToken();
  if (!token) {
    handleUnauthorized('登录已失效，请重新进入小程序');
    return '';
  }
  return token;
}

function isPublicRequest(url, method) {
  const normalizedMethod = String(method || 'GET').toUpperCase();
  let path = String(url || '').split('?')[0].replace(/\/+$/, '') || '/';
  if (path === PRINT_PREFIX) {
    path = '/';
  } else if (path.startsWith(`${PRINT_PREFIX}/`)) {
    path = path.slice(PRINT_PREFIX.length) || '/';
  }
  if (normalizedMethod === 'GET' && (path === '/service' || path.startsWith('/service/'))) {
    return true;
  }
  if (normalizedMethod === 'GET' && path === '/store/active') {
    return true;
  }
  if (normalizedMethod === 'POST' && (path === '/auth/login' || path === '/auth/register')) {
    return true;
  }
  return normalizedMethod === 'GET' && path.startsWith('/public-files/');
}

const authApi = {
  login(data) {
    return printRequest('/auth/login', 'POST', data);
  },
  register(data) {
    return printRequest('/auth/register', 'POST', data);
  },
  changePassword(data) {
    return printRequest('/auth/change-password', 'POST', data);
  }
};

// 服务项目相关接口
const serviceApi = {
  getAll() {
    return printRequest('/service/')
  },
  getById(id) {
    return printRequest(`/service/${id}`)
  },
  getByCategory(category) {
    return printRequest(`/service/category/${category}`)
  }
}

// 用户相关接口
const userApi = {
  getById(id) {
    return printRequest(`/user/${id}`)
  },
  update(data) {
    return printRequest('/user/', 'PUT', data)
  }
}

// 订单相关接口
const orderApi = {
  getByUserId(userId) {
    return printRequest(`/order/user/${userId}`)
  },
  getById(id) {
    return printRequest(`/order/${id}`)
  },
  create(data) {
    return printRequest('/order/', 'POST', data)
  },
  getByStatus(userId, status) {
    return printRequest(`/order/user/${userId}/status/${status}`)
  },
  // 更新订单状态
  updateStatus(orderId, status, fetchCode = '') {
    return printRequest(`/order/${orderId}/status?status=${status}&fetchCode=${fetchCode}`, 'POST')
  },
  cancel(orderId) {
    return printRequest(`/order/${orderId}/cancel`, 'POST')
  },
  // 获取排队订单数量
  getQueueCount() {
    return printRequest('/order/queue/count')
  },
  // 删除订单
  deleteOrder(orderId) {
    return printRequest(`/order/${orderId}`, 'DELETE')
  }
}

// 店铺相关接口
const storeApi = {
  getAll() {
    return printRequest('/store/')
  },
  getActive() {
    return printRequest('/store/active')
  },
  getById(id) {
    return printRequest(`/store/${id}`)
  }
}

// 教务课表相关接口
const scheduleApi = {
  bind(data) {
    return request('/schedule/bind', 'POST', data)
  },
  bindStatus() {
    return request('/schedule/bind-status')
  },
  sync(data) {
    return request('/schedule/sync', 'POST', data)
  },
  list(params) {
    return request('/schedule', 'GET', params || {})
  },
  terms() {
    return request('/schedule/terms')
  }
}

// AI 客服相关接口
const chatApi = {
  ask(data) {
    return request('/chat/ask', 'POST', data)
  },
  status() {
    return request('/chat/status')
  },
  conversations() {
    return request('/chat/conversations')
  },
  createConversation(data) {
    return request('/chat/conversations', 'POST', data)
  },
  conversationMessages(id) {
    return request('/chat/conversations/' + encodeURIComponent(id) + '/messages')
  },
  deleteConversation(id) {
    return request('/chat/conversations/' + encodeURIComponent(id), 'DELETE')
  },
  feedback(data) {
    return request('/chat/feedback', 'POST', data)
  },
  askCancelable(data) {
    const token = getValidToken();
    if (!token) return { promise: Promise.reject(handleUnauthorized('登录已失效，请重新进入小程序')), abort() {} };
    let task;
    const promise = new Promise((resolve, reject) => {
      task = wx.request({
        url: config.getApiBase() + '/chat/ask',
        method: 'POST',
        data,
        header: { 'content-type': 'application/json', Authorization: 'Bearer ' + token },
        timeout: 90000,
        success(res) {
          if (res.statusCode === 200) resolve(res.data);
          else {
            if (res.statusCode === 401) handleUnauthorized('登录已失效，请重新进入小程序');
            reject(res);
          }
        },
        fail(err) { reject(err); }
      });
    });
    return { promise, abort() { if (task) task.abort(); } };
  }
}

function uploadPhoto(endpoint, filePath, formData = {}) {
  const uploadUrl = config.getUploadBase() + '/api/photo' + endpoint;
  const token = requireAuthToken();
  if (!token) {
    return Promise.reject({ code: 401, message: '登录已失效，请重新进入小程序' });
  }

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: uploadUrl,
      filePath,
      name: 'image',
      header: { Authorization: 'Bearer ' + token },
      formData,
      success(res) {
        let data = {};
        try {
          data = JSON.parse(res.data || '{}');
        } catch (e) {
          reject({ code: 500, message: '解析服务器响应失败' });
          return;
        }

        if (res.statusCode === 401 || data.code === 401) {
          handleUnauthorized('登录已失效，请重新进入小程序');
          reject(data);
          return;
        }

        if (res.statusCode >= 200 && res.statusCode < 300 && data.code === 0) {
          resolve(data);
          return;
        }

        reject({
          code: data.code || res.statusCode,
          message: data.message || data.detail || '图片处理失败',
          data
        });
      },
      fail(err) {
        wx.showToast({
          title: '网络请求失败',
          icon: 'none'
        });
        reject(err);
      }
    });
  });
}

// AI 证件照相关接口
const photoApi = {
  generateIdPhoto(filePath, options = {}) {
    return uploadPhoto('/generate-id-photo', filePath, {
      size: options.size || '1寸',
      bg_color: options.bgColor || '#FFFFFF',
      enhance: options.enhance === false ? 'false' : 'true'
    });
  },
  changeBackground(filePath, options = {}) {
    return uploadPhoto('/change-background', filePath, {
      bg_color: options.bgColor || '#FFFFFF',
      output_format: options.outputFormat || 'png',
      enhance: options.enhance === false ? 'false' : 'true',
      enhance_level: options.enhanceLevel || 'normal',
      gradient_bg: options.gradientBg === false ? 'false' : 'true',
      smooth_edge: options.smoothEdge === false ? 'false' : 'true'
    });
  }
}

// 文件相关接口
const fileApi = {
  getByOrderId(orderId) {
    return printRequest(`/file/order/${orderId}`)
  },
  // 文件上传同样走 Gateway 统一入口。
  upload(filePath, orderId, originalFileName = '') {
    const uploadUrl = config.getUploadBase() + '/api/print/file/upload';
    const token = requireAuthToken();
    if (!token) {
      return Promise.reject({ code: 401, message: '登录已失效，请重新进入小程序' });
    }
    
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: uploadUrl,
        filePath: filePath,
        name: 'file',
        header: token ? { Authorization: 'Bearer ' + token } : {},
        formData: {
          'orderId': orderId,
          'originalFileName': originalFileName  // 传递原始文件名
        },
        success(res) {
          try {
            const data = JSON.parse(res.data)
            
            if (res.statusCode === 401 || data.code === 401) {
              handleUnauthorized('登录已失效，请重新进入小程序');
              reject(data);
            } else if (data.code === 200) {
              resolve(data)
            } else {
              reject(data)
            }
          } catch (e) {
            reject({ code: 500, message: '解析服务器响应失败' })
          }
        },
        fail(err) {
          reject(err)
        }
      })
    })
  }
}

module.exports = {
  authApi,
  serviceApi,
  userApi,
  orderApi,
  fileApi,
  storeApi,
  scheduleApi,
  chatApi,
  photoApi,
  toPrintApiPath,
  toPrintApiUrl,
  getValidToken,
  requireAuthToken
}
