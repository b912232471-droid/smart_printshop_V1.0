const { userApi, orderApi, requireAuthToken, toPrintApiUrl } = require('../../utils/api.js');
const config = require('../../utils/config.js');
const { maskPhone } = require('../../utils/formatter.js');
const app = getApp();

Page({
  data: {
    userInfo: {},
    orderStats: {
      pending: 0,
      processing: 0,
      ready: 0,
      completed: 0
    }
  },

  onLoad() {
    // 延迟加载，等待登录完成
    setTimeout(() => {
      this.loadUserInfo();
      this.loadOrderStats();
    }, 300);
  },

  onShow() {
    this.loadOrderStats();
    // 更新自定义TabBar
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 3
      });
    }
  },

  // 加载用户信息
  loadUserInfo() {
    const userId = app.globalData.userId;
    
    if (!userId) {
      // 延迟重试
      setTimeout(() => {
        if (app.globalData.userId) {
          this.loadUserInfo();
        }
      }, 500);
      return;
    }
    
    // 从服务器加载最新数据
    userApi.getById(userId)
      .then(res => {
        if (!res || !res.id) {
          return;
        }
        const safeUserInfo = this.publicUserInfo(res);
        
        // 转换头像URL（使用配置的API地址）
        safeUserInfo.avatarUrl = toPrintApiUrl(safeUserInfo.avatarUrl);
        
        // 更新界面
        this.setData({
          userInfo: safeUserInfo
        });
        
        // 更新全局和缓存
        app.globalData.userInfo = safeUserInfo;
        wx.setStorageSync('userInfo', safeUserInfo);
      })
      .catch(err => {
        // 如果加载失败，使用缓存或默认信息
        if (app.globalData.userInfo) {
          this.setData({
            userInfo: app.globalData.userInfo
          });
        } else {
          this.setData({
            userInfo: {
              id: userId,
              username: null,
              phone: null,
              avatarUrl: null
            }
          });
        }
      });
  },
  
  // 选择头像
  onChooseAvatar(e) {
    const { avatarUrl } = e.detail;
    
    // 立即更新界面（先显示临时图片）
    this.setData({
      'userInfo.avatarUrl': avatarUrl
    });
    
    // 上传头像到服务器
    const uploadUrl = config.getUploadBase() + '/api/print/file/avatar';
    const token = requireAuthToken();
    if (!token) {
      return;
    }
    
    wx.showLoading({ title: '上传头像中...' });
    
    wx.uploadFile({
      url: uploadUrl,
      filePath: avatarUrl,
      name: 'file',
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        const data = JSON.parse(res.data);
        
        if (data.code === 200) {
          let serverAvatarUrl = data.avatarUrl;
          
          // 转换为完整URL
          serverAvatarUrl = toPrintApiUrl(serverAvatarUrl);
          
          // 更新为服务器URL
          this.setData({
            'userInfo.avatarUrl': serverAvatarUrl
          });
          
          // 保存到数据库（保存完整URL）
          this.updateUserField('avatarUrl', serverAvatarUrl);
        } else {
          wx.hideLoading();
          wx.showToast({
            title: '头像上传失败',
            icon: 'none'
          });
        }
      },
      fail: (err) => {
        wx.hideLoading();
        wx.showToast({
          title: '头像上传失败',
          icon: 'none'
        });
      }
    });
  },
  
  // 编辑昵称
  editNickname() {
    const currentName = this.data.userInfo.username || '';
    
    wx.showModal({
      title: currentName ? '修改昵称' : '设置昵称',
      editable: true,
      placeholderText: '请输入昵称',
      content: currentName,
      success: (res) => {
        if (res.confirm && res.content) {
          const nickname = res.content.trim();
          
          if (!nickname) {
            wx.showToast({
              title: '昵称不能为空',
              icon: 'none'
            });
            return;
          }
          
          // 如果昵称没变化，不更新
          if (nickname === currentName) {
            return;
          }
          
          // 更新到后端
          this.updateUserField('username', nickname);
        }
      }
    });
  },
  
  // 编辑手机号
  editPhone() {
    const currentPhone = this.data.userInfo.phone || '';
    
    wx.showModal({
      title: currentPhone ? '修改手机号' : '绑定手机号',
      editable: true,
      placeholderText: '请输入手机号',
      content: currentPhone,
      success: (res) => {
        if (res.confirm && res.content) {
          const phone = res.content.trim();
          
          // 验证手机号
          if (/^1[3-9]\d{9}$/.test(phone)) {
            this.updateUserField('phone', phone);
          } else {
            wx.showToast({
              title: '手机号格式不正确',
              icon: 'none'
            });
          }
        }
      }
    });
  },
  
  // 更新用户字段
  updateUserField(field, value) {
    wx.showLoading({ title: '保存中...' });
    
    const { userApi } = require('../../utils/api.js');
    
    // 从data中获取最新数据
    const currentUserInfo = this.data.userInfo || {};
    
    // 保留所有现有字段，只更新指定字段
    const updatedInfo = {
      id: app.globalData.userId,
      username: currentUserInfo.username || null,
      phone: currentUserInfo.phone || null,
      avatarUrl: currentUserInfo.avatarUrl || null,
      [field]: value  // 覆盖要更新的字段
    };

    userApi.update(updatedInfo)
      .then((result) => {
        wx.hideLoading();
        wx.showToast({
          title: '保存成功',
          icon: 'success'
        });
        
        // 更新界面
        this.setData({
          [`userInfo.${field}`]: value
        });
        
        // 更新缓存
        const updatedUserInfo = {
          ...this.data.userInfo,
          [field]: value
        };
        app.globalData.userInfo = updatedUserInfo;
        wx.setStorageSync('userInfo', updatedUserInfo);
      })
      .catch(err => {
        wx.hideLoading();
        wx.showToast({
          title: '保存失败: ' + (err.message || JSON.stringify(err)),
          icon: 'none',
          duration: 3000
        });
      });
  },
  
  // 获取微信手机号（保留但不再使用）
  onGetPhoneNumber(e) {
    if (e.detail.errMsg === 'getPhoneNumber:ok') {
      wx.showModal({
        title: '提示',
        content: '获取手机号需要后端配置。\n\n请点击"手机号"直接输入。',
        showCancel: false
      });
    }
  },
  

  // 加载订单统计
  loadOrderStats() {
    const userId = app.globalData.userId;
    
    Promise.all([
      orderApi.getByStatus(userId, 0), // 待处理
      orderApi.getByStatus(userId, 1), // 打印中
      orderApi.getByStatus(userId, 2), // 待取件
      orderApi.getByStatus(userId, 3)  // 已完成
    ])
      .then(([pending, processing, ready, completed]) => {
        this.setData({
          orderStats: {
            pending: pending.length,
            processing: processing.length,
            ready: ready.length,
            completed: completed.length
          }
        });
      })
      .catch(err => {
      });
  },

  // 跳转到订单页面
  goOrders(e) {
    const tab = e.currentTarget.dataset.tab;
    
    // 将tab索引保存到全局，让订单页面读取
    // tab: 1=待处理, 2=打印中, 3=待取件, 4=已完成
    getApp().globalData.orderFilterTab = parseInt(tab);
    
    wx.switchTab({
      url: '/pages/orders/orders'
    });
  },

  // 查看历史订单
  goHistory() {
    wx.switchTab({
      url: '/pages/orders/orders'
    });
  },

  // 跳转到网点页面
  goLocation() {
    wx.switchTab({
      url: '/pages/location/location'
    });
  },

  // 显示退款
  showRefund() {
    wx.showModal({
      title: '待取订单退款',
      content: '如需退款，请在订单页面选择对应订单申请退款。\n\n退款将在1-3个工作日内原路返回。\n\n如有疑问请拨打客服电话：400-778-1811',
      confirmText: '查看订单',
      success: (res) => {
        if (res.confirm) {
          wx.switchTab({
            url: '/pages/orders/orders'
          });
        }
      }
    });
  },

  // 在线客服
  showOnlineService() {
    wx.navigateTo({
      url: '/pages/chat/chat'
    });
  },

  // 拨打热线
  callHotline() {
    wx.makePhoneCall({
      phoneNumber: '4007781811'
    });
  },

  // 提交反馈
  showFeedback() {
    wx.showModal({
      title: '提点建议完善产品',
      editable: true,
      placeholderText: '请输入您的宝贵意见...',
      content: '',
      success: (res) => {
        if (res.confirm && res.content) {
          wx.showToast({
            title: '感谢您的反馈！',
            icon: 'success'
          });
          // 这里可以添加提交反馈到后端的逻辑
        }
      }
    });
  },

  // 自助打印机推荐
  showSelfService() {
    wx.showModal({
      title: '推荐安装自助打印机',
      content: '百步印社提供自助打印机安装服务\n\n24小时营业，便捷快速\n适合学校、商场、社区等场所\n\n咨询电话：400-778-1811',
      confirmText: '了解更多',
      success: (res) => {
        if (res.confirm) {
          this.callHotline();
        }
      }
    });
  },

  // 显示关于信息
  showAbout() {
    wx.showModal({
      title: '关于我们',
      content: '打印预约系统 v1.0\n\n为您提供便捷的在线打印预约服务，免去现场排队烦恼。\n\n感谢您的使用！',
      showCancel: false
    });
  },

  // 打开管理后台
  openAdminPanel() {
    const config = require('../../utils/config.js');
    const adminUrl = config.getUploadBase();
    
    wx.showModal({
      title: '💻 管理后台',
      content: `管理后台地址：\n${adminUrl}\n\n请使用已分配的管理员账号登录。`,
      confirmText: '复制网址',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          // 复制网址到剪贴板
          wx.setClipboardData({
            data: adminUrl,
            success: () => {
              wx.showToast({
                title: '网址已复制',
                icon: 'success',
                duration: 2000
              });
            }
          });
        }
      }
    });
  },

  // 退出当前平台账号
  logout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新输入账号和密码，订单与课表数据不会被删除。',
      confirmText: '退出',
      confirmColor: '#ef4444',
      success: (res) => {
        if (res.confirm) {
          app.clearAuth();
          wx.reLaunch({ url: '/pages/login/login' });
        }
      }
    });
  },

  publicUserInfo(userInfo) {
    return {
      id: userInfo.id,
      username: userInfo.username || null,
      phone: userInfo.phone || null,
      avatarUrl: userInfo.avatarUrl || null
    };
  }
});
