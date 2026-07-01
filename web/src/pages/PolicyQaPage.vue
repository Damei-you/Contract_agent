<script setup lang="ts">
/**
 * 页面：政策制度问答（/policies/qa）
 */
import { computed, ref } from 'vue'
import AgentTraceEvidencePanel from '../components/AgentTraceEvidencePanel.vue'
import { askPolicyKnowledge } from '../api/policies'
import type { NormalizedHttpError } from '../api/http'
import type { AgentTrace, PolicyQaResponse } from '../types/contracts'

const contractType = ref('')
const question = ref('付款节点和发票合规有哪些制度要求？')
const loading = ref(false)
const result = ref<PolicyQaResponse | null>(null)
const errorMsg = ref<string>('')

const evidenceTraces = computed<AgentTrace[]>(() => {
  if (!result.value) {
    return []
  }
  const traces = result.value.agentTrace ?? []
  if (traces.some((trace) => hasEvidence(trace))) {
    return traces
  }
  const policyIds = normalizeIds(result.value.retrievedPolicyIds)
  if (!policyIds.length) {
    return []
  }
  return [{
    agentName: 'PolicyEvidenceAgent',
    summary: `已按问题检索制度依据，命中 ${policyIds.length} 条制度依据。`,
    retrievedChunkIds: [],
    retrievedPolicyIds: policyIds,
  }]
})

async function submit() {
  errorMsg.value = ''
  result.value = null
  if (!question.value.trim()) {
    errorMsg.value = 'question 必填。'
    return
  }
  loading.value = true
  try {
    result.value = await askPolicyKnowledge({
      question: question.value.trim(),
      contractType: contractType.value,
    })
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
      <h1>政策制度问答 <span class="endpoint">POST /api/policies/qa</span></h1>
    </div>

    <div class="layout">
      <main class="main-col">
        <div class="card">
          <div class="field">
            <label class="field-label">合同类型范围</label>
            <select v-model="contractType" class="inp">
              <option value="">不限</option>
              <option value="procurement">采购合同</option>
              <option value="service">服务合同</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label">问题</label>
            <textarea v-model="question" rows="4" class="inp ta"></textarea>
          </div>
          <button :disabled="loading" @click="submit" class="btn">
            {{ loading ? '查询中...' : '发送问题' }}
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
        contract-id=""
        :traces="evidenceTraces"
        subtitle="回答引用的制度依据"
        empty-text="本次未命中制度依据"
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
