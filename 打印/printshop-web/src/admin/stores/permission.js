import { defineStore } from 'pinia'
import { adminApi } from '@/api'

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    roles: [],
    perms: [],
    menus: [],
    loaded: false,
    loadingPromise: null
  }),
  getters: {
    isSuperAdmin: state => state.roles.includes('superadmin'),
    firstMenuPath(state) {
      const pages = state.menus
        .filter(menu => menu.menuType === 'C' && menu.visible === 1 && menu.path)
        .slice()
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0))
      return pages[0]?.path || '/dashboard'
    }
  },
  actions: {
    hasPerm(perm) {
      if (!perm) return true
      if (this.isSuperAdmin) return true
      return this.perms.includes(perm)
    },
    hasMenu(path) {
      return this.menus.some(menu => menu.menuType === 'C' && menu.path === path)
    },
    async load(force = false) {
      if (this.loaded && !force) return
      if (!this.loadingPromise) {
        this.loadingPromise = adminApi
          .getPermissions()
          .then(res => {
            const data = res?.data || {}
            this.roles = data.roles || []
            this.perms = data.perms || []
            this.menus = data.menus || []
            this.loaded = true
          })
          .finally(() => {
            this.loadingPromise = null
          })
      }
      await this.loadingPromise
    },
    reset() {
      this.roles = []
      this.perms = []
      this.menus = []
      this.loaded = false
      this.loadingPromise = null
    }
  }
})
