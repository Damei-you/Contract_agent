<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getContractChunk } from '../api/contracts'
import { getPolicyKnowledge } from '../api/policies'
import type { NormalizedHttpError } from '../api/http'
import type {
  AgentTrace,
  ContractClauseChunk,
  PolicyKnowledgeDetail,
} from '../types/contracts'

const props = withDefaults(defineProps<{
  contractId: string
  traces?: AgentTrace[]
  title?: string
  subtitle?: string
  emptyText?: string
}>(), {
  traces: () => [],
  title: 'AGENTTRACE',
  subtitle: '合同条款与制度依据',
  emptyText: '本次未返回可点击证据',
})

const activeEvidence = ref<{ type: 'chunk' | 'policy'; id: string } | null>(null)
const chunkDetail = ref<ContractClauseChunk | null>(null)
const policyDetail = ref<PolicyKnowledgeDetail | null>(null)
const evidenceLoading = ref(false)
const evidenceError = ref<string>('')

const evidenceTraces = computed(() => {
  return props.traces.filter((trace) => {
    return trace.agentName !== 'RiskReviewAgent' && hasEvidence(trace)
  })
})

const evidenceTotal = computed(() => {
  return evidenceTraces.value.reduce((sum, trace) => {
    return sum + evidenceCount(trace)
  }, 0)
})

watch(
  () => [props.contractId, props.traces],
  () => resetEvidence(),
)

function resetEvidence() {
  activeEvidence.value = null
  chunkDetail.value = null
  policyDetail.value = null
  evidenceError.value = ''
  evidenceLoading.value = false
}

function traceKey(trace: AgentTrace, index: number) {
  return `${trace.agentName || 'Agent'}-${index}`
}

function hasEvidence(trace: AgentTrace) {
  return Boolean(trace.retrievedChunkIds?.length || trace.retrievedPolicyIds?.length)
}

function evidenceCount(trace: AgentTrace) {
  return (trace.retrievedChunkIds?.length || 0) + (trace.retrievedPolicyIds?.length || 0)
}

function traceDisplayName(agentName: string) {
  if (agentName === 'ContractFactAgent') {
    return '合同事实检索'
  }
  if (agentName === 'PolicyEvidenceAgent') {
    return '制度依据检索'
  }
  if (agentName === 'ApprovalAdviceAgent') {
    return '审批建议生成'
  }
  return agentName || 'Agent'
}

async function loadChunk(chunkId: string) {
  activeEvidence.value = { type: 'chunk', id: chunkId }
  chunkDetail.value = null
  policyDetail.value = null
  evidenceError.value = ''
  if (!props.contractId.trim()) {
    evidenceError.value = '缺少合同 id，无法查询条款。'
    return
  }
  evidenceLoading.value = true
  try {
    chunkDetail.value = await getContractChunk(props.contractId.trim(), chunkId)
  } catch (e) {
    evidenceError.value = (e as NormalizedHttpError).message
  } finally {
    evidenceLoading.value = false
  }
}

async function loadPolicy(policyId: string) {
  activeEvidence.value = { type: 'policy', id: policyId }
  chunkDetail.value = null
  policyDetail.value = null
  evidenceError.value = ''
  evidenceLoading.value = true
  try {
    policyDetail.value = await getPolicyKnowledge(policyId)
  } catch (e) {
    evidenceError.value = (e as NormalizedHttpError).message
  } finally {
    evidenceLoading.value = false
  }
}
</script>

<template>
  <aside class="trace-panel">
    <div class="trace-top">
      <div>
        <div class="trace-title">{{ title }}</div>
        <div class="trace-subtitle">{{ subtitle }}</div>
      </div>
      <span v-if="evidenceTotal" class="trace-total">{{ evidenceTotal }}</span>
    </div>

    <div v-if="!traces.length" class="trace-empty">执行后显示检索证据</div>
    <div v-else-if="!evidenceTraces.length" class="trace-empty">{{ emptyText }}</div>
    <div v-else class="source-list">
      <section
        v-for="(trace, index) in evidenceTraces"
        :key="traceKey(trace, index)"
        class="source-group"
      >
        <div class="source-head">
          <span class="source-title">{{ traceDisplayName(trace.agentName) }}</span>
          <span class="source-count">{{ evidenceCount(trace) }} 条</span>
        </div>
        <p class="source-summary">{{ trace.summary }}</p>

        <div v-if="trace.retrievedChunkIds?.length" class="evidence-group">
          <span class="evidence-label">合同条款</span>
          <div class="evidence-row">
            <button
              v-for="chunkId in trace.retrievedChunkIds"
              :key="chunkId"
              type="button"
              class="evidence-chip"
              :class="{ 'evidence-chip--active': activeEvidence?.type === 'chunk' && activeEvidence.id === chunkId }"
              @click="loadChunk(chunkId)"
            >
              {{ chunkId }}
            </button>
          </div>
        </div>

        <div v-if="trace.retrievedPolicyIds?.length" class="evidence-group">
          <span class="evidence-label">制度依据</span>
          <div class="evidence-row">
            <button
              v-for="policyId in trace.retrievedPolicyIds"
              :key="policyId"
              type="button"
              class="evidence-chip"
              :class="{ 'evidence-chip--active': activeEvidence?.type === 'policy' && activeEvidence.id === policyId }"
              @click="loadPolicy(policyId)"
            >
              {{ policyId }}
            </button>
          </div>
        </div>
      </section>
    </div>

    <div class="detail-box">
      <div v-if="!activeEvidence" class="detail-placeholder">
        暂无选中证据
      </div>
      <template v-else>
        <div class="detail-head">
          <span class="detail-label">{{ activeEvidence.type === 'chunk' ? '合同条款原文' : '制度依据原文' }}</span>
          <code>{{ activeEvidence.id }}</code>
        </div>
        <div v-if="evidenceLoading" class="detail-muted">加载中...</div>
        <div v-else-if="evidenceError" class="detail-error">{{ evidenceError }}</div>
        <div v-else-if="chunkDetail" class="detail-content">
          <div class="detail-title-line">
            {{ chunkDetail.clauseTitle || chunkDetail.clauseCode || '条款正文' }}
          </div>
          <div class="detail-meta">
            <span v-if="chunkDetail.clauseCode">编码 {{ chunkDetail.clauseCode }}</span>
            <span v-if="chunkDetail.sourceSection">章节 {{ chunkDetail.sourceSection }}</span>
          </div>
          <pre>{{ chunkDetail.textForEmbedding }}</pre>
        </div>
        <div v-else-if="policyDetail" class="detail-content">
          <div class="detail-title-line">
            {{ policyDetail.controlObjective || policyDetail.policyDomain }}
          </div>
          <div class="detail-meta">
            <span>{{ policyDetail.policyDomain }}</span>
            <span>{{ policyDetail.severity }}</span>
          </div>
          <pre>{{ policyDetail.policyTextForEmbedding }}</pre>
        </div>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.trace-panel {
  min-height: calc(100vh - 120px);
  position: sticky;
  top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fff;
  border: 1px solid #e5e5e5;
  padding: 12px;
}
.trace-top,
.source-head,
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.trace-title,
.evidence-label,
.detail-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.trace-title {
  color: #000;
  margin-bottom: 2px;
}
.trace-subtitle {
  color: #777;
  font-size: 12px;
}
.trace-total {
  min-width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #000;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.trace-empty,
.detail-muted,
.detail-error,
.detail-placeholder {
  font-size: 13px;
  color: #777;
  background: #fafafa;
  padding: 8px;
}
.detail-placeholder {
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-error {
  color: #000;
  border-left: 3px solid #ccc;
}
.source-list {
  display: grid;
  gap: 10px;
}
.source-group {
  border: 1px solid #eee;
  background: #fafafa;
  padding: 10px;
}
.source-title {
  color: #000;
  font-size: 13px;
  font-weight: 600;
}
.source-count {
  color: #777;
  font-size: 12px;
  white-space: nowrap;
}
.source-summary {
  margin: 4px 0 8px;
  color: #555;
  font-size: 13px;
  line-height: 1.5;
}
.evidence-group {
  margin-top: 8px;
}
.evidence-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.evidence-chip {
  padding: 5px 9px;
  max-width: 100%;
  border: 1px solid #ddd;
  background: #fff;
  color: #333;
  border-radius: 0;
  cursor: pointer;
  font-size: 13px;
  font-family: ui-monospace, Consolas, monospace;
  overflow-wrap: anywhere;
  transition: all 0.15s;
}
.evidence-chip:hover,
.evidence-chip--active {
  border-color: #000;
  background: #000;
  color: #fff;
}
.detail-box {
  margin-top: 0;
  border-top: 1px solid #e5e5e5;
  padding-top: 12px;
  min-height: 220px;
}
.detail-head code {
  font-size: 12px;
  color: #333;
  overflow-wrap: anywhere;
}
.detail-content {
  background: #fafafa;
  padding: 10px;
}
.detail-title-line {
  color: #000;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  margin-bottom: 8px;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  color: #333;
  font-size: 12px;
  font-weight: 600;
}
.detail-meta span {
  background: #fff;
  border: 1px solid #eee;
  padding: 2px 6px;
}
.detail-content pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: #333;
  font-size: 14px;
  line-height: 1.7;
  font-family: inherit;
}
@media (max-width: 960px) {
  .trace-panel {
    min-height: auto;
    position: static;
  }
}
</style>
