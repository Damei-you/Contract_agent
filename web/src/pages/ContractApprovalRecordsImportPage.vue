<script setup lang="ts">
/**
 * 页面：审批记录导入（/contracts/:id/approval-records）
 *
 * 对应后端接口：
 * - POST /api/contracts/{id}/approval-records/import
 *
 * 语义说明（重要）：
 * - 这是“全量导入/替换”接口：你提交的 records 会整体替换该合同下的审批历史
 * - 因为 records 结构较深，阶段用 JSON 文本域方式最简单、最不容易做错
 *
 * 调用流程（点击“全量导入”按钮后）：
 * 1) 校验 contractId 必填
 * 2) JSON.parse 解析文本域
 * 3) 调用 importApprovalRecords(contractId, body)
 * 4) 成功：展示响应，并写回 store.currentContractId
 * 5) 失败：展示统一错误文案 + 后端 error body（若存在）
 */
import { ref } from 'vue'
import { useContractContextStore } from '../stores/contractContext'
import { importApprovalRecords } from '../api/contracts'
import { prettyJson } from '../utils/json'
import type { NormalizedHttpError } from '../api/http'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)

const payloadText = ref('')
const loading = ref(false)
const result = ref<string>('')
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = ''
  // 1) contractId 必填（后端不存在会返回 404，但前端先挡一次更明确）
  if (!contractId.value.trim()) {
    errorMsg.value = '请输入 contractId'
    return
  }
  let body: unknown
  try {
    // 2) JSON 解析：让用户可直接粘贴/编辑复杂 records 结构
    body = JSON.parse(payloadText.value)
  } catch {
    errorMsg.value = 'JSON 解析失败，请检查格式。'
    return
  }
  loading.value = true
  try {
    // 3) 调用 API 封装层
    const resp = await importApprovalRecords(contractId.value.trim(), body as any)
    result.value = prettyJson(resp)
    // 4) 同步全局上下文
    store.setCurrentContractId(contractId.value.trim())
  } catch (e) {
    const err = e as NormalizedHttpError
    // 失败：统一错误文案 + 可选的后端错误体
    errorMsg.value = `${err.message}${err.data ? '\n' + prettyJson(err.data) : ''}`
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="page-header">
      <h1>审批记录导入 <span class="endpoint">POST /api/contracts/{id}/approval-records/import</span></h1>
    </div>

    <div class="card">
      <div class="field">
        <label class="field-label">合同 id</label>
        <input v-model="contractId" type="text" class="inp" />
      </div>

      <label class="field-label">请求体（JSON）</label>
      <textarea v-model="payloadText" rows="18" class="ta" spellcheck="false"></textarea>

      <div class="actions">
        <button :disabled="loading" @click="submit" class="btn">
          {{ loading ? '导入中…' : '全量导入' }}
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
