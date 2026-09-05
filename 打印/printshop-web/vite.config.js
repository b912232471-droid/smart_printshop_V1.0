import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import http from 'node:http'

const gatewayAgent = new http.Agent({ keepAlive: true })

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src/admin'),
      '@admin': path.resolve(__dirname, 'src/admin'),
      '@client': path.resolve(__dirname, 'src/client'),
      '@web': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
        agent: gatewayAgent
      }
    }
  }
})
