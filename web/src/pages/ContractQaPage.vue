<script setup lang="ts">
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
  if (!contractId.value.trim() || !question.value.trim()) {
    errorMsg.value = 'contractId 和 question 都必填。'
    return
  }
  loading.value = true
  try {
    result.value = await askContract(contractId.value.trim(), {
      question: question.value,
    })
    store.setCurrentContractId(contractId.value.trim())
  } catch (e) {
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
