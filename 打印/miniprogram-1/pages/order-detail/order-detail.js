const { orderApi, fileApi, requireAuthToken, toPrintApiUrl } = require('../../utils/api.js');

Page({

  

  data: {
    orderId: 0,
    order: {},
    files: [],
    statusText: ['待处理', '打印中', '待取件', '已完成', '已取消'],
    statusDesc: [
      '您的订单正在等待处理',
      '正在为您打印文件',
      '打印完成，请尽快取件',
      '订单已完成',
      '订单已取消'
    ],
    statusClass: ['status-pending', 'status-processing', 'status-ready', 'status-completed', 'status-cancelled']
  },

  onLoad(options) {
    const orderId = parseInt(options.id);
    this.setData({ orderId });
    this.loadOrderDetail();
  },

  // 加载订单详情
  loadOrderDetail() {
    wx.showLoading({ title: '加载中...' });
    
    Promise.all([
      orderApi.getById(this.data.orderId),
      fileApi.getByOrderId(this.data.orderId)
    ])
      .then(([order, files]) => {
        this.setData({
          order: this.decorateOrder(order || {}),
          files: files
        });
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

  decorateOrder(order) {
    const copies = order.copies || 1;
    const pageCount = order.pageCount || 1;
    const duplexText = order.duplex === 1 ? '双面' : '单面';
    const colorText = this.getColorModeText(order.colorMode);
    const paperText = this.getPaperSizeText(order.paperSize);
    return {
      ...order,
      printSummary: `${paperText} · ${colorText} · ${duplexText} · ${pageCount}页 × ${copies}份`
    };
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

  // 联系客服
  contactService() {
    wx.navigateTo({
      url: '/pages/chat/chat?q=' + encodeURIComponent('我的订单 ' + this.data.orderId + ' 需要咨询')
    });
  },

  // 取消订单
  cancelOrder() {
    wx.showModal({
      title: '确认取消',
      content: '确定要取消这个订单吗？',
      success: (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '取消中...' });
          
          const { orderApi } = require('../../utils/api.js');
          orderApi.cancel(this.data.orderId)
            .then(() => {
              wx.hideLoading();
              wx.showToast({
                title: '订单已取消',
                icon: 'success'
              });
              
              // 刷新订单详情
              setTimeout(() => {
                this.loadOrderDetail();
              }, 1500);
            })
            .catch(err => {
              wx.hideLoading();
              wx.showToast({
                title: '取消失败，请重试',
                icon: 'none'
              });
            });
        }
      }
    });
  },

  // 预览文件
  previewFile(e) {
    const fileId = e.currentTarget.dataset.id;
    const fileName = e.currentTarget.dataset.name;
    const fileUrl = toPrintApiUrl('/api/print/file/download/' + fileId);
    const token = requireAuthToken();
    if (!token) {
      return;
    }
    
    // 获取文件扩展名
    const ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    
    wx.showLoading({ title: '加载中...' });
    
    wx.downloadFile({
      url: fileUrl,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        wx.hideLoading();
        
        if (res.statusCode === 200) {
          const filePath = res.tempFilePath;
          if (['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp'].includes(ext)) {
            wx.previewImage({
              urls: [filePath],
              current: filePath
            });
            return;
          }
          
          // 打开文档
          wx.openDocument({
            filePath: filePath,
            fileType: ext.substring(1), // 去掉点号
            success: () => {},
            fail: (err) => {
              wx.showModal({
                title: '无法预览',
                content: '该文件格式暂不支持预览，请在电脑上查看',
                showCancel: false
              });
            }
          });
        } else {
          wx.showToast({
            title: '下载失败',
            icon: 'none'
          });
        }
      },
      fail: (err) => {
        wx.hideLoading();
        wx.showToast({
          title: '下载失败',
          icon: 'none'
        });
      }
    });
  }
});
