import { computed, nextTick, ref, watch } from 'vue'
import { defineStore } from 'pinia'

export const usePreferenceStore = defineStore('preference', () => {
  const themeMode = ref(localStorage.getItem('admin_theme') || 'light')
  const language = ref(localStorage.getItem('lang') || 'zh-cn')
  const isDark = computed(() => themeMode.value === 'dark')
  const isEnglish = computed(() => language.value === 'en')

  const applyTheme = () => {
    document.documentElement.classList.toggle('dark', isDark.value)
    document.documentElement.classList.toggle('light', !isDark.value)
    document.documentElement.style.colorScheme = isDark.value ? 'dark' : 'light'
  }

  const toggleTheme = () => {
    themeMode.value = isDark.value ? 'light' : 'dark'
  }

  const toggleThemeWithAnimation = event => {
    if (!document.startViewTransition || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      toggleTheme()
      return
    }
    const x = event?.clientX ?? window.innerWidth / 2
    const y = event?.clientY ?? 0
    const endRadius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y))
    const transition = document.startViewTransition(async () => {
      toggleTheme()
      await nextTick()
    })
    transition.ready.then(() => {
      const clipPath = [`circle(0 at ${x}px ${y}px)`, `circle(${endRadius}px at ${x}px ${y}px)`]
      document.documentElement.animate(
        { clipPath: isDark.value ? clipPath : [...clipPath].reverse() },
        { duration: 420, easing: 'ease-in', pseudoElement: isDark.value ? '::view-transition-new(root)' : '::view-transition-old(root)' }
      )
    })
  }

  const setLanguage = value => {
    if (!['zh-cn', 'en'].includes(value) || language.value === value) return
    localStorage.setItem('lang', value)
    window.location.reload()
  }

  watch(themeMode, value => {
    localStorage.setItem('admin_theme', value)
    applyTheme()
  }, { immediate: true })

  return { themeMode, language, isDark, isEnglish, toggleTheme, toggleThemeWithAnimation, setLanguage }
})
