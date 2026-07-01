<script setup lang="ts">
/**
 * 页面：风险检查（/contracts/:id/risk）
 */
import { ref } from 'vue'
import AgentTraceEvidencePanel from '../components/AgentTraceEvidencePanel.vue'
import { useContractContextStore } from '../stores/contractContext'
import { checkContractRisk } from '../api/contracts'
import type { NormalizedHttpError } from '../api/http'
import type { ContractRiskCheckResponse } from '../types/contracts'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)
const loading = ref(false)
const result = ref<ContractRiskCheckResponse | null>(null)
const resultContractId = ref('')
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = null
  resultContractId.value = ''
  if (!contractId.value.trim()) {
    errorMsg.value = '请输入 contractId'
    return
  }
  loading.value = true
  try {
    const submittedContractId = contractId.value.trim()
    result.value = await checkContractRisk(submittedContractId)
    resultContractId.value = submittedContractId
    store.setCurrentContractId(submittedContractId)
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

    <div class="layout">
      <main class="main-col">
        <div class="card">
          <div class="field">
            <label class="field-label">合同 id</label>
            <input v-model="contractId" type="text" class="inp" />
          </div>
          <button :disabled="loading" @click="submit" class="btn">
            {{ loading ? '检查中...' : '执行风险检查' }}
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
                <span class="risk-detail">- {{ item.detail }}</span>
              </li>
            </ul>
          </div>
        </div>
      </main>

      <AgentTraceEvidencePanel
        :contract-id="resultContractId || contractId"
        :traces="result?.agentTrace ?? []"
        subtitle="合同条款与制度依据"
      />
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 20px;
  max-width: 1480px;
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
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(440px, 560px);
  gap: 16px;
  align-items: start;
}
.main-col {
  min-width: 0;
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
@media (max-width: 960px) {
  .page {
    max-width: none;
  }
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
