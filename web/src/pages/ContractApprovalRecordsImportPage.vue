<script setup lang="ts">
import { ref } from 'vue'
import { useContractContextStore } from '../stores/contractContext'
import { importApprovalRecords } from '../api/contracts'
import { prettyJson } from '../utils/json'
import type { NormalizedHttpError } from '../api/http'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)

const defaultPayload = {
  records: [
    {
      id: 'AR-001',
      stepNo: 2,
      approverRole: '财务经理',
      decision: 'APPROVED',
      decisionTime: '2026-04-16T10:00:00+08:00',
      commentSummary: '付款条件清晰，可通过。',
      linkedPolicyIds: ['POL-TAX-001'],
      linkedClauseChunkIds: ['c001', 'c002'],
      riskItems: [
        {
          code: 'PAYMENT_REVIEW',
          severity: 'LOW',
          detail: '付款闭环已确认。',
          relatedClauseChunkIds: ['c001'],
          relatedPolicyIds: [],
        },
      ],
      vectorDocId: 'doc_ar_001',
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
  if (!contractId.value.trim()) {
    errorMsg.value = '请输入 contractId'
    return
  }
  let body: unknown
  try {
    body = JSON.parse(payloadText.value)
  } catch {
    errorMsg.value = 'JSON 解析失败，请检查格式。'
    return
  }
  loading.value = true
  try {
    const resp = await importApprovalRecords(contractId.value.trim(), body as any)
    result.value = prettyJson(resp)
    store.setCurrentContractId(contractId.value.trim())
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
    <h1>审批记录导入</h1>
    <p class="hint">
      POST <code>/api/contracts/{id}/approval-records/import</code>
    </p>

    <div class="field">
      <label>合同 id</label>
      <input v-model="contractId" type="text" />
    </div>

    <label>请求体（JSON）</label>
    <textarea v-model="payloadText" rows="18" class="ta" spellcheck="false"></textarea>

    <div class="actions">
      <button :disabled="loading" @click="submit">
        {{ loading ? '导入中…' : '全量导入' }}
      </button>
    </div>

    <div v-if="errorMsg" class="err">
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
.field {
  margin: 12px 0;
}
.field label {
  display: block;
  font-weight: 600;
  margin-bottom: 4px;
}
input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  box-sizing: border-box;
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
