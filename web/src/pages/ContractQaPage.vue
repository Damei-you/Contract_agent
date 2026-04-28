<script setup lang="ts">
/**
 * 页面：合同问答（/contracts/:id/qa）
 *
 * 对应后端接口：
 * - POST /api/contracts/{id}/qa
 *
 * 页面目标（最小可用）：
 * - 输入 contractId + question，点击按钮发请求
 * - 展示 answer 与 retrievedChunkIds
 *
 * contractId 的来源：
 * - 默认取 Pinia store 的 currentContractId（通常来自“合同导入页”的成功返回）
 * - 这里用 ref(store.currentContractId) 作为初始值：页面创建时读取一次即可
 *   （每次切换页面会重新创建组件实例，所以通常能拿到最新的 store 值）
 */
import { ref } from 'vue'
import { useContractContextStore } from '../stores/contractContext'
import { askContract } from '../api/contracts'
import { prettyJson } from '../utils/json'
import type { NormalizedHttpError } from '../api/http'
import type { ContractQaResponse } from '../types/contracts'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)
const question = ref('这个合同付款条件是什么？')
const loading = ref(false)
const result = ref<ContractQaResponse | null>(null)
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = null
  // 1) 最小必填校验：避免 400（后端也会校验，但前端先挡一次体验更好）
  if (!contractId.value.trim() || !question.value.trim()) {
    errorMsg.value = 'contractId 和 question 都必填。'
    return
  }
  loading.value = true
  try {
    // 2) 调用 API 封装：contracts.ts -> http.ts -> /api 代理 -> 后端
    result.value = await askContract(contractId.value.trim(), {
      question: question.value,
    })
    // 3) 成功后把 contractId 写回 store，保持全局上下文一致
    store.setCurrentContractId(contractId.value.trim())
  } catch (e) {
    // 失败：这里展示统一错误文案（来自 http.ts 的归一化）
    errorMsg.value = (e as NormalizedHttpError).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="page-header">
      <h1>合同问答 <span class="endpoint">POST /api/contracts/{id}/qa</span></h1>
    </div>

    <div class="card">
      <div class="field">
        <label class="field-label">合同 id</label>
        <input v-model="contractId" type="text" class="inp" />
      </div>
      <div class="field">
        <label class="field-label">问题</label>
        <textarea v-model="question" rows="3" class="inp ta"></textarea>
      </div>
      <button :disabled="loading" @click="submit" class="btn">
        {{ loading ? '询问中…' : '发送问题' }}
      </button>
    </div>

    <div v-if="errorMsg" class="msg msg--error">{{ errorMsg }}</div>

    <div v-if="result" class="msg msg--success">
      <div class="result-item">
        <span class="result-label">answer</span>
        <p class="result-text">{{ result.answer }}</p>
      </div>
      <div class="result-item">
        <span class="result-label">retrievedChunkIds</span>
        <code class="result-code">{{ prettyJson(result.retrievedChunkIds) }}</code>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 20px;
  max-width: 720px;
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
.result-code {
  font-size: 12px;
  color: #555;
  background: #f0f0f0;
  padding: 2px 6px;
  display: inline-block;
}
</style>
