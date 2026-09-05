const { chatApi, requireAuthToken } = require('../../utils/api.js');

function extractErrorMessage(err, fallback) {
  if (!err) return fallback;
  if (err.data && err.data.message) return err.data.message;
  if (err.message) return err.message;
  return fallback;
}

Page({
  data: {
    sessionId: '',
    inputValue: '',
    sending: false,
    scrollToMessage: '',
    serviceEnabled: true,
    serviceStatusText: '正在连接服务',
    conversations: [],
    quickQuestions: [
      '如何上传文件并下单打印？',
      '证件照怎么换底色？',
      '课表同步失败怎么办？',
      '怎么联系人工客服？'
    ],
    messages: [
      {
        id: 'welcome',
        role: 'assistant',
        text: '你好，我是智慧打印 AI 客服。你可以问我下单、证件照、课表、订单和门店相关问题。'
      }
    ]
  },

  onLoad(options) {
    const token = requireAuthToken();
    if (!token) {
      return;
    }
    const preset = options && options.q ? decodeURIComponent(options.q) : '';
    Promise.all([chatApi.status(), chatApi.conversations()])
      .then(([status, conversations]) => {
        this.setData({
          serviceEnabled: Boolean(status && status.enabled),
          serviceStatusText: status && status.enabled ? (status.mode === 'agent' ? 'Agent 在线' : '知识库客服在线') : '服务暂未开放',
          conversations: conversations || [],
          inputValue: preset
        });
        if (preset && status && status.enabled) this.sendMessage();
      })
      .catch(() => this.setData({ serviceEnabled: false, serviceStatusText: '服务暂不可用' }));
  },

  newConversation() {
    if (this.data.sending) this.stopGeneration();
    chatApi.createConversation({ title: '新会话' }).then(item => {
      this.setData({
        sessionId: item.id,
        conversations: [item].concat(this.data.conversations),
        messages: [{ id: 'welcome-' + Date.now(), role: 'assistant', text: '你好，我是智慧打印 Agent。可以查询订单、门店、服务价格，也可以解答平台使用问题。' }]
      });
    });
  },

  openHistory() {
    if (!this.data.conversations.length) {
      wx.showToast({ title: '暂无历史会话', icon: 'none' });
      return;
    }
    const items = this.data.conversations.slice(0, 6);
    wx.showActionSheet({
      itemList: items.map(item => item.title || '新会话'),
      success: result => {
        const conversation = items[result.tapIndex];
        chatApi.conversationMessages(conversation.id).then(data => {
          this.setData({
            sessionId: conversation.id,
            messages: (data.messages || []).map(item => ({ id: item.id, messageId: item.id, role: item.role, text: item.content, source: item.source, sources: item.sources || [] }))
          });
        });
      }
    });
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value });
  },

  onQuickQuestion(e) {
    const question = e.currentTarget.dataset.question;
    if (!question || this.data.sending) return;
    this.setData({ inputValue: question });
    this.sendMessage();
  },

  sendMessage() {
    const question = (this.data.inputValue || '').trim();
    if (!question) {
      wx.showToast({ title: '请输入问题', icon: 'none' });
      return;
    }
    if (this.data.sending || !this.data.serviceEnabled) return;

    const userMessage = {
      id: 'u-' + Date.now(),
      role: 'user',
      text: question
    };
    const loadingMessage = {
      id: 'a-' + Date.now(),
      role: 'assistant',
      text: '正在整理答案...'
    };
    const messages = this.data.messages.concat([userMessage, loadingMessage]);

    this.setData({
      messages,
      inputValue: '',
      sending: true,
      scrollToMessage: loadingMessage.id
    });

    this.currentRequest = chatApi.askCancelable({
      question,
      sessionId: this.data.sessionId || undefined
    });
    this.currentRequest.promise
      .then(res => {
        const answer = res && res.answer ? res.answer : '暂时没有找到答案，请稍后再试。';
        const updated = this.data.messages.map(item => (
          item.id === loadingMessage.id
            ? {
                ...item,
                text: answer,
                source: res.source || '',
                sources: res.sources || [],
                toolCalls: res.toolCalls || [],
                messageId: res.messageId
              }
            : item
        ));
        this.setData({
          messages: updated,
          sessionId: res.sessionId || this.data.sessionId,
          sending: false,
          scrollToMessage: loadingMessage.id
        });
        this.loadConversations();
      })
      .catch(err => {
        if (err && String(err.errMsg || '').includes('abort')) return;
        const updated = this.data.messages.map(item => (
          item.id === loadingMessage.id
            ? { ...item, text: extractErrorMessage(err, '客服服务暂不可用，请稍后再试。') }
            : item
        ));
        this.setData({
          messages: updated,
          sending: false,
          scrollToMessage: loadingMessage.id
        });
      })
      .finally(() => { this.currentRequest = null; });
  },

  stopGeneration() {
    if (this.currentRequest) this.currentRequest.abort();
    const updated = this.data.messages.map(item => item.text === '正在整理答案...' ? { ...item, text: '已停止生成。' } : item);
    this.setData({ messages: updated, sending: false });
    this.currentRequest = null;
  },

  loadConversations() {
    chatApi.conversations().then(items => this.setData({ conversations: items || [] })).catch(() => {});
  },

  sendFeedback(e) {
    const messageId = Number(e.currentTarget.dataset.id || 0);
    const rating = Number(e.currentTarget.dataset.rating || 0);
    if (!messageId || !rating) return;
    chatApi.feedback({ messageId, rating, comment: '' }).then(() => {
      const updated = this.data.messages.map(item => item.messageId === messageId ? { ...item, feedback: rating } : item);
      this.setData({ messages: updated });
      wx.showToast({ title: '反馈已记录', icon: 'success' });
    });
  },

  callHotline() {
    wx.makePhoneCall({
      phoneNumber: '4007781811'
    });
  }
});
