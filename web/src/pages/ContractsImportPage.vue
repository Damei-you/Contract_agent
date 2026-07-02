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
 * - 阶段以“联调可用”为第一目标：直接粘贴/修改 JSON 最快
 *
 * 调用流程（点击“提交导入”按钮后）：
 * 1) 解析 textarea 中的 JSON 字符串（JSON.parse）
 * 2) 调用 api/importContract（src/api/contracts.ts）发请求
 * 3) 成功：展示响应，并更新 store.currentContractId
 * 4) 失败：展示统一错误 message，并可附带后端返回的 error body（err.data）
 */
import { ref } from 'vue'
import { importContract, parseContractFile } from '../api/contracts'
import { useContractContextStore } from '../stores/contractContext'
import { prettyJson } from '../utils/json'
import type { NormalizedHttpError } from '../api/http'
import type { ContractImportRequest } from '../types/contracts'

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
const selectedFile = ref<File | null>(null)
const loading = ref(false)
const parseLoading = ref(false)
const result = ref<string>('')
const errorMsg = ref<string>('')
const parseMsg = ref<string>('')
const pendingOverwriteId = ref<string>('')
const overwriteConfirmationMsg = ref<string>('')

function clearOverwriteConfirmation() {
  pendingOverwriteId.value = ''
  overwriteConfirmationMsg.value = ''
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
  parseMsg.value = ''
  result.value = ''
  errorMsg.value = ''
  clearOverwriteConfirmation()
}

async function parseSelectedFile() {
  errorMsg.value = ''
  result.value = ''
  parseMsg.value = ''
  clearOverwriteConfirmation()
  if (!selectedFile.value) {
    errorMsg.value = '请选择要解析的合同文件。'
    return
  }
  parseLoading.value = true
  try {
    const resp = await parseContractFile(selectedFile.value)
    payloadText.value = prettyJson(resp.draft)
    const warnings = resp.document.warnings.length
      ? `\n${resp.document.warnings.join('\n')}`
      : ''
    parseMsg.value = `已解析 ${resp.document.filename || selectedFile.value.name}，生成 ${resp.chunkCount} 个条款块。${warnings}`
  } catch (e) {
    const err = e as NormalizedHttpError
    errorMsg.value = `${err.message}${err.data ? '\n' + prettyJson(err.data) : ''}`
  } finally {
    parseLoading.value = false
  }
}

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
  if (body === null || Array.isArray(body) || typeof body !== 'object') {
    errorMsg.value = '请求体必须是 JSON 对象。'
    return
  }
  const payload = body as ContractImportRequest
  const payloadId = typeof payload.id === 'string' ? payload.id.trim() : ''
  const shouldConfirmOverwrite = Boolean(
    pendingOverwriteId.value && payloadId === pendingOverwriteId.value,
  )
  if (!shouldConfirmOverwrite && pendingOverwriteId.value) {
    clearOverwriteConfirmation()
  }
  payload.overwriteConfirmed = shouldConfirmOverwrite
  // 2) loading：最小防重复提交
  loading.value = true
  try {
    // 3) 调用 API 封装层（contracts.ts -> http.ts -> Vite proxy -> 后端）
    const resp = await importContract(payload)
    if (resp.requiresConfirmation) {
      pendingOverwriteId.value = resp.contractId
      overwriteConfirmationMsg.value = resp.message
        || `合同 ID ${resp.contractId} 已存在，请确认是否覆盖导入。`
      return
    }
    clearOverwriteConfirmation()
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
    <div class="page-header">
      <h1>合同导入 <span class="endpoint">POST /api/contracts/import</span></h1>
    </div>

    <div class="card file-card">
      <label class="field-label">合同文件</label>
      <div class="file-row">
        <input
          type="file"
          class="file-input"
          accept=".pdf,.doc,.docx,.txt,.md,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown"
          @change="onFileChange"
        />
        <button :disabled="parseLoading || !selectedFile" @click="parseSelectedFile" class="btn btn--secondary">
          {{ parseLoading ? '解析中…' : '解析为 JSON' }}
        </button>
      </div>
      <div v-if="parseMsg" class="file-msg">
        <pre>{{ parseMsg }}</pre>
      </div>
    </div>

    <div class="card">
      <label class="field-label">请求体（JSON）</label>
      <textarea
        v-model="payloadText"
        rows="18"
        class="ta"
        spellcheck="false"
        @input="clearOverwriteConfirmation"
      ></textarea>

      <div v-if="overwriteConfirmationMsg" class="msg msg--warning">
        <strong>需要确认</strong>
        <pre>{{ overwriteConfirmationMsg }}</pre>
      </div>

      <div class="actions">
        <button :disabled="loading" @click="submit" class="btn">
          {{ loading ? '提交中…' : pendingOverwriteId ? '确认覆盖' : '提交导入' }}
        </button>
      </div>
    </div>

    <div v-if="errorMsg" class="msg msg--error">
      <pre>{{ errorMsg }}</pre>
    </div>

    <div v-if="result" class="msg msg--success">
      <strong>成功响应</strong>
      <pre>{{ result }}</pre>
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 20px;
  max-width: 920px;
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
.file-card {
  margin-bottom: 8px;
}
.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}
.ta {
  width: 100%;
  font-family: 'Cascadia Code', 'Fira Code', Consolas, ui-monospace, monospace;
  font-size: 13px;
  padding: 8px 10px;
  border: 1px solid #ddd;
  border-radius: 0;
  box-sizing: border-box;
  background: #f5f5f5;
  color: #333;
  resize: vertical;
}
.ta:focus {
  outline: none;
  border-color: #000;
  background: #fff;
}
.actions {
  margin-top: 8px;
}
.file-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.file-input {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #333;
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
.btn--secondary {
  background: #fff;
  color: #000;
}
.btn--secondary:hover {
  background: #000;
  color: #fff;
}
.btn--secondary:disabled {
  background: #fff;
  color: #000;
}
.msg {
  margin-top: 8px;
  padding: 8px 12px;
  background: #fafafa;
}
.msg--error {
  border-left: 3px solid #ccc;
  color: #000;
}
.msg--success {
  border-left: 3px solid #000;
  color: #000;
}
.msg--warning {
  border-left: 3px solid #000;
  color: #000;
}
.msg strong {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: #000;
}
.msg pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-size: 13px;
  color: #333;
}
.file-msg {
  margin-top: 8px;
  border-left: 3px solid #000;
  background: #fafafa;
  padding: 8px 10px;
}
.file-msg pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-size: 12px;
  color: #333;
}
</style>
