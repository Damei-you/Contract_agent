<script setup lang="ts">
/**
 * 页面：审批辅助（/contracts/:id/approval-assist）
 */
import { ref } from 'vue'
import AgentTraceEvidencePanel from '../components/AgentTraceEvidencePanel.vue'
import { useContractContextStore } from '../stores/contractContext'
import { assistApproval } from '../api/contracts'
import type { NormalizedHttpError } from '../api/http'
import type { ApprovalAssistResponse } from '../types/contracts'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)
const approverRole = ref('财务经理')
const focus = ref('付款节点和发票合规')
const loading = ref(false)
const result = ref<ApprovalAssistResponse | null>(null)
const resultContractId = ref('')
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = null
  resultContractId.value = ''
  if (!contractId.value.trim() || !approverRole.value.trim() || !focus.value.trim()) {
    errorMsg.value = 'contractId / approverRole / focus 均为必填。'
    return
  }
  loading.value = true
  try {
    const submittedContractId = contractId.value.trim()
    result.value = await assistApproval(submittedContractId, {
      approverRole: approverRole.value,
      focus: focus.value,
    })
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
      <h1>审批辅助 <span class="endpoint">POST /api/contracts/{id}/approval-assist</span></h1>
    </div>

    <div class="layout">
      <main class="main-col">
        <div class="card">
          <div class="field">
            <label class="field-label">合同 id</label>
            <input v-model="contractId" type="text" class="inp" />
          </div>
          <div class="field">
            <label class="field-label">approverRole</label>
            <input v-model="approverRole" type="text" class="inp" />
          </div>
          <div class="field">
            <label class="field-label">focus</label>
            <input v-model="focus" type="text" class="inp" />
          </div>
          <button :disabled="loading" @click="submit" class="btn">
            {{ loading ? '生成中...' : '生成建议与清单' }}
          </button>
        </div>

        <div v-if="errorMsg" class="msg msg--error">{{ errorMsg }}</div>

        <div v-if="result" class="msg msg--success">
          <div class="result-item">
            <span class="result-label">suggestion</span>
            <p class="result-text">{{ result.suggestion }}</p>
          </div>
          <div class="result-item">
            <span class="result-label">checklist</span>
            <ul class="checklist">
              <li v-for="(item, i) in result.checklist" :key="i" class="checklist-item">{{ item }}</li>
            </ul>
          </div>
        </div>
      </main>

      <AgentTraceEvidencePanel
        :contract-id="resultContractId || contractId"
        :traces="result?.agentTrace ?? []"
        subtitle="建议和清单引用的证据"
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
.checklist {
  list-style: none;
  margin: 0;
  padding: 0;
}
.checklist-item {
  padding: 4px 0 4px 16px;
  border-bottom: 1px solid #eee;
  font-size: 13px;
  color: #333;
  position: relative;
}
.checklist-item:last-child {
  border-bottom: none;
}
.checklist-item::before {
  content: '-';
  position: absolute;
  left: 0;
  color: #999;
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
