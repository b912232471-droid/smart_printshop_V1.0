const { photoApi, requireAuthToken } = require('../../utils/api.js');

const PHOTO_SIZES = ['1寸', '小1寸', '2寸', '小2寸', '大1寸', '驾照', '签证(美国)', '签证(日本)'];
const ENHANCE_LEVELS = [
  { label: '轻微', value: 'light' },
  { label: '标准', value: 'normal' },
  { label: '增强', value: 'strong' }
];
const COLOR_OPTIONS = [
  { name: '白', value: '#FFFFFF' },
  { name: '蓝', value: '#0000FF' },
  { name: '红', value: '#FF0000' },
  { name: '浅蓝', value: '#87CEEB' },
  { name: '粉', value: '#FFC0CB' },
  { name: '灰', value: '#F3F4F6' }
];

function extractErrorMessage(err, fallback) {
  if (!err) return fallback;
  if (err.message) return err.message;
  if (err.detail) return err.detail;
  if (err.data && err.data.detail) return err.data.detail;
  if (err.data && err.data.message) return err.data.message;
  return fallback;
}

Page({
  data: {
    mode: 'generate',
    modeTabs: [
      { label: '生成证件照', value: 'generate' },
      { label: '换底色', value: 'background' }
    ],
    photoSizes: PHOTO_SIZES,
    sizeIndex: 0,
    colorOptions: COLOR_OPTIONS,
    colorIndex: 0,
    enhanceLevels: ENHANCE_LEVELS,
    enhanceLevelIndex: 1,
    outputFormat: 'png',
    enhance: true,
    gradientBg: true,
    smoothEdge: true,
    sourcePath: '',
    resultPath: '',
    resultInfo: null,
    loading: false,
    errorMsg: ''
  },

  onLoad() {
    const token = requireAuthToken();
    if (!token) {
      this.setData({ errorMsg: '登录已失效，请重新进入小程序' });
    }
  },

  switchMode(e) {
    const mode = e.currentTarget.dataset.mode;
    if (!mode || mode === this.data.mode) return;
    this.setData({
      mode,
      resultPath: '',
      resultInfo: null,
      errorMsg: ''
    });
  },

  chooseImage() {
    wx.chooseImage({
      count: 1,
      sizeType: ['original', 'compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const filePath = res.tempFilePaths && res.tempFilePaths[0];
        if (!filePath) return;
        this.setData({
          sourcePath: filePath,
          resultPath: '',
          resultInfo: null,
          errorMsg: ''
        });
      }
    });
  },

  onSizeChange(e) {
    this.setData({ sizeIndex: Number(e.detail.value) || 0 });
  },

  onEnhanceLevelChange(e) {
    this.setData({ enhanceLevelIndex: Number(e.detail.value) || 1 });
  },

  selectColor(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (Number.isNaN(index)) return;
    this.setData({ colorIndex: index });
  },

  switchOutputFormat(e) {
    const format = e.currentTarget.dataset.format;
    if (!format) return;
    this.setData({ outputFormat: format });
  },

  onToggleEnhance(e) {
    this.setData({ enhance: e.detail.value });
  },

  onToggleGradient(e) {
    this.setData({ gradientBg: e.detail.value });
  },

  onToggleSmooth(e) {
    this.setData({ smoothEdge: e.detail.value });
  },

  handleProcess() {
    if (!this.data.sourcePath) {
      wx.showToast({ title: '请先选择照片', icon: 'none' });
      return;
    }

    this.setData({ loading: true, errorMsg: '', resultPath: '', resultInfo: null });

    const options = {
      size: this.data.photoSizes[this.data.sizeIndex],
      bgColor: this.data.colorOptions[this.data.colorIndex].value,
      outputFormat: this.data.outputFormat,
      enhance: this.data.enhance,
      enhanceLevel: this.data.enhanceLevels[this.data.enhanceLevelIndex].value,
      gradientBg: this.data.gradientBg,
      smoothEdge: this.data.smoothEdge
    };

    const task = this.data.mode === 'generate'
      ? photoApi.generateIdPhoto(this.data.sourcePath, options)
      : photoApi.changeBackground(this.data.sourcePath, options);

    task
      .then(res => this.handleResult(res))
      .catch(err => {
        this.setData({
          loading: false,
          errorMsg: extractErrorMessage(err, '图片处理失败，请稍后重试')
        });
      });
  },

  handleResult(res) {
    const data = res && res.data ? res.data : {};
    if (!data.image) {
      this.setData({
        loading: false,
        errorMsg: '服务未返回图片结果'
      });
      return;
    }

    this.writeBase64Image(data.image, data.format || this.data.outputFormat)
      .then(filePath => {
        this.setData({
          loading: false,
          resultPath: filePath,
          resultInfo: {
            width: data.width,
            height: data.height,
            format: data.format || this.data.outputFormat,
            size: data.size || this.data.photoSizes[this.data.sizeIndex]
          }
        });
        wx.showToast({ title: '处理完成', icon: 'success' });
      })
      .catch(err => {
        this.setData({
          loading: false,
          errorMsg: '结果图片保存失败'
        });
      });
  },

  writeBase64Image(base64Value, format) {
    return new Promise((resolve, reject) => {
      const clean = String(base64Value || '').replace(/^data:image\/\w+;base64,/, '');
      const ext = format === 'jpg' || format === 'jpeg' ? 'jpg' : 'png';
      const filePath = `${wx.env.USER_DATA_PATH}/photo-result-${Date.now()}.${ext}`;
      wx.getFileSystemManager().writeFile({
        filePath,
        data: clean,
        encoding: 'base64',
        success: () => resolve(filePath),
        fail: reject
      });
    });
  },

  previewResult() {
    if (!this.data.resultPath) return;
    wx.previewImage({
      current: this.data.resultPath,
      urls: [this.data.resultPath]
    });
  },

  ensureAlbumPermission() {
    return new Promise((resolve) => {
      wx.getSetting({
        success: (res) => {
          if (res.authSetting['scope.writePhotosAlbum']) {
            resolve(true);
            return;
          }
          wx.authorize({
            scope: 'scope.writePhotosAlbum',
            success: () => resolve(true),
            fail: () => {
              wx.showModal({
                title: '需要相册权限',
                content: '请在设置中允许保存到相册后重试',
                confirmText: '去设置',
                success: (modalRes) => {
                  if (!modalRes.confirm) {
                    resolve(false);
                    return;
                  }
                  wx.openSetting({
                    success: (settingRes) => {
                      resolve(!!settingRes.authSetting['scope.writePhotosAlbum']);
                    },
                    fail: () => resolve(false)
                  });
                },
                fail: () => resolve(false)
              });
            }
          });
        },
        fail: () => resolve(false)
      });
    });
  },

  async saveResult() {
    if (!this.data.resultPath) {
      wx.showToast({ title: '暂无结果可保存', icon: 'none' });
      return;
    }
    const hasPermission = await this.ensureAlbumPermission();
    if (!hasPermission) {
      wx.showToast({ title: '未获得相册权限', icon: 'none' });
      return;
    }
    wx.saveImageToPhotosAlbum({
      filePath: this.data.resultPath,
      success: () => {
        wx.showToast({ title: '已保存到相册', icon: 'success' });
      },
      fail: () => {
        wx.showToast({ title: '保存失败，请检查相册权限', icon: 'none' });
      }
    });
  }
});
