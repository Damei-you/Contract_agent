<script setup lang="ts">
import { ref } from 'vue'
import { useContractContextStore } from '../stores/contractContext'
import { checkContractRisk } from '../api/contracts'
import type { NormalizedHttpError } from '../api/http'
import type { ContractRiskCheckResponse } from '../types/contracts'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)
const loading = ref(false)
const result = ref<ContractRiskCheckResponse | null>(null)
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = null
  if (!contractId.value.trim()) {
    errorMsg.value = '请输入 contractId'
    return
  }
  loading.value = true
  try {
    result.value = await checkContractRisk(contractId.value.trim())
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
    <h1>风险检查</h1>
    <p class="hint">POST <code>/api/contracts/{id}/risk-check</code></p>

    <div class="field">
      <label>合同 id</label>
      <input v-model="contractId" type="text" />
    </div>

    <button :disabled="loading" @click="submit">
      {{ loading ? '检查中…' : '执行风险检查' }}
    </button>

    <div v-if="errorMsg" class="err">{{ errorMsg }}</div>

    <div v-if="result" class="ok">
      <p><strong>summary：</strong>{{ result.summary }}</p>
      <p><strong>riskItems：</strong></p>
      <ul>
        <li v-for="item in result.riskItems" :key="item.code">
          <span class="badge" :data-sev="item.severity">{{ item.severity }}</span>
          <code>{{ item.code }}</code> — {{ item.detail }}
        </li>
      </ul>
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
input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  box-sizing: border-box;
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
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  margin-right: 8px;
  background: #eee;
}
.badge[data-sev='HIGH'] {
  background: #ffebee;
  color: #b71c1c;
}
.badge[data-sev='MEDIUM'] {
  background: #fff8e1;
  color: #8d6e00;
}
.badge[data-sev='LOW'] {
  background: #e8f5e9;
  color: #1b5e20;
}
</style>
