import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/contracts/import',
  },
  {
    path: '/contracts/import',
    name: 'contracts-import',
    component: () => import('../pages/ContractsImportPage.vue'),
    meta: { title: '合同导入' },
  },
  {
    path: '/contracts/:id/qa',
    name: 'contract-qa',
    component: () => import('../pages/ContractQaPage.vue'),
    meta: { title: '合同问答' },
  },
  {
    path: '/contracts/:id/risk',
    name: 'contract-risk',
    component: () => import('../pages/ContractRiskPage.vue'),
    meta: { title: '风险检查' },
  },
  {
    path: '/contracts/:id/approval-assist',
    name: 'contract-approval-assist',
    component: () => import('../pages/ContractApprovalAssistPage.vue'),
    meta: { title: '审批辅助' },
  },
  {
    path: '/contracts/:id/approval-records',
    name: 'contract-approval-records',
    component: () => import('../pages/ContractApprovalRecordsImportPage.vue'),
    meta: { title: '审批记录导入' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
