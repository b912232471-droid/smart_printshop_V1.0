const config = require('./utils/config.js');

App({
  onLaunch() {
    this.initUser();
  },

  globalData: {
    apiBase: config.getApiBase(),
    userInfo: null,
    userId: null,
    token: null,
    tokenExpiresAt: 0,
    orderFilterTab: null,
    nearestStoreId: null
  },

  initUser() {
    const userId = wx.getStorageSync('userId');
    const userInfo = wx.getStorageSync('userInfo');
    const token = wx.getStorageSync('userToken');
    const tokenExpiresAt = Number(wx.getStorageSync('userTokenExpiresAt') || 0);

    if (userId && token && Date.now() < tokenExpiresAt) {
      const safeUserInfo = this.publicUserInfo(userInfo);
      this.globalData.userId = userId;
      this.globalData.userInfo = safeUserInfo;
      this.globalData.token = token;
      this.globalData.tokenExpiresAt = tokenExpiresAt;
      wx.setStorageSync('userInfo', safeUserInfo);
      return;
    }
    this.clearAuth();
  },

  isAuthenticated() {
    return Boolean(this.globalData.userId && this.globalData.token && Date.now() < this.globalData.tokenExpiresAt);
  },

  setUserInfo(userId, userInfo, token, expiresIn) {
    const tokenExpiresAt = Date.now() + Number(expiresIn || 0) * 1000;
    const safeUserInfo = this.publicUserInfo(userInfo);
    this.globalData.userId = userId;
    this.globalData.userInfo = safeUserInfo;
    this.globalData.token = token;
    this.globalData.tokenExpiresAt = tokenExpiresAt;
    wx.setStorageSync('userId', userId);
    wx.setStorageSync('userInfo', safeUserInfo);
    wx.setStorageSync('userToken', token);
    wx.setStorageSync('userTokenExpiresAt', tokenExpiresAt);
  },

  clearAuth() {
    this.globalData.userId = null;
    this.globalData.userInfo = null;
    this.globalData.token = null;
    this.globalData.tokenExpiresAt = 0;
    wx.removeStorageSync('userId');
    wx.removeStorageSync('openid');
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('userToken');
    wx.removeStorageSync('userTokenExpiresAt');
  },

  publicUserInfo(userInfo) {
    if (!userInfo) return null;
    return {
      id: userInfo.id,
      username: userInfo.username || null,
      phone: userInfo.phone || null,
      avatarUrl: userInfo.avatarUrl || null
    };
  }
});
