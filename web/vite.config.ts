import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      /**
       * 开发联调代理（避免 CORS）
       *
       * 约定：前端所有请求都用相对路径 `/api/...`（见 src/api/http.ts 的 baseURL）
       * Vite dev server 会把它转发到后端 `http://localhost:8088`：
       *
       * 浏览器 -> http://localhost:5173/api/...  (前端开发服务器)
       *        -> 代理转发到 http://localhost:8088/api/... (后端)
       *
       * 好处：
       * - 页面里不用写后端域名/端口，环境切换更容易
       * - 避免浏览器跨域限制（CORS）
       */
      '/api': {
        target: 'http://localhost:8088',
        /**
         * changeOrigin:
         * - 让代理请求头里的 Host 看起来像目标站点
         * - 对某些后端/网关的 Host 校验更友好
         */
        changeOrigin: true,
      },
    },
  },
})
