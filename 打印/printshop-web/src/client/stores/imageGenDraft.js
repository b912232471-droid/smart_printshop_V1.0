import { defineStore } from 'pinia'

// AI 生成图转打印的临时载体：ImageGen 页写入，Booking 页读取后清除
export const useImageGenDraftStore = defineStore('imageGenDraft', {
  state: () => ({
    blob: null,
    filename: '',
    genId: null
  }),
  actions: {
    set(blob, filename, genId) {
      this.blob = blob
      this.filename = filename || 'ai-image.png'
      this.genId = genId || null
    },
    clear() {
      this.blob = null
      this.filename = ''
      this.genId = null
    }
  }
})
