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
    <h1>合同问答</h1>
    <p class="hint">POST <code>/api/contracts/{id}/qa</code></p>

    <div class="field">
      <label>合同 id</label>
      <input v-model="contractId" type="text" />
    </div>

    <div class="field">
      <label>问题</label>
      <textarea v-model="question" rows="3"></textarea>
    </div>

    <button :disabled="loading" @click="submit">
      {{ loading ? '询问中…' : '发送问题' }}
    </button>

    <div v-if="errorMsg" class="err">{{ errorMsg }}</div>

    <div v-if="result" class="ok">
      <p><strong>answer：</strong>{{ result.answer }}</p>
      <p>
        <strong>retrievedChunkIds：</strong>
        <code>{{ prettyJson(result.retrievedChunkIds) }}</code>
      </p>
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 24px;
  max-width: 720px;
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
input,
textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  box-sizing: border-box;
  font-family: inherit;
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
  background: #f5f5f5;
  padding: 12px;
  border-radius: 6px;
  margin-top: 12px;
}
</style>
