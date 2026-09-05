const { authApi } = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    mode: 'login',
    username: '',
    password: '',
    displayName: '',
    submitting: false
  },

  onLoad() {
    if (app.isAuthenticated()) {
      wx.reLaunch({ url: '/pages/index/index' });
    }
  },

  setMode(e) {
    this.setData({ mode: e.currentTarget.dataset.mode, password: '' });
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value });
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  onDisplayNameInput(e) {
    this.setData({ displayName: e.detail.value });
  },

  submit() {
    if (this.data.submitting) return;
    const username = this.data.username.trim();
    const password = this.data.password;
    if (!/^[A-Za-z0-9_@.-]{3,50}$/.test(username)) {
      wx.showToast({ title: '请输入有效用户名', icon: 'none' });
      return;
    }
    if (password.length < 8 || password.length > 72) {
      wx.showToast({ title: '密码长度须为8到72位', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    const action = this.data.mode === 'register' ? authApi.register : authApi.login;
    action({ username, password, displayName: this.data.displayName.trim() })
      .then((body) => {
        const data = body && body.data;
        if (!body || body.code !== 200 || !data || !data.token || !data.user) {
          throw new Error((body && body.message) || '登录失败');
        }
        app.setUserInfo(data.user.id, data.user, data.token, data.expiresIn);
        wx.reLaunch({ url: '/pages/index/index' });
      })
      .catch((error) => {
        const message = error && error.data && error.data.message
          ? error.data.message
          : (error.message || '登录失败');
        wx.showToast({ title: message, icon: 'none' });
      })
      .finally(() => this.setData({ submitting: false }));
  }
});
