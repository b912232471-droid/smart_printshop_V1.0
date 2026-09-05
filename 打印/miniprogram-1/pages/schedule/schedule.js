const { scheduleApi, requireAuthToken } = require('../../utils/api.js');

const WEEKDAY_NAMES = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日'
};

const TERM_OPTIONS = [
  { label: '第一学期', value: '1' },
  { label: '第二学期', value: '2' },
  { label: '第三学期', value: '3' }
];

function defaultSchoolYear() {
  const year = new Date().getFullYear();
  return (year - 1) + '-' + year;
}

function termName(xqm) {
  const option = TERM_OPTIONS.find(item => item.value === String(xqm || ''));
  return option ? option.label : '第' + (xqm || '-') + '学期';
}

function extractErrorMessage(err, fallback) {
  if (!err) return fallback;
  if (err.data && err.data.message) return err.data.message;
  if (err.message) return err.message;
  return fallback;
}

Page({
  data: {
    loadingStatus: true,
    loginExpired: false,
    bound: false,
    editingBind: false,
    account: {
      studentId: ''
    },
    bindForm: {
      studentId: '',
      jwPassword: ''
    },
    termOptions: TERM_OPTIONS,
    termList: [],
    selectedTermIndex: -1,
    xnm: defaultSchoolYear(),
    xqm: '1',
    loadingCourses: false,
    syncing: false,
    courseList: [],
    errorMsg: ''
  },

  onLoad() {
    const token = requireAuthToken();
    if (!token) {
      this.setData({
        loginExpired: true,
        loadingStatus: false
      });
      return;
    }
    this.loadBindStatus();
  },

  loadBindStatus() {
    this.setData({
      loadingStatus: true,
      errorMsg: ''
    });

    scheduleApi.bindStatus()
      .then(res => {
        const bound = !!(res && res.bound);
        const account = {
          studentId: res && res.studentId ? res.studentId : ''
        };
        this.setData({
          bound,
          account,
          bindForm: {
            studentId: account.studentId,
            jwPassword: ''
          },
          editingBind: !bound,
          loadingStatus: false
        });

        if (bound) {
          this.loadTerms();
        }
      })
      .catch(err => {
        this.setData({
          loadingStatus: false,
          errorMsg: extractErrorMessage(err, '绑定状态加载失败')
        });
      });
  },

  onBindInput(e) {
    const field = e.currentTarget.dataset.field;
    if (!field) return;
    this.setData({
      ['bindForm.' + field]: e.detail.value
    });
  },

  toggleBindForm() {
    this.setData({
      editingBind: !this.data.editingBind,
      errorMsg: ''
    });
  },

  handleBind() {
    const form = this.data.bindForm;
    const payload = {
      studentId: (form.studentId || '').trim(),
      jwPassword: form.jwPassword || ''
    };

    if (!payload.studentId || !payload.jwPassword) {
      wx.showToast({
        title: '请完整填写绑定信息',
        icon: 'none'
      });
      return;
    }
    if (payload.jwPassword.length < 6) {
      wx.showToast({
        title: '密码至少 6 位',
        icon: 'none'
      });
      return;
    }

    wx.showLoading({
      title: '保存中...',
      mask: true
    });

    scheduleApi.bind(payload)
      .then(res => {
        wx.hideLoading();
        const account = {
          studentId: res && res.studentId ? res.studentId : payload.studentId
        };
        this.setData({
          bound: true,
          editingBind: false,
          account,
          bindForm: {
            studentId: account.studentId,
            jwPassword: ''
          },
          errorMsg: ''
        });
        wx.showToast({
          title: '绑定成功',
          icon: 'success'
        });
        this.loadTerms();
      })
      .catch(err => {
        wx.hideLoading();
        this.setData({
          errorMsg: extractErrorMessage(err, '绑定失败，请稍后重试')
        });
      });
  },

  loadTerms() {
    scheduleApi.terms()
      .then(res => {
        const terms = (Array.isArray(res) ? res : []).map(item => ({
          ...item,
          key: (item.xnm || '') + '-' + (item.xqm || '')
        }));
        const firstTerm = terms[0];
        this.setData({
          termList: terms,
          selectedTermIndex: firstTerm ? 0 : -1,
          xnm: firstTerm && firstTerm.xnm ? firstTerm.xnm : this.data.xnm,
          xqm: firstTerm && firstTerm.xqm ? firstTerm.xqm : this.data.xqm
        });
        this.loadCourses();
      })
      .catch(err => {
        this.setData({
          errorMsg: extractErrorMessage(err, '学期列表加载失败')
        });
        this.loadCourses();
      });
  },

  loadCourses() {
    if (!this.data.bound) return;

    this.setData({
      loadingCourses: true,
      errorMsg: ''
    });

    const params = {};
    if (this.data.xnm) params.xnm = this.data.xnm;
    if (this.data.xqm) params.xqm = this.data.xqm;

    scheduleApi.list(params)
      .then(res => {
        const list = (Array.isArray(res) ? res : []).map((item, index) => ({
          ...item,
          key: [
            item.xnm,
            item.xqm,
            item.xqj,
            item.jcs,
            item.kcmc,
            index
          ].join('-'),
          weekdayLabel: WEEKDAY_NAMES[item.xqj] || '周' + (item.xqj || '-'),
          termLabel: item.xnm + ' ' + termName(item.xqm),
          placeText: item.cdmc || '地点待定',
          teacherText: item.xm || '教师待定'
        }));
        this.setData({
          courseList: list,
          loadingCourses: false
        });
      })
      .catch(err => {
        this.setData({
          loadingCourses: false,
          errorMsg: extractErrorMessage(err, '课表加载失败')
        });
      });
  },

  onTermTap(e) {
    const index = Number(e.currentTarget.dataset.index);
    const term = this.data.termList[index];
    if (!term) return;
    this.setData({
      selectedTermIndex: index,
      xnm: term.xnm,
      xqm: term.xqm
    });
    this.loadCourses();
  },

  onSchoolYearInput(e) {
    this.setData({
      xnm: e.detail.value,
      selectedTermIndex: -1
    });
  },

  onTermPickerChange(e) {
    const index = Number(e.detail.value);
    const option = this.data.termOptions[index] || this.data.termOptions[0];
    this.setData({
      xqm: option.value,
      selectedTermIndex: -1
    });
  },

  handleQuery() {
    this.loadCourses();
  },

  handleSync() {
    if (!this.data.bound || this.data.syncing) return;

    const payload = {
      xnm: (this.data.xnm || '').trim(),
      xqm: (this.data.xqm || '').trim()
    };

    if (!payload.xnm || !payload.xqm) {
      wx.showToast({
        title: '请填写学年学期',
        icon: 'none'
      });
      return;
    }

    this.setData({
      syncing: true,
      errorMsg: ''
    });

    scheduleApi.sync(payload)
      .then(res => {
        wx.showToast({
          title: '已同步 ' + (res.imported || 0) + ' 门课',
          icon: 'none'
        });
        this.setData({
          syncing: false
        });
        this.loadTerms();
      })
      .catch(err => {
        const message = extractErrorMessage(err, '同步失败，请稍后重试');
        this.setData({
          syncing: false,
          errorMsg: message
        });
        wx.showModal({
          title: '同步未完成',
          content: message,
          showCancel: false
        });
      });
  },

  refreshAll() {
    if (!this.data.bound) {
      this.loadBindStatus();
      return;
    }
    this.loadTerms();
  }
});
