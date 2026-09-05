const { storeApi, toPrintApiUrl } = require('../../utils/api.js');
const config = require('../../utils/config.js');
const app = getApp();

Page({
  data: {
    // 地图中心位置（默认南宁）
    latitude: 22.8240,
    longitude: 108.3662,
    // 地图缩放级别
    scale: 13,
    // 打印店列表（从后端获取）
    stores: [],
    // 过滤后的店铺列表
    filteredStores: [],
    // 搜索关键词
    searchKeyword: '',
    // 地图标记点
    markers: [],
    // 当前选中的店铺
    selectedStoreId: null,
    // 用户位置
    userLocation: null
  },
  
  // 地图上下文
  mapCtx: null,

  onLoad() {
  },

  onReady() {
    // 页面渲染完成后再初始化地图
    this.setData({
      latitude: 22.8240,
      longitude: 108.3662,
      scale: 13
    }, () => {
      // 创建地图上下文
      this.mapCtx = wx.createMapContext('storeMap', this);
      
      // 延迟加载数据，让地图先完全渲染
      // 真机需要更长的初始化时间
      setTimeout(() => {
        this.loadStores();
        this.getUserLocation();
      }, 400);
    });
  },

  onShow() {
    // 更新自定义TabBar选中状态
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 1
      });
    }
  },

  // 加载店铺列表
  loadStores() {
    storeApi.getActive()
      .then(res => {
        if (!res || !Array.isArray(res) || res.length === 0) {
          return;
        }
        
        // 处理服务字段（逗号分隔转数组）
        const stores = res.map(store => {
          // 处理图片URL
          let imageUrl = store.imageUrl || '/images/store-default.jpg';
          imageUrl = toPrintApiUrl(imageUrl);
          
          // 处理图片URL
          if (imageUrl && imageUrl !== '/images/store-default.jpg') {
            // 如果不是默认图片
            if (!imageUrl.startsWith('http')) {
              // 相对路径转完整URL
              imageUrl = config.getUploadBase() + imageUrl;
            }
          }
          
          return {
            ...store,
            imageUrl: imageUrl,
            services: store.services ? store.services.split(',') : [],
            shortName: store.shortName || store.name || '打印店',
            hours: store.hours || '营业时间未设置',
            phone: store.phone || '暂无电话'
          };
        });
        
        // 设置所有店铺数据
        this.setData({
          stores: stores,
          filteredStores: stores  // 初始时显示所有店铺
        }, () => {
          // 初始化地图标记
          this.initMap();
          
          // 如果已经获取到用户位置，计算距离
          if (this.data.userLocation) {
            this.calculateDistances();
          }
        });
      })
      .catch(err => {
      });
  },

  // 初始化地图标记
  initMap() {
    // 过滤并验证店铺数据
    const validStores = this.data.stores.filter(store => {
      const lat = parseFloat(store.latitude);
      const lng = parseFloat(store.longitude);
      // 检查经纬度是否有效
      return !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0;
    });

    const markers = validStores.map((store, index) => ({
      id: store.id,
      latitude: parseFloat(store.latitude),
      longitude: parseFloat(store.longitude),
      iconPath: '/images/print-icon.png',
      width: 30,
      height: 30,
      callout: {
        content: store.shortName,
        color: '#000',
        fontSize: 12,
        borderRadius: 4,
        borderWidth: 2,
        borderColor: '#000',
        bgColor: '#fbbf24',
        padding: 8,
        display: 'ALWAYS',
        textAlign: 'center'
      }
    }));

    this.setData({
      markers: markers
    });
  },

  // 获取用户位置
  getUserLocation() {
    // 判断是否使用虚拟GPS
    if (config.shouldUseVirtualGPS()) {
      this.setData({
        userLocation: {
          latitude: config.DEV_LOCATION.latitude,
          longitude: config.DEV_LOCATION.longitude
        },
        latitude: config.DEV_LOCATION.latitude,
        longitude: config.DEV_LOCATION.longitude
      });
      // 计算距离
      this.calculateDistances();
      return;
    }

    // 真机：获取真实GPS位置
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        this.setData({
          userLocation: {
            latitude: res.latitude,
            longitude: res.longitude
          },
          latitude: res.latitude,
          longitude: res.longitude
        });
        
        // 计算距离
        this.calculateDistances();
      },
      fail: (err) => {
        // 降级方案：使用配置的虚拟坐标
        this.setData({
          userLocation: {
            latitude: config.DEV_LOCATION.latitude,
            longitude: config.DEV_LOCATION.longitude
          },
          latitude: config.DEV_LOCATION.latitude,
          longitude: config.DEV_LOCATION.longitude
        });
        this.calculateDistances();
      }
    });
  },

  // 计算各店铺距离
  calculateDistances() {
    if (!this.data.userLocation) return;

    const stores = this.data.stores.map(store => {
      const distance = this.getDistance(
        this.data.userLocation.latitude,
        this.data.userLocation.longitude,
        store.latitude,
        store.longitude
      );
      return {
        ...store,
        distance: distance.toFixed(2)
      };
    });

    // 按距离排序
    stores.sort((a, b) => parseFloat(a.distance) - parseFloat(b.distance));

    this.setData({
      stores,
      filteredStores: this.filterStores(stores, this.data.searchKeyword)
    });
  },

  // 搜索输入
  onSearchInput(e) {
    const keyword = e.detail.value;
    this.setData({
      searchKeyword: keyword,
      filteredStores: this.filterStores(this.data.stores, keyword)
    });
  },

  // 搜索确认
  onSearchConfirm(e) {
    const keyword = e.detail.value;
    this.setData({
      filteredStores: this.filterStores(this.data.stores, keyword)
    });
  },

  // 过滤店铺
  filterStores(stores, keyword) {
    if (!keyword || keyword.trim() === '') {
      return stores;
    }
    
    const key = keyword.toLowerCase().trim();
    return stores.filter(store => {
      // 支持多关键词搜索（空格分隔）
      const keywords = key.split(/\s+/);
      return keywords.every(k => {
        return (store.shortName && store.shortName.toLowerCase().includes(k)) ||
               (store.address && store.address.toLowerCase().includes(k)) ||
               (store.name && store.name.toLowerCase().includes(k)) ||
               (store.phone && store.phone.includes(k));
      });
    });
  },

  // 格式化营业时间显示
  formatHours(hours) {
    if (!hours) return '营业时间未设置';
    if (hours.includes('24小时')) return '24小时营业';
    return hours;
  },

  // 检查店铺是否营业中
  isStoreOpen(hours) {
    if (!hours) return true;
    if (hours.includes('24小时')) return true;
    
    const now = new Date();
    const currentHour = now.getHours();
    const currentMinute = now.getMinutes();
    const currentTime = currentHour * 60 + currentMinute;
    
    // 简单判断：解析 "08:00-20:00" 格式
    const match = hours.match(/(\d{2}):(\d{2})-(\d{2}):(\d{2})/);
    if (match) {
      const openTime = parseInt(match[1]) * 60 + parseInt(match[2]);
      const closeTime = parseInt(match[3]) * 60 + parseInt(match[4]);
      return currentTime >= openTime && currentTime <= closeTime;
    }
    
    return true;  // 无法判断时默认营业中
  },

  // 计算两点间距离（单位：公里）
  getDistance(lat1, lng1, lat2, lng2) {
    const rad = Math.PI / 180;
    const R = 6371; // 地球半径（公里）
    const dLat = (lat2 - lat1) * rad;
    const dLng = (lng2 - lng1) * rad;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * rad) * Math.cos(lat2 * rad) *
              Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  },

  // 点击店铺卡片
  selectStore(e) {
    const storeId = e.currentTarget.dataset.id;
    const store = this.data.stores.find(s => s.id === storeId);
    
    if (store) {
      this.setData({
        selectedStoreId: storeId,
        latitude: store.latitude,
        longitude: store.longitude,
        scale: 15
      });

      // 更新marker样式（高亮选中的）
      const markers = this.data.stores.map(s => ({
        id: s.id,
        latitude: s.latitude,
        longitude: s.longitude,
        iconPath: '/images/print-icon.png',
        width: s.id === storeId ? 40 : 30,
        height: s.id === storeId ? 40 : 30,
        callout: {
          content: s.shortName,
          color: '#000',
          fontSize: s.id === storeId ? 14 : 12,
          borderRadius: 4,
          borderWidth: s.id === storeId ? 3 : 2,
          borderColor: '#000',
          bgColor: s.id === storeId ? '#fbbf24' : '#fff',
          padding: s.id === storeId ? 10 : 8,
          display: 'ALWAYS',
          textAlign: 'center'
        }
      }));

      this.setData({ markers });
    }
  },

  // 导航到店铺
  navigateToStore(e) {
    const storeId = e.currentTarget.dataset.id;
    const store = this.data.stores.find(s => s.id === storeId);
    
    if (store) {
      wx.openLocation({
        latitude: store.latitude,
        longitude: store.longitude,
        name: store.shortName,
        address: store.address,
        scale: 15
      });
    }
  },

  // 拨打电话
  callPhone(e) {
    const phone = e.currentTarget.dataset.phone;
    wx.makePhoneCall({
      phoneNumber: phone
    });
  },

  // 地图点击marker
  onMarkerTap(e) {
    const markerId = e.detail.markerId;
    this.selectStore({ currentTarget: { dataset: { id: markerId } } });
  },

  // 地图区域变化
  onRegionChange(e) {
    // 地图区域变化事件
  },

  // 重新定位
  relocate() {
    this.getUserLocation();
  },

  // 图片加载成功
  onImageLoad(e) {
    // 图片加载成功
  },

  // 图片加载失败
  onImageError(e) {
    // 图片加载失败
  },

  // 预览店铺图片
  previewStoreImage(e) {
    const imageUrl = e.currentTarget.dataset.url;
    
    // 如果是默认图片，不预览
    if (!imageUrl || imageUrl === '/images/store-default.jpg') {
      wx.showToast({
        title: '暂无店铺图片',
        icon: 'none'
      });
      return;
    }
    
    // 预览图片
    wx.previewImage({
      urls: [imageUrl],
      current: imageUrl,
      fail: (err) => {
        wx.showToast({
          title: '图片加载失败',
          icon: 'none'
        });
      }
    });
  }
});
