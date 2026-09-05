// 配置文件（前端）
const config = {
  // 环境配置
  // true: 本地开发 Gateway（localhost:8080）
  // false: 生产 Nginx/Gateway 域名
  IS_LOCAL_DEV: true,

  // GPS 定位配置
  // true: 使用模拟 GPS（开发者工具）
  // false: 使用真实 GPS（真机）
  // 'auto': 自动判断，推荐
  DEV_MODE: 'auto',
  DEV_LOCATION: {
    latitude: 22.8240,
    longitude: 108.3662,
    name: '南宁邕江城'
  },
};

// 获取 Gateway API 基础地址
config.getApiBase = function() {
  return 'http://localhost:8080/api';

  // 上线后切换为生产 Gateway：
  // return 'https://www.guangxun.ltd/api';
};

// 获取 Gateway 文件上传地址
config.getUploadBase = function() {
  return 'http://localhost:8080';

  // 上线后切换为生产 Gateway：
  // return 'https://www.guangxun.ltd';
};

// 是否使用虚拟 GPS
config.shouldUseVirtualGPS = function() {
  if (this.DEV_MODE === 'auto') {
    const systemInfo = wx.getSystemInfoSync();
    return systemInfo.platform === 'devtools';
  }
  return this.DEV_MODE === true;
};

// 当前环境信息
config.getEnvInfo = function() {
  const systemInfo = wx.getSystemInfoSync();
  return {
    platform: systemInfo.platform,
    system: systemInfo.system,
    version: systemInfo.version,
    isDevTools: systemInfo.platform === 'devtools',
    networkType: systemInfo.networkType || 'unknown'
  };
};

// 距离格式化
config.formatDistance = function(distance) {
  if (distance < 0.01) return '附近';
  if (distance < 1) return `${(distance * 1000).toFixed(0)}m`;
  if (distance < 10) return `${distance.toFixed(1)}km`;
  return `${distance.toFixed(0)}km`;
};

// 校验经纬度
config.isValidLocation = function(lat, lng) {
  return !isNaN(lat) && !isNaN(lng) &&
         lat !== 0 && lng !== 0 &&
         lat >= -90 && lat <= 90 &&
         lng >= -180 && lng <= 180;
};

module.exports = config;
