<script setup lang="ts">
/**
 * 页面：合同导入（/contracts/import）
 *
 * 对应后端接口：
 * - POST /api/contracts/import
 *
 * 这是“主链路”的起点页：
 * - 其余页面（问答/风险检查/审批辅助/审批记录导入）都依赖一个已存在的 contractId
 * - 所以本页导入成功后，会把返回的 contractId 写入 Pinia store（contractContext）
 *
 * 为什么用 JSON 文本域（而不是一堆表单项）：
 * - 导入合同的字段较多，且 chunks 是数组结构
 * - MVP 阶段以“联调可用”为第一目标：直接粘贴/修改 JSON 最快
 *
 * 调用流程（点击“提交导入”按钮后）：
 * 1) 解析 textarea 中的 JSON 字符串（JSON.parse）
 * 2) 调用 api/importContract（src/api/contracts.ts）发请求
 * 3) 成功：展示响应，并更新 store.currentContractId
 * 4) 失败：展示统一错误 message，并可附带后端返回的 error body（err.data）
 */
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
    // 1) JSON 解析：让用户可直接粘贴/编辑请求体
    body = JSON.parse(payloadText.value)
  } catch (e) {
    errorMsg.value = 'JSON 解析失败，请检查格式。'
    return
  }
  // 2) loading：最小防重复提交
  loading.value = true
  try {
    // 3) 调用 API 封装层（contracts.ts -> http.ts -> Vite proxy -> 后端）
    const resp = await importContract(body as any)
    result.value = prettyJson(resp)
    // 4) 把 contractId 写入全局上下文，供其他页面默认使用
    store.setCurrentContractId(resp.contractId)
  } catch (e) {
    const err = e as NormalizedHttpError
    // 失败：err.message 来自 utils/errors.ts 的状态码映射；err.data 是后端错误体（若存在）
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
