const { serviceApi } = require('../../utils/api.js');

Page({
  data: {
    category: '',
    categoryName: '',
    services: [],
    categoryEmoji: {
      '论文': '📚',
      '照片': '📷',
      '证件照': '🎫',
      '复印': '🖨️'
    }
  },

  onLoad(options) {
    const category = options.category || '论文';
    this.setData({
      category,
      categoryName: category
    });

    wx.setNavigationBarTitle({
      title: category + '服务'
    });

    this.loadServices();
  },

  loadServices() {
    wx.showLoading({ title: '加载中...' });

    serviceApi.getByCategory(this.data.category)
      .then(res => {


        this.setData({ services: res });
        wx.hideLoading();

        if (res.length === 0) {
          wx.showToast({
            title: '该分类暂无服务',
            icon: 'none'
          });
        }
      })
      .catch(err => {
        wx.hideLoading();
        wx.showToast({
          title: '加载失败',
          icon: 'none'
        });
      });
  },

  goBooking(e) {
    const service = e.currentTarget.dataset.service;
    wx.navigateTo({
      url: `/pages/booking/booking?serviceId=${service.id}&serviceName=${service.name}&price=${service.price}`
    });
  },

  onPullDownRefresh() {
    this.loadServices();
    wx.stopPullDownRefresh();
  }
});
