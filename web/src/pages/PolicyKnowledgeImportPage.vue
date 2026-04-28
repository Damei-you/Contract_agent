<script setup lang="ts">
/**
 * 页面：政策制度知识库导入（/policies/import）
 *
 * 对应后端接口：
 * - POST /api/policies/import
 *
 * 本页面独立于合同流程，不依赖 contractId：
 * - 导入政策制度条目到知识库，同时同步写入向量库做语义检索
 *
 * 调用流程（点击"提交导入"按钮后）：
 * 1) 解析 textarea 中的 JSON 字符串（JSON.parse）
 * 2) 调用 importPolicyKnowledge（src/api/policies.ts）发请求
 * 3) 成功：展示响应（importedCount + policyIds + 向量警告
 * 4) 失败：展示统一错误 message，并可附带后端返回的 error body（err.data）
 */
import { ref } from 'vue'
import { importPolicyKnowledge } from '../api/policies'
import { prettyJson } from '../utils/json'
import type { NormalizedHttpError } from '../api/http'

const defaultPayload = {
  policies: [
    {
      policyId: 'POL-FIN-001',
      policyDomain: '财务合规',
      appliesToContractType: 'procurement',
      severity: 'HIGH',
      triggerKeywords: '预付款;预付;定金',
      controlObjective: '控制预付款比例不超过30%',
      policyTextForEmbedding: '采购合同预付款比例不得超过合同总金额的30%，且需提供等额保函。',
      requiredEvidence: '预付款保函;付款审批单',
      escalationRole: '财务总监',
      vectorDocId: 'vec_pol_fin_001',
      updatedAt: null,
    },
    {
      policyId: 'POL-COM-001',
      policyDomain: '合规审查',
      appliesToContractType: 'procurement',
      severity: 'MEDIUM',
      triggerKeywords: '关联交易;关联方;利益冲突',
      controlObjective: '确保关联交易合规披露',
      policyTextForEmbedding: '与关联方签订采购合同需经合规部门审批，并在合同中明确披露关联关系。',
      requiredEvidence: '关联交易审批表;合规审查意见',
      escalationRole: '合规负责人',
      vectorDocId: 'vec_pol_com_001',
      updatedAt: null,
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
    // 1) JSON 解析
    body = JSON.parse(payloadText.value)
  } catch (e) {
    errorMsg.value = 'JSON 解析失败，请检查格式。'
    return
  }
  // 2) loading：防重复提交
  loading.value = true
  try {
    // 3) 调用 API
    const resp = await importPolicyKnowledge(body as any)
    result.value = prettyJson(resp)
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
    <div class="page-header">
      <h1>政策制度导入 <span class="endpoint">POST /api/policies/import</span></h1>
    </div>

    <div class="card">
      <label class="field-label">请求体（JSON）</label>
      <textarea v-model="payloadText" rows="22" class="ta" spellcheck="false"></textarea>

      <div class="actions">
        <button :disabled="loading" @click="submit" class="btn">
          {{ loading ? '提交中…' : '提交导入' }}
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
}
.msg--error {
  border-left: 3px solid #ccc;
  color: #000;
}
.msg--success {
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
</style>
