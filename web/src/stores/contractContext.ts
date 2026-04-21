import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useContractContextStore = defineStore('contractContext', () => {
  // 当前选中/最近使用的合同 id，供“合同相关”页面共用
  const currentContractId = ref<string>('demo-001')

  function setCurrentContractId(id: string) {
    currentContractId.value = id
  }

  return { currentContractId, setCurrentContractId }
})
