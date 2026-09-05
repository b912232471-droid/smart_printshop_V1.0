const { serviceApi, storeApi, orderApi } = require('../../utils/api.js');
const config = require('../../utils/config.js');

Page({
  data: {
    statusBarHeight: 0,  // 状态栏高度
    currentStoreName: '定位中...',  // 当前店铺名称
    services: [],
    categories: [
      { name: '文档', emoji: '📄', desc: 'A4/A3打印装订' },
      { name: '照片', emoji: '📸', desc: '各种尺寸照片' },
      { name: '证件照', emoji: '🪪', desc: '专业证件照拍摄' },
      { name: '复印', emoji: '📋', desc: '快速复印扫描' }
    ],
    queueCount: 0,  // 排队人数
    minWaitTime: 0,  // 最短等待时间（分钟）
    maxWaitTime: 0   // 最长等待时间（分钟）
  },

  onLoad() {
    // 获取状态栏高度
    const systemInfo = wx.getSystemInfoSync();
    this.setData({
      statusBarHeight: systemInfo.statusBarHeight || 20
    });
    
    // 加载默认店铺信息
    this.loadDefaultStore();
    // 加载排队信息
    this.loadQueueInfo();
  },

  onShow() {
    // 更新自定义TabBar
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 0
      });
    }
    
    // 刷新排队信息
    this.loadQueueInfo();
  },

  // 加载排队信息
  loadQueueInfo() {
    orderApi.getQueueCount()
      .then(count => {
        // 每单平均2分钟，范围在1-3分钟之间
        const minTime = Math.max(count * 1, count > 0 ? 1 : 0);  // 最少1分钟/单
        const maxTime = count * 3;  // 最多3分钟/单
        
        this.setData({
          queueCount: count,
          minWaitTime: minTime,
          maxWaitTime: maxTime
        });
      })
      .catch(err => {
      });
  },

  // 加载服务列表
  loadServices() {
    wx.showLoading({ title: '加载中...' });
    
    serviceApi.getAll()
      .then(res => {
        this.setData({
          services: res
        });
        wx.hideLoading();
      })
      .catch(err => {
        wx.hideLoading();
        
        // 如果是连接错误，延迟重试
        if (err && err.toString().includes('ECONNREFUSED')) {
          setTimeout(() => {
            this.loadServices();
          }, 3000);
        } else {
          wx.showToast({
            title: '加载失败',
            icon: 'none'
          });
        }
      });
  },

  // 跳转到分类服务列表
  goCategory(e) {
    const category = e.currentTarget.dataset.category;
    wx.navigateTo({
      url: `/pages/service-list/service-list?category=${category}`
    });
  },

  // 跳转到预约页面（保留，供其他地方使用）
  goBooking(e) {
    const service = e.currentTarget.dataset.service;
    wx.navigateTo({
      url: `/pages/booking/booking?serviceId=${service.id}&serviceName=${service.name}&price=${service.price}`
    });
  },

  // 加载最近的店铺（基于GPS定位）
  loadDefaultStore() {
    this.setData({
      currentStoreName: '定位中...'
    });

    // 判断是否使用虚拟GPS
    if (config.shouldUseVirtualGPS()) {
      this.findNearestStore(config.DEV_LOCATION.latitude, config.DEV_LOCATION.longitude);
      return;
    }

    // 真机：获取真实GPS位置
    wx.getLocation({
      type: 'gcj02',
      success: (locationRes) => {
        const userLat = locationRes.latitude;
        const userLng = locationRes.longitude;
        this.findNearestStore(userLat, userLng);
      },
      fail: (err) => {
        // 定位失败，使用虚拟坐标作为降级方案
        this.findNearestStore(config.DEV_LOCATION.latitude, config.DEV_LOCATION.longitude);
      }
    });
  },

  // 根据用户位置查找最近店铺
  findNearestStore(userLat, userLng) {
    storeApi.getActive()
      .then(res => {
        if (res && res.length > 0) {
          // 计算每个店铺的距离
          const storesWithDistance = res.map(store => {
            const distance = this.calculateDistance(
              userLat, userLng,
              store.latitude, store.longitude
            );
            return {
              ...store,
              distance: distance
            };
          });

          // 按距离排序，找到最近的店铺
          storesWithDistance.sort((a, b) => a.distance - b.distance);
          const nearestStore = storesWithDistance[0];

          // 显示最近的店铺名称和距离
          let displayName = nearestStore.shortName || nearestStore.name;
          
          // 格式化距离显示
          const distanceText = nearestStore.distance < 1 
            ? `${(nearestStore.distance * 1000).toFixed(0)}m` 
            : `${nearestStore.distance.toFixed(1)}km`;

          // 控制总长度，优先显示距离
          const fullText = `${displayName} 距离${distanceText}`;
          if (fullText.length > 15) {
            displayName = displayName.substring(0, 8) + '...';
          }

        this.setData({
          currentStoreName: `${displayName} 距离${distanceText}`
        });
        
        // 保存最近店铺ID到全局变量（预约时使用）
        const app = getApp();
        app.globalData.nearestStoreId = nearestStore.id;
      } else {
        this.setData({
          currentStoreName: '暂无网点'
        });
      }
      })
      .catch(err => {
        this.setData({
          currentStoreName: '加载失败'
        });
      });
  },

  // 计算两点间距离（单位：公里）- Haversine公式
  calculateDistance(lat1, lng1, lat2, lng2) {
    // 验证输入参数
    if (!config.isValidLocation(lat1, lng1) || !config.isValidLocation(lat2, lng2)) {
      return 0;
    }
    
    const rad = Math.PI / 180;
    const R = 6371; // 地球半径（公里）
    const dLat = (lat2 - lat1) * rad;
    const dLng = (lng2 - lng1) * rad;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * rad) * Math.cos(lat2 * rad) *
              Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const distance = R * c;
    
    // 返回保留2位小数的距离
    return Math.round(distance * 100) / 100;
  },

  // 节流函数：防止频繁触发
  throttle(fn, delay) {
    let timer = null;
    return function(...args) {
      if (timer) return;
      timer = setTimeout(() => {
        fn.apply(this, args);
        timer = null;
      }, delay);
    };
  },

  // 跳转到网点页面
  goLocation() {
    wx.switchTab({
      url: '/pages/location/location'
    });
  },

  // 跳转到订单页
  goOrders() {
    wx.switchTab({
      url: '/pages/orders/orders'
    });
  },

  // 教务课表入口
  goSchedule() {
    wx.navigateTo({
      url: '/pages/schedule/schedule'
    });
  },

  // AI 证件照入口
  goPhoto() {
    wx.navigateTo({
      url: '/pages/photo/photo'
    });
  },

  // 显示退款信息
  showRefund() {
    wx.showModal({
      title: '待取订单退款',
      content: '如需退款，请在订单页面选择对应订单申请退款。\n\n客服电话：400-778-1811',
      showCancel: false
    });
  },

  // 显示优惠券
  showCoupons() {
    wx.showModal({
      title: '优惠券',
      content: '暂无可用优惠券\n\n成为会员即可获得打印优惠券！',
      confirmText: '成为会员',
      success: (res) => {
        if (res.confirm) {
          wx.showToast({
            title: '会员功能开发中',
            icon: 'none'
          });
        }
      }
    });
  },

  // 显示"敬请期待"提示
  showComingSoon() {
    wx.showToast({
      title: '功能开发中，敬请期待 🚀',
      icon: 'none',
      duration: 2000
    });
  }
});
