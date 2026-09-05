import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import 'simplebar-vue/dist/simplebar.min.css'
import '@admin/assets/main.css'
import '@client/assets/main.css'
import '@web/assets/unified.css'
import App from '@web/App.vue'
import router from '@web/router'

createApp(App).use(createPinia()).use(Antd).use(router).mount('#app')
