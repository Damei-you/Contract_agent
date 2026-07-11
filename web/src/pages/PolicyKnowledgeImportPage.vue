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
import { importPolicyKnowledge, parsePolicyKnowledgeFile } from '../api/policies'
import { prettyJson } from '../utils/json'
import type { NormalizedHttpError } from '../api/http'

const payloadText = ref('')
const selectedFile = ref<File | null>(null)
const loading = ref(false)
const parseLoading = ref(false)
const result = ref<string>('')
const errorMsg = ref<string>('')
const parseMsg = ref<string>('')

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
  parseMsg.value = ''
}

async function parseSelectedFile() {
  errorMsg.value = ''
  result.value = ''
  parseMsg.value = ''
  if (!selectedFile.value) {
    errorMsg.value = '请选择要解析的制度文件。'
    return
  }
  parseLoading.value = true
  try {
    const resp = await parsePolicyKnowledgeFile(selectedFile.value)
    payloadText.value = prettyJson(resp.draft)
    const warnings = resp.document.warnings.length
      ? `\n${resp.document.warnings.join('\n')}`
      : ''
    parseMsg.value = `已解析 ${resp.document.filename || selectedFile.value.name}，生成 ${resp.policyCount} 个制度条目。${warnings}`
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

    <div class="card file-card">
      <label class="field-label">制度文件</label>
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
