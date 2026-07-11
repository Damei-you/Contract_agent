import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listContracts } from '../api/contracts'
import type { ContractListItem } from '../types/contracts'

export const useContractContextStore = defineStore('contractContext', () => {
  /**
   * 合同上下文 Store（跨页面共享状态）
   *
   * 为什么需要它：
   * - 项目有 5 个页面，但除了“合同导入”之外，其余 4 个页面都依赖 `contractId`
   * - 通过 store 维护一个“当前合同 id”，可以让导入成功后自动带到问答/风险/审批辅助/审批记录导入页
   *
   * 目前 store 只放最小必要状态（阶段）：
   * - currentContractId：当前/最近使用的合同 id
   *
   * 后续可能扩展（按 docs/FRONTEND_DEV_GUIDE.md 的全局功能建议）：
   * - localStorage 持久化最近使用的 contractId
   * - 记录最近一次导入请求体 / 最近一次审批记录导入请求体 / 最近提问历史等
   */

  // 当前选中/最近使用的合同 id，供“合同相关”页面共用
  const currentContractId = ref<string>('')
  const contracts = ref<ContractListItem[]>([])
  const contractsLoading = ref(false)

  /**
   * 更新当前合同 id（供各页面在成功请求后写入）
   *
   * 典型调用：
   * - ContractsImportPage：导入成功后 resp.contractId 写入
   * - QA/Risk/Assist/ApprovalRecords：请求前由用户输入；请求成功后再写回，保持上下文一致
   */
  function setCurrentContractId(id: string) {
    currentContractId.value = id
  }

  async function refreshContracts() {
    contractsLoading.value = true
    try {
      contracts.value = await listContracts()
    } finally {
      contractsLoading.value = false
    }
  }

  return { currentContractId, contracts, contractsLoading, setCurrentContractId, refreshContracts }
})
