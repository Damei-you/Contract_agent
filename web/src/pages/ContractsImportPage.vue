<script setup lang="ts">
import { ref } from 'vue'
import { importContract } from '../api/contracts'
import { useContractContextStore } from '../stores/contractContext'
import { prettyJson } from '../utils/json'
import type { NormalizedHttpError } from '../api/http'

const store = useContractContextStore()

const defaultPayload = {
  id: 'CTR-RAG-001',
  type: 'procurement',
  partyAName: '甲方公司',
  partyBName: '乙方公司',
  currency: 'CNY',
  amountExTax: 500000,
  taxRatePct: 13,
  amountIncTax: 565000,
  signDate: '2026-04-16',
  effectiveDate: '2026-04-16',
  endDate: '2027-04-15',
  performanceSite: '上海',
  paymentTermsSummary: '验收后30日付款',
  businessOwnerDept: '采购部',
  riskTier: 'MEDIUM',
  vectorDocId: 'doc_ctr_001',
  notes: '首版导入',
  chunks: [
    {
      id: 'c001',
      clauseCode: 'PAY',
      clauseTitle: '付款条件',
      clauseCategory: '财务',
      textForEmbedding: '甲方在收到发票及验收证明后30个自然日内付款。',
    },
    {
      id: 'c002',
      clauseCode: 'LIA',
      clauseTitle: '违约责任',
      clauseCategory: '法务',
      textForEmbedding: '逾期履约按日万分之五承担违约金。',
    },
  ],
}

const payloadText = ref(prettyJson(defaultPayload))
const loading = ref(false)
const result = ref<string>('')
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = ''
  let body: unknown
  try {
    body = JSON.parse(payloadText.value)
  } catch (e) {
    errorMsg.value = 'JSON 解析失败，请检查格式。'
    return
  }
  loading.value = true
  try {
    const resp = await importContract(body as any)
    result.value = prettyJson(resp)
    store.setCurrentContractId(resp.contractId)
  } catch (e) {
    const err = e as NormalizedHttpError
    errorMsg.value = `${err.message}${err.data ? '\n' + prettyJson(err.data) : ''}`
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <h1>合同导入</h1>
    <p class="hint">POST <code>/api/contracts/import</code></p>

    <label>请求体（JSON）：</label>
    <textarea v-model="payloadText" rows="18" class="ta" spellcheck="false"></textarea>

    <div class="actions">
      <button :disabled="loading" @click="submit">
        {{ loading ? '提交中…' : '提交导入' }}
      </button>
    </div>

    <div v-if="errorMsg" class="err">
      <strong>错误：</strong>
      <pre>{{ errorMsg }}</pre>
    </div>

    <div v-if="result" class="ok">
      <strong>成功响应：</strong>
      <pre>{{ result }}</pre>
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 24px;
  max-width: 920px;
}
.hint {
  color: #888;
}
.ta {
  width: 100%;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  box-sizing: border-box;
}
.actions {
  margin: 12px 0;
}
button {
  padding: 8px 18px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.err {
  color: #c62828;
  background: #fdecea;
  padding: 12px;
  border-radius: 6px;
  margin-top: 12px;
}
.ok {
  color: #1b5e20;
  background: #e8f5e9;
  padding: 12px;
  border-radius: 6px;
  margin-top: 12px;
}
pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 8px 0 0;
}
</style>
