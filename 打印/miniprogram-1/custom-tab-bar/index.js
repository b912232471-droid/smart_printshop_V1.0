Component({
  data: {
    selected: 0,
    list: [
      { pagePath: "pages/index/index", text: "打印", icon: "/images/home.png", activeIcon: "/images/home-active.png", badge: 0 },
      { pagePath: "pages/location/location", text: "网点", icon: "/images/order.png", activeIcon: "/images/order-active.png", badge: 0 },
      { pagePath: "pages/orders/orders", text: "订单", icon: "/images/file-icon.png", activeIcon: "/images/print-icon.png", badge: 0 },
      { pagePath: "pages/profile/profile", text: "我的", icon: "/images/profile.png", activeIcon: "/images/profile-active.png", badge: 0 }
    ]
  },

  methods: {
    switchTab(e) {
      const index = e.currentTarget.dataset.index;
      const item = this.data.list[index];
      wx.switchTab({ url: '/' + item.pagePath });
    },

    init() {
      const page = getCurrentPages().pop();
      const route = page ? page.route : '';
      const index = this.data.list.findIndex(item => item.pagePath === route);
      this.setData({ selected: index === -1 ? 0 : index });
    }
  },

  lifetimes: {
    attached() {
      this.init();
    }
  }
});
