<template>
  <a-config-provider :theme="themeConfig" :locale="antLocale" :auto-insert-space-in-button="false">
    <router-view />
  </a-config-provider>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { theme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import { usePreferenceStore } from '@admin/stores/preference'
import { installLanguageObserver } from '@admin/lang'

const route = useRoute()
const preference = usePreferenceStore()
const antLocale = computed(() => preference.isEnglish ? enUS : zhCN)
const themeConfig = computed(() => ({
  algorithm: preference.isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
  token: {
    colorPrimary: '#1677ff',
    borderRadius: 4,
    colorBgLayout: preference.isDark ? '#101014' : '#f5f7fa',
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif"
  }
}))

watch(() => route.path, path => {
  document.body.classList.toggle('client-zone', path.startsWith('/client'))
  document.body.classList.toggle('admin-zone', !path.startsWith('/client') && path !== '/login')
}, { immediate: true })

let stopLanguageObserver = () => {}
onMounted(() => { stopLanguageObserver = installLanguageObserver(document.getElementById('app')) })
onBeforeUnmount(() => stopLanguageObserver())
</script>
