<script setup lang="ts">
/**
 * 页面：审批辅助（/contracts/:id/approval-assist）
 *
 * 对应后端接口：
 * - POST /api/contracts/{id}/approval-assist
 *
 * 页面目标（最小可用）：
 * - 输入 contractId + approverRole + focus
 * - 点击按钮生成 suggestion + checklist
 *
 * 说明：
 * - approverRole/focus 在后端会做参数校验；前端先做必填校验可减少 400
 */
import { ref } from 'vue'
import { useContractContextStore } from '../stores/contractContext'
import { assistApproval } from '../api/contracts'
import type { NormalizedHttpError } from '../api/http'
import type { ApprovalAssistResponse } from '../types/contracts'

const store = useContractContextStore()

const contractId = ref(store.currentContractId)
const approverRole = ref('财务经理')
const focus = ref('付款节点和发票合规')
const loading = ref(false)
const result = ref<ApprovalAssistResponse | null>(null)
const errorMsg = ref<string>('')

async function submit() {
  errorMsg.value = ''
  result.value = null
  // 1) 最小必填校验
  if (!contractId.value.trim() || !approverRole.value.trim() || !focus.value.trim()) {
    errorMsg.value = 'contractId / approverRole / focus 均为必填。'
    return
  }
  loading.value = true
  try {
    // 2) 调用审批辅助接口
    result.value = await assistApproval(contractId.value.trim(), {
      approverRole: approverRole.value,
      focus: focus.value,
    })
    // 3) 成功后同步全局当前合同 id
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
    <h1>审批辅助</h1>
    <p class="hint">POST <code>/api/contracts/{id}/approval-assist</code></p>

    <div class="field">
      <label>合同 id</label>
      <input v-model="contractId" type="text" />
    </div>
    <div class="field">
      <label>approverRole</label>
      <input v-model="approverRole" type="text" />
    </div>
    <div class="field">
      <label>focus</label>
      <input v-model="focus" type="text" />
    </div>

    <button :disabled="loading" @click="submit">
      {{ loading ? '生成中…' : '生成建议与清单' }}
    </button>

    <div v-if="errorMsg" class="err">{{ errorMsg }}</div>

    <div v-if="result" class="ok">
      <p><strong>suggestion：</strong>{{ result.suggestion }}</p>
      <p><strong>checklist：</strong></p>
      <ul>
        <li v-for="(item, i) in result.checklist" :key="i">{{ item }}</li>
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
</style>
