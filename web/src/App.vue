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
      <div class="brand">CONTRACT AGENT</div>
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
  gap: 16px;
  background: #000;
  color: #fff;
  padding: 0 16px;
  border-bottom: 1px solid #222;
}
.brand {
  font-weight: 700;
  font-size: 14px;
  letter-spacing: 1.5px;
  color: #fff;
  white-space: nowrap;
}
.nav {
  display: flex;
  gap: 2px;
  flex: 1;
}
.nav-link {
  color: #888;
  text-decoration: none;
  padding: 4px 10px;
  border-radius: 0;
  font-size: 13px;
  transition: color 0.2s, background 0.2s;
}
.nav-link:hover {
  color: #fff;
  background: #111;
}
.nav-link--active {
  color: #000;
  background: #fff;
  position: relative;
}
.nav-link--active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: #333333;
}
.nav-link--active:hover {
  color: #000;
  background: #fff;
}
.ctx {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
  white-space: nowrap;
}
.ctx-label {
  color: #555;
}
.ctx-id {
  color: #aaa;
  background: #111;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px;
}
.main {
  background: #fff;
  padding: 0;
}
</style>
