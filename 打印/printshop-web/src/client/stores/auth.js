import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('client_user') || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref(readUser())
  const token = ref(localStorage.getItem('client_token') || '')
  const expiresAt = ref(Number(localStorage.getItem('client_token_expires_at') || 0))
  const isAuthenticated = computed(() => Boolean(token.value && user.value && (!expiresAt.value || Date.now() < expiresAt.value)))

  function setSession(data) {
    user.value = data.user
    token.value = data.token
    expiresAt.value = Date.now() + Number(data.expiresIn || 0) * 1000
    localStorage.setItem('client_user', JSON.stringify(user.value))
    localStorage.setItem('client_token', token.value)
    localStorage.setItem('client_token_expires_at', String(expiresAt.value))
  }

  function updateUser(nextUser) {
    user.value = nextUser
    localStorage.setItem('client_user', JSON.stringify(nextUser))
  }

  function clearSession() {
    user.value = null
    token.value = ''
    expiresAt.value = 0
    localStorage.removeItem('client_user')
    localStorage.removeItem('client_token')
    localStorage.removeItem('client_token_expires_at')
  }

  return { user, token, expiresAt, isAuthenticated, setSession, updateUser, clearSession }
})
