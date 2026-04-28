<script setup lang="ts">
/**
 * 根组件（应用壳 / Layout）
 *
 * 作用：
 * - 提供全局布局（头部导航 + 主内容区）
 * - 提供 5 个“业务页面”的入口导航（对应 docs/FRONTEND_DEV_GUIDE.md 的 5 个页面）
 * - 展示并共享“当前合同 id”上下文：通过 Pinia store（`contractContext`）在页面之间传递
 *
 * 为什么要有 currentContractId：
 * - 除“合同导入”外，其余接口都要求先有 contractId
 * - 导入成功后把 contractId 写入 store，其他页面默认读 store，减少反复手工填写
 *
 * 页面渲染方式：
 * - `<router-view />` 是路由出口：当前 URL 命中的页面组件会被渲染在这里
 *
 * 组件库说明：
 * - 这里使用了 Element Plus 的布局组件（`el-container / el-header / el-main`）快速搭骨架
 * - 页面本身为了“先跑通真实请求”，大多仍使用原生表单控件（input/textarea/button）
 */
import { computed } from 'vue'
import { useContractContextStore } from './stores/contractContext'

const store = useContractContextStore()

/**
 * 顶部导航定义（展示 label + 路由目标）
 *
 * 注意：
 * - 除导入页外，其余页面路由都需要 params.id（即 contractId）
 * - 这里统一使用 store.currentContractId 作为 params，保持“全局当前合同”的一致性
 */
const navItems = computed(() => [
  { label: '合同导入', to: { name: 'contracts-import' } },
  {
    label: '合同问答',
    to: { name: 'contract-qa', params: { id: store.currentContractId } },
  },
  {
    label: '风险检查',
    to: { name: 'contract-risk', params: { id: store.currentContractId } },
  },
  {
    label: '审批辅助',
    to: {
      name: 'contract-approval-assist',
      params: { id: store.currentContractId },
    },
  },
  {
    label: '审批记录导入',
    to: {
      name: 'contract-approval-records',
      params: { id: store.currentContractId },
    },
  },
])
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="brand">合同 Agent · MVP</div>
      <nav class="nav">
        <!--
          这是最简单的导航实现：router-link + active-class。
          点击后会更新 URL，Vue Router 会把对应页面渲染到 <router-view />。
        -->
        <router-link
          v-for="item in navItems"
          :key="item.label"
          :to="item.to"
          class="nav-link"
          active-class="nav-link--active"
        >
          {{ item.label }}
        </router-link>
      </nav>
      <div class="ctx">
        <!-- 当前合同上下文展示，便于联调时确认 contractId 是否一致 -->
        当前合同 id：<code>{{ store.currentContractId }}</code>
      </div>
    </el-header>
    <el-main class="main">
      <!-- 页面出口：具体业务逻辑都在 src/pages 下 -->
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #001529;
  color: #fff;
  padding: 0 24px;
}
.brand {
  font-weight: 600;
}
.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}
.nav-link {
  color: rgba(255, 255, 255, 0.75);
  text-decoration: none;
  padding: 6px 8px;
  border-radius: 4px;
}
.nav-link:hover {
  color: #fff;
}
.nav-link--active {
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
}
.ctx {
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
}
.main {
  background: #f5f7fa;
}
</style>
