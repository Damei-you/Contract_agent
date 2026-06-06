<script setup lang="ts">
/**
 * 页面：风险检查（/contracts/:id/risk）
 *
 * 对应后端接口：
 * - POST /api/contracts/{id}/risk-check（无请求体）
 *
 * 页面目标（最小可用）：
 * - 输入 contractId，点击按钮触发检查
 * - 展示 summary + riskItems（列表）
 *
 * 常见错误：
 * - 404：合同不存在（提示先去导入合同）
 * - 500：服务端异常（可稍后重试）
 */
import { ref } from 'vue'
import { useContractContextStore } from '../stores/contractContext'
import { checkContractRisk } from '../api/contracts'
import type { NormalizedHttpError } from '../api/http'
import type { ContractRiskCheckResponse } from '../types/contracts'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)
const loading = ref(false)
const result = ref<ContractRiskCheckResponse | null>(null)
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = null
  // 1) 必填校验：避免发出明显无效的请求
  if (!contractId.value.trim()) {
    errorMsg.value = '请输入 contractId'
    return
  }
  loading.value = true
  try {
    // 2) 调用风险检查接口（无请求体）
    result.value = await checkContractRisk(contractId.value.trim())
    // 3) 同步全局上下文：本页成功说明这个 contractId 有效
    store.setCurrentContractId(contractId.value.trim())
  } catch (e) {
    errorMsg.value = (e as NormalizedHttpError).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="page-header">
      <h1>风险检查 <span class="endpoint">POST /api/contracts/{id}/risk-check</span></h1>
    </div>

    <div class="card">
      <div class="field">
        <label class="field-label">合同 id</label>
        <input v-model="contractId" type="text" class="inp" />
      </div>
      <button :disabled="loading" @click="submit" class="btn">
        {{ loading ? '检查中…' : '执行风险检查' }}
      </button>
    </div>

    <div v-if="errorMsg" class="msg msg--error">{{ errorMsg }}</div>

    <div v-if="result" class="msg msg--success">
      <div class="result-item">
        <span class="result-label">summary</span>
        <p class="result-text">{{ result.summary }}</p>
      </div>
      <div class="result-item">
        <span class="result-label">riskItems</span>
        <ul class="risk-list">
          <li v-for="item in result.riskItems" :key="item.code" class="risk-item">
            <span class="badge" :class="'badge--' + item.severity.toLowerCase()">{{ item.severity }}</span>
            <code class="risk-code">{{ item.code }}</code>
            <span class="risk-detail">— {{ item.detail }}</span>
          </li>
        </ul>
      </div>
      <div v-if="result.agentTrace?.length" class="result-item">
        <span class="result-label">agentTrace</span>
        <ol class="trace-list">
          <li v-for="trace in result.agentTrace" :key="trace.agentName" class="trace-item">
            <span class="trace-agent">{{ trace.agentName }}</span>
            <span class="trace-summary">{{ trace.summary }}</span>
          </li>
        </ol>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 20px;
  max-width: 720px;
}
.page-header {
  margin-bottom: 12px;
}
.page-header h1 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: #000;
}
.endpoint {
  font-size: 12px;
  font-weight: 400;
  color: #999;
  font-family: ui-monospace, Consolas, monospace;
  margin-left: 12px;
}
.card {
  background: #fff;
  border: 1px solid #e5e5e5;
  padding: 12px;
}
.field {
  margin-bottom: 8px;
}
.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}
.inp {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #ddd;
  border-radius: 0;
  box-sizing: border-box;
  font-family: inherit;
  font-size: 13px;
  background: #fafafa;
  color: #333;
}
.inp:focus {
  outline: none;
  border-color: #000;
  background: #fff;
}
.btn {
  padding: 6px 16px;
  background: #000;
  color: #fff;
  border: 1px solid #000;
  border-radius: 0;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
}
.btn:hover {
  background: #fff;
  color: #000;
}
.btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  background: #000;
  color: #fff;
}
.msg {
  margin-top: 8px;
  padding: 8px 12px;
  background: #fafafa;
  font-size: 13px;
  color: #000;
}
.msg--error {
  border-left: 3px solid #ccc;
}
.msg--success {
  border-left: 3px solid #000;
}
.result-item {
  margin-bottom: 8px;
}
.result-item:last-child {
  margin-bottom: 0;
}
.result-label {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.result-text {
  margin: 0;
  font-size: 13px;
  color: #333;
  line-height: 1.6;
}
.risk-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.risk-item {
  padding: 6px 0;
  border-bottom: 1px solid #eee;
  font-size: 13px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.risk-item:last-child {
  border-bottom: none;
}
.badge {
  display: inline-block;
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  background: #eee;
  color: #555;
  white-space: nowrap;
  flex-shrink: 0;
  margin-top: 1px;
}
.badge--high {
  background: #000;
  color: #fff;
}
.badge--medium {
  background: #888;
  color: #fff;
}
.badge--low {
  background: #ddd;
  color: #333;
}
.risk-code {
  font-size: 13px;
  color: #555;
  flex-shrink: 0;
}
.risk-detail {
  color: #666;
}
.trace-list {
  list-style: none;
  margin: 0;
  padding: 0;
  counter-reset: trace;
}
.trace-item {
  counter-increment: trace;
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px solid #eee;
  font-size: 13px;
  color: #333;
}
.trace-item:last-child {
  border-bottom: none;
}
.trace-agent {
  font-family: ui-monospace, Consolas, monospace;
  color: #000;
  white-space: nowrap;
}
.trace-agent::before {
  content: counter(trace) '. ';
  color: #999;
}
.trace-summary {
  color: #666;
  line-height: 1.5;
}
</style>
