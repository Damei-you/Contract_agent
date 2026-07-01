<script setup lang="ts">
/**
 * 页面：合同问答（/contracts/:id/qa）
 */
import { computed, ref } from 'vue'
import AgentTraceEvidencePanel from '../components/AgentTraceEvidencePanel.vue'
import { useContractContextStore } from '../stores/contractContext'
import { askContract } from '../api/contracts'
import type { NormalizedHttpError } from '../api/http'
import type { AgentTrace, ContractQaResponse } from '../types/contracts'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)
const question = ref('这个合同付款条件是什么？')
const includePolicyEvidence = ref(false)
const loading = ref(false)
const result = ref<ContractQaResponse | null>(null)
const resultContractId = ref('')
const resultIncludePolicyEvidence = ref(false)
const errorMsg = ref<string>('')

const traceSubtitle = computed(() => {
  if (!result.value) {
    return includePolicyEvidence.value ? '回答引用的条款与制度' : '回答引用的合同条款'
  }
  return resultIncludePolicyEvidence.value ? '回答引用的条款与制度' : '回答引用的合同条款'
})

const evidenceTraces = computed<AgentTrace[]>(() => {
  if (!result.value) {
    return []
  }
  const traces = result.value.agentTrace ?? []
  if (traces.some((trace) => hasEvidence(trace))) {
    return traces
  }
  const fallback: AgentTrace[] = []
  const chunkIds = normalizeIds(result.value.retrievedChunkIds)
  const policyIds = normalizeIds(result.value.retrievedPolicyIds)
  if (chunkIds.length) {
    fallback.push({
      agentName: 'ContractFactAgent',
      summary: `已按问题检索合同条款，命中 ${chunkIds.length} 个合同条款片段。`,
      retrievedChunkIds: chunkIds,
      retrievedPolicyIds: [],
    })
  }
  if (policyIds.length) {
    fallback.push({
      agentName: 'PolicyEvidenceAgent',
      summary: `已按问题检索制度依据，命中 ${policyIds.length} 条制度依据。`,
      retrievedChunkIds: [],
      retrievedPolicyIds: policyIds,
    })
  }
  return fallback
})

async function submit() {
  errorMsg.value = ''
  result.value = null
  resultContractId.value = ''
  resultIncludePolicyEvidence.value = false
  if (!contractId.value.trim() || !question.value.trim()) {
    errorMsg.value = 'contractId 和 question 都必填。'
    return
  }
  loading.value = true
  try {
    const submittedContractId = contractId.value.trim()
    result.value = await askContract(submittedContractId, {
      question: question.value,
      includePolicyEvidence: includePolicyEvidence.value,
    })
    resultContractId.value = submittedContractId
    resultIncludePolicyEvidence.value = includePolicyEvidence.value
    store.setCurrentContractId(submittedContractId)
  } catch (e) {
    errorMsg.value = (e as NormalizedHttpError).message
  } finally {
    loading.value = false
  }
}

function hasEvidence(trace: AgentTrace) {
  return Boolean(trace.retrievedChunkIds?.length || trace.retrievedPolicyIds?.length)
}

function normalizeIds(ids?: string[]) {
  return (ids ?? []).map((id) => id.trim()).filter(Boolean)
}
</script>

<template>
  <section class="page">
    <div class="page-header">
      <h1>合同问答 <span class="endpoint">POST /api/contracts/{id}/qa</span></h1>
    </div>

    <div class="layout">
      <main class="main-col">
        <div class="card">
          <div class="field">
            <label class="field-label">合同 id</label>
            <input v-model="contractId" type="text" class="inp" />
          </div>
          <div class="field">
            <label class="field-label">问题</label>
            <textarea v-model="question" rows="3" class="inp ta"></textarea>
          </div>
          <label class="option-row">
            <input v-model="includePolicyEvidence" type="checkbox" />
            <span>同时引用制度依据</span>
          </label>
          <button :disabled="loading" @click="submit" class="btn">
            {{ loading ? '询问中...' : '发送问题' }}
          </button>
        </div>

        <div v-if="errorMsg" class="msg msg--error">{{ errorMsg }}</div>

        <div v-if="result" class="msg msg--success">
          <div class="result-item">
            <span class="result-label">answer</span>
            <p class="result-text">{{ result.answer }}</p>
          </div>
        </div>
      </main>

      <AgentTraceEvidencePanel
        :contract-id="resultContractId || contractId"
        :traces="evidenceTraces"
        :subtitle="traceSubtitle"
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
.ta {
  resize: vertical;
}
.option-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0 10px;
  color: #333;
  font-size: 13px;
  cursor: pointer;
}
.option-row input {
  width: 14px;
  height: 14px;
  margin: 0;
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
  margin-bottom: 2px;
}
.result-text {
  margin: 0;
  font-size: 13px;
  color: #333;
  line-height: 1.6;
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
