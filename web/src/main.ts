/**
 * 应用入口（装配层）
 *
 * 这一层的职责只有“把应用拼起来”，不放任何业务逻辑：
 * - 注册全局插件：Pinia（状态）、Vue Router（路由）、Element Plus（基础 UI 组件）
 * - 挂载根组件 `App.vue`
 *
 * 调用链（从用户点击到后端）在本项目里大致是：
 * 页面组件（src/pages） -> API 封装（src/api/contracts.ts） -> 统一 axios 实例（src/api/http.ts）
 * -> 请求相对路径 /api/... -> Vite 代理（vite.config.ts） -> 后端 http://localhost:8088
 *
 * 开发时怎么跑：
 * - 前端：在 web/ 下执行 `pnpm dev`（默认 http://localhost:5173）
 * - 后端：确保 Spring Boot 已启动在 http://localhost:8088（见后端 application.yml / 文档）
 * - 若只启动前端不启动后端：页面仍能打开，但调用接口会失败（会走统一错误提示）
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import './style.css'
import App from './App.vue'
import { router } from './router'

const app = createApp(App)

// 全局状态：用于跨页面共享“当前合同 id”等上下文信息。
app.use(createPinia())
// 路由：负责把 URL 映射到 5 个页面组件。
app.use(router)
// UI 组件库：当前主要用于布局容器（`el-container` 等）。
app.use(ElementPlus)

app.mount('#app')
