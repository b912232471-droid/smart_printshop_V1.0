Component({
  properties: {
    title: {
      type: String,
      value: ''
    },
    showDots: {
      type: Boolean,
      value: true
    }
  },
  data: {
    statusBarHeight: 20,
    navBodyHeight: 22,
    navHeight: 42,
    canGoBack: false
  },
  lifetimes: {
    attached() {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const statusBarHeight = info.statusBarHeight || 20;
      const navBodyPx = 48;
      const navHeight = Math.round(statusBarHeight + navBodyPx);
      this.setData({
        statusBarHeight,
        navBodyHeight: navBodyPx,
        navHeight,
        canGoBack: getCurrentPages().length > 1
      });
    }
  },
  methods: {
    goBack() {
      wx.navigateBack({ delta: 1 });
    }
  }
});
