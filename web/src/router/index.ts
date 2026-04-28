import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/**
 * 路由层（URL -> 页面组件）
 *
 * 作用：
 * - 约定 5 个核心页面的访问路径（与 docs/FRONTEND_DEV_GUIDE.md 保持一致）
 * - 负责把“用户在浏览器地址栏/导航点击”转换为“加载哪个页面组件”
 *
 * 这里的路由都使用懒加载（() => import(...)）：
 * - 好处：开发/生产下都可以按需加载页面代码，入口更轻
 * - 代价：首次进入某个页面会多一次模块加载，但对 MVP 阶段很合适
 *
 * 路由 name 的用途：
 * - App.vue 的导航使用 `{ name: '...' }` 跳转，而不是硬编码 path 字符串
 * - 好处：后续即使 path 调整，只要 name 不变，导航与跳转逻辑仍然稳定
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/contracts/import',
  },
  {
    // 合同导入（其他页面的前置条件）
    path: '/contracts/import',
    name: 'contracts-import',
    component: () => import('../pages/ContractsImportPage.vue'),
    meta: { title: '合同导入' },
  },
  {
    // 合同问答（需要 contractId）
    path: '/contracts/:id/qa',
    name: 'contract-qa',
    component: () => import('../pages/ContractQaPage.vue'),
    meta: { title: '合同问答' },
  },
  {
    // 风险检查（需要 contractId）
    path: '/contracts/:id/risk',
    name: 'contract-risk',
    component: () => import('../pages/ContractRiskPage.vue'),
    meta: { title: '风险检查' },
  },
  {
    // 审批辅助（需要 contractId）
    path: '/contracts/:id/approval-assist',
    name: 'contract-approval-assist',
    component: () => import('../pages/ContractApprovalAssistPage.vue'),
    meta: { title: '审批辅助' },
  },
  {
    // 审批记录导入（需要 contractId）
    path: '/contracts/:id/approval-records',
    name: 'contract-approval-records',
    component: () => import('../pages/ContractApprovalRecordsImportPage.vue'),
    meta: { title: '审批记录导入' },
  },
  {
    // 政策制度知识库导入（独立页面，不需要 contractId）
    path: '/policies/import',
    name: 'policies-import',
    component: () => import('../pages/PolicyKnowledgeImportPage.vue'),
    meta: { title: '政策制度导入' },
  },
]

/**
 * Router 实例
 *
 * - createWebHistory(): 使用 history 模式（更像“正常网站 URL”）
 * - routes: 上面定义的路由表
 */
export const router = createRouter({
  history: createWebHistory(),
  routes,
})
