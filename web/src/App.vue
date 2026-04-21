<script setup lang="ts">
import { computed } from 'vue'
import { useContractContextStore } from './stores/contractContext'

const store = useContractContextStore()

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
