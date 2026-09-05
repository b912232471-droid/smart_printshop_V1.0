const { orderApi, fileApi, storeApi, requireAuthToken } = require('../../utils/api.js');

Page({
  data: {
    serviceId: 0,
    serviceName: '',
    price: 0,
    count: 1,
    pageCount: 1,
    fileName: '',
    filePath: '',
    appointDate: '',
    appointTime: '',
    today: '',
    totalPrice: 0,
    duplexOptions: [
      { label: '单面', value: 0 },
      { label: '双面', value: 1 }
    ],
    selectedDuplexIndex: 0,
    selectedDuplexLabel: '单面',
    colorOptions: [
      { label: '黑白', value: 'BLACK_WHITE' },
      { label: '彩色', value: 'COLOR' }
    ],
    selectedColorIndex: 0,
    selectedColorLabel: '黑白',
    paperOptions: [
      { label: 'A4', value: 'A4' },
      { label: 'A3', value: 'A3' },
      { label: '6寸照片', value: 'PHOTO_6IN' },
      { label: '证件照', value: 'ID_PHOTO' }
    ],
    selectedPaperIndex: 0,
    selectedPaperLabel: 'A4',
    stores: [],              // 店铺列表
    selectedStoreIndex: 0,   // 选中的店铺索引
    selectedStore: {}        // 选中的店铺对象
  },

  onLoad(options) {
    // 获取服务信息
    const { serviceId, serviceName, price } = options;
    const today = this.formatDate(new Date());
    const parsedPrice = parseFloat(price);
    const defaults = this.getDefaultPrintSettings(serviceName);
    
    this.setData({
      serviceId: parseInt(serviceId),
      serviceName: serviceName,
      price: parsedPrice,
      today: today,
      selectedDuplexIndex: defaults.duplexIndex,
      selectedDuplexLabel: this.data.duplexOptions[defaults.duplexIndex].label,
      selectedColorIndex: defaults.colorIndex,
      selectedColorLabel: this.data.colorOptions[defaults.colorIndex].label,
      selectedPaperIndex: defaults.paperIndex,
      selectedPaperLabel: this.data.paperOptions[defaults.paperIndex].label,
      totalPrice: this.calculatePrice(parsedPrice, 1, 1)
    });
    
    // 加载店铺列表
    this.loadStores();
  },

  getDefaultPrintSettings(serviceName) {
    const name = serviceName || '';
    const colorIndex = name.includes('彩色') ? 1 : 0;
    let paperIndex = 0;
    if (name.includes('A3')) {
      paperIndex = 1;
    } else if (name.includes('证件照')) {
      paperIndex = 3;
    } else if (name.includes('照片') || name.includes('6寸')) {
      paperIndex = 2;
    }
    return {
      duplexIndex: 0,
      colorIndex,
      paperIndex
    };
  },

  calculatePrice(price, count, pageCount) {
    const amount = Number(price || 0) * Number(count || 1) * Number(pageCount || 1);
    return amount.toFixed(2);
  },

  updateEstimate() {
    this.setData({
      totalPrice: this.calculatePrice(this.data.price, this.data.count, this.data.pageCount)
    });
  },

  // 格式化日期
  formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  },

  // 选择文件
  chooseFile() {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      success: (res) => {
        const file = res.tempFiles[0];
        this.setData({
          fileName: file.name,
          filePath: file.path
        });
        wx.showToast({
          title: '文件选择成功',
          icon: 'success'
        });
      },
      fail: (err) => {
        wx.showToast({
          title: '选择文件失败',
          icon: 'none'
        });
      }
    });
  },

  // 减少份数
  decreaseCount() {
    if (this.data.count > 1) {
      const newCount = this.data.count - 1;
      this.setData({
        count: newCount
      });
      this.updateEstimate();
    }
  },

  // 增加份数
  increaseCount() {
    if (this.data.count >= 99) {
      wx.showToast({
        title: '最多99份',
        icon: 'none'
      });
      return;
    }
    const newCount = this.data.count + 1;
    this.setData({
      count: newCount
    });
    this.updateEstimate();
  },

  decreasePageCount() {
    if (this.data.pageCount > 1) {
      this.setData({
        pageCount: this.data.pageCount - 1
      });
      this.updateEstimate();
    }
  },

  increasePageCount() {
    if (this.data.pageCount >= 500) {
      wx.showToast({
        title: '最多500页',
        icon: 'none'
      });
      return;
    }
    this.setData({
      pageCount: this.data.pageCount + 1
    });
    this.updateEstimate();
  },

  onDuplexChange(e) {
    const index = Number(e.detail.value);
    this.setData({
      selectedDuplexIndex: index,
      selectedDuplexLabel: this.data.duplexOptions[index].label
    });
  },

  onColorChange(e) {
    const index = Number(e.detail.value);
    this.setData({
      selectedColorIndex: index,
      selectedColorLabel: this.data.colorOptions[index].label
    });
  },

  onPaperChange(e) {
    const index = Number(e.detail.value);
    this.setData({
      selectedPaperIndex: index,
      selectedPaperLabel: this.data.paperOptions[index].label
    });
  },

  // 选择日期
  onDateChange(e) {
    this.setData({
      appointDate: e.detail.value
    });
  },

  // 加载店铺列表
  loadStores() {
    storeApi.getActive()
      .then(stores => {
        if (stores && stores.length > 0) {
          // 默认选择第一个店铺
          const defaultIndex = 0;
          this.setData({
            stores: stores,
            selectedStoreIndex: defaultIndex,
            selectedStore: stores[defaultIndex]
          });
        }
      })
      .catch(err => {
        wx.showToast({
          title: '加载门店失败',
          icon: 'none'
        });
      });
  },

  // 选择店铺
  onStoreChange(e) {
    const index = e.detail.value;
    this.setData({
      selectedStoreIndex: index,
      selectedStore: this.data.stores[index]
    });
  },

  // 选择时间
  onTimeChange(e) {
    this.setData({
      appointTime: e.detail.value
    });
  },

  // 提交订单
  submitOrder() {
    // 验证必填项
    if (!this.data.filePath) {
      wx.showToast({
        title: '请上传文件',
        icon: 'none'
      });
      return;
    }

    if (!this.data.appointDate || !this.data.appointTime) {
      wx.showToast({
        title: '请选择预约时间',
        icon: 'none'
      });
      return;
    }

    if (!this.data.selectedStore || !this.data.selectedStore.id) {
      wx.showToast({
        title: '请选择营业门店',
        icon: 'none'
      });
      return;
    }

    if (!requireAuthToken()) {
      return;
    }

    wx.showLoading({ title: '提交中...' });

    // 创建订单（排队号和取件码由后端自动生成）
    const orderData = {
      serviceId: this.data.serviceId,
      storeId: this.data.selectedStore.id,
      appointTime: `${this.data.appointDate} ${this.data.appointTime}:00`,
      copies: this.data.count,
      pageCount: this.data.pageCount,
      duplex: this.data.duplexOptions[this.data.selectedDuplexIndex].value,
      colorMode: this.data.colorOptions[this.data.selectedColorIndex].value,
      paperSize: this.data.paperOptions[this.data.selectedPaperIndex].value
    };

    orderApi.create(orderData)
      .then(orderId => {
        // 订单创建成功后上传文件，传递原始文件名
        return fileApi.upload(this.data.filePath, orderId, this.data.fileName);
      })
      .then(fileRes => {
        wx.hideLoading();
        wx.showToast({
          title: '预约成功',
          icon: 'success'
        });
        
        // 跳转到订单列表
        setTimeout(() => {
          wx.switchTab({
            url: '/pages/orders/orders'
          });
        }, 1500);
      })
      .catch(err => {
        wx.hideLoading();
        wx.showToast({
          title: '提交失败：' + (err.message || JSON.stringify(err)),
          icon: 'none',
          duration: 3000
        });
      });
  }
});
