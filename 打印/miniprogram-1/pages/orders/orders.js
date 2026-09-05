const { orderApi } = require('../../utils/api.js');
const { formatPrice, formatDate } = require('../../utils/formatter.js');
const app = getApp();

Page({
  data: {
    tabs: ['全部', '待处理', '打印中', '待取件', '已完成'],
    currentTab: 0,
    orders: [],
    allOrders: [], // 保存所有订单用于搜索
    searchKeyword: '',
    statusText: ['待处理', '打印中', '待取件', '已完成', '已取消'],
    statusClass: ['status-pending', 'status-processing', 'status-ready', 'status-completed', 'status-cancelled']
  },

  onLoad() {
    this.loadOrders();
  },

  onShow() {
    // 检查是否有筛选tab
    const app = getApp();
    if (app.globalData.orderFilterTab) {
      const tab = app.globalData.orderFilterTab;
      // 清除全局状态
      app.globalData.orderFilterTab = null;
      
      // 切换到对应的tab
      this.setData({
        currentTab: tab
      });
    }
    
    this.loadOrders();
    
    // 更新自定义TabBar
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 2
      });
    }
  },

  // 切换标签
  switchTab(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({
      currentTab: index
    });
    this.loadOrders();
  },

  // 加载订单列表
  loadOrders() {
    const userId = app.globalData.userId;
    
    // 如果用户ID不存在，等待登录完成后重试
    if (!userId) {
      setTimeout(() => {
        if (app.globalData.userId) {
          this.loadOrders();
        } else {
          setTimeout(() => {
            if (app.globalData.userId) {
              this.loadOrders();
            }
          }, 500);
        }
      }, 300);
      return;
    }
    
    wx.showLoading({ title: '加载中...' });
    
    const status = this.data.currentTab - 1; // 0表示全部，1-4对应状态0-3

    let apiCall;
    if (this.data.currentTab === 0) {
      // 全部订单
      apiCall = orderApi.getByUserId(userId);
    } else {
      // 按状态筛选
      apiCall = orderApi.getByStatus(userId, status);
    }

    apiCall
      .then(res => {
        const orders = this.decorateOrders(res || []);
        this.setData({
          orders: orders,
          allOrders: orders // 保存原始数据
        });
        // 如果有搜索关键词，执行搜索
        if (this.data.searchKeyword) {
          this.performSearch();
        }
        wx.hideLoading();
      })
      .catch(err => {
        wx.hideLoading();
        wx.showToast({
          title: '加载失败',
          icon: 'none'
        });
      });
  },

  decorateOrders(orders) {
    return orders.map(order => {
      const copies = order.copies || 1;
      const pageCount = order.pageCount || 1;
      const duplexText = order.duplex === 1 ? '双面' : '单面';
      const colorText = this.getColorModeText(order.colorMode);
      const paperText = this.getPaperSizeText(order.paperSize);
      return {
        ...order,
        printSummary: `${paperText} · ${colorText} · ${duplexText} · ${pageCount}页 × ${copies}份`
      };
    });
  },

  getColorModeText(colorMode) {
    const map = {
      BLACK_WHITE: '黑白',
      COLOR: '彩色'
    };
    return map[colorMode] || '黑白';
  },

  getPaperSizeText(paperSize) {
    const map = {
      A4: 'A4',
      A3: 'A3',
      PHOTO_6IN: '6寸照片',
      ID_PHOTO: '证件照'
    };
    return map[paperSize] || 'A4';
  },

  // 搜索输入
  onSearchInput(e) {
    const keyword = e.detail.value;
    this.setData({
      searchKeyword: keyword
    });
    this.performSearch();
  },

  // 执行搜索
  performSearch() {
    const keyword = this.data.searchKeyword.toLowerCase().trim();
    
    if (!keyword) {
      // 没有关键词，显示所有订单
      this.setData({
        orders: this.data.allOrders
      });
      return;
    }

    // 过滤订单
    const filtered = this.data.allOrders.filter(order => {
      const queueNumber = (order.queueNumber || '').toLowerCase();
      const serviceName = (order.serviceName || '').toLowerCase();
      const fetchCode = (order.fetchCode || '').toLowerCase();
      
      return queueNumber.includes(keyword) || 
             serviceName.includes(keyword) ||
             fetchCode.includes(keyword);
    });

    this.setData({
      orders: filtered
    });
  },

  // 清空搜索
  clearSearch() {
    this.setData({
      searchKeyword: '',
      orders: this.data.allOrders
    });
  },

  // 格式化订单状态文本
  getStatusText(status) {
    const statusMap = {
      0: '待处理',
      1: '打印中',
      2: '待取件',
      3: '已完成',
      4: '已取消'
    };
    return statusMap[status] || '未知状态';
  },

  // 获取订单状态样式类
  getStatusClass(status) {
    const classMap = {
      0: 'status-pending',
      1: 'status-processing',
      2: 'status-ready',
      3: 'status-completed',
      4: 'status-cancelled'
    };
    return classMap[status] || 'status-pending';
  },

  // 判断订单是否可以取消
  canCancelOrder(status) {
    return status === 0;  // 只有待处理状态可以取消
  },

  // 跳转到订单详情
  goDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/order-detail/order-detail?id=${id}`
    });
  },

  // 删除订单
  deleteOrder(e) {
    const orderId = e.currentTarget.dataset.id;
    const queueNumber = e.currentTarget.dataset.queue;
    
    wx.showModal({
      title: '确认删除',
      content: `确定要删除订单 ${queueNumber} 吗？\n删除后无法恢复`,
      confirmText: '删除',
      confirmColor: '#ef4444',
      success: (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '删除中...' });
          
          orderApi.deleteOrder(orderId)
            .then(() => {
              wx.hideLoading();
              wx.showToast({
                title: '删除成功',
                icon: 'success'
              });
              
              // 刷新订单列表
              setTimeout(() => {
                this.loadOrders();
              }, 500);
            })
            .catch(err => {
              wx.hideLoading();
              wx.showToast({
                title: '删除失败',
                icon: 'none'
              });
            });
        }
      }
    });
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.loadOrders();
    wx.stopPullDownRefresh();
  }
});
