import { http } from './http'
import type {
  ApprovalAssistRequest,
  ApprovalAssistResponse,
  ApprovalRecordsImportRequest,
  ApprovalRecordsImportResponse,
  ContractImportRequest,
  ContractImportResponse,
  ContractQaRequest,
  ContractQaResponse,
  ContractRiskCheckResponse,
} from '../types/contracts'

/**
 * 合同相关 API（业务接口层）
 *
 * 目标：
 * - 把“后端接口清单”抽象成可复用的 TypeScript 函数
 * - 让页面层（src/pages）只关心“调用哪个函数”和“展示结果”，而不是拼 URL/处理 axios 细节
 *
 * 调用链：
 * pages/*.vue -> 本文件函数 -> api/http.ts 的 http 实例（baseURL=/api）
 * -> Vite 代理（vite.config.ts）-> 后端 http://localhost:8088
 *
 * 注意：
 * - 本文件只负责接口路径/参数/类型，不做 UI 交互、不做错误提示文案
 * - 错误会在 api/http.ts 中被归一化成 NormalizedHttpError，页面 catch 到的是“统一形状的错误”
 * - encodeURIComponent(contractId) 用于保证 contractId 中包含特殊字符时仍能构造合法 URL
 * - 因为 http.baseURL 已经是 `/api`，所以这里只需要写 `/contracts/...`（不需要重复 `/api` 前缀）
 *
 * 与文档的对应关系：
 * - docs/API_REFERENCE.md 第 2~5 节分别对应下方 5 个函数
 */

/** 2.1 导入合同（POST /api/contracts/import） */
export async function importContract(
  payload: ContractImportRequest,
): Promise<ContractImportResponse> {
  const { data } = await http.post<ContractImportResponse>(
    '/contracts/import',
    payload,
  )
  return data
}

/** 2.2 全量导入审批记录（POST /api/contracts/{id}/approval-records/import） */
export async function importApprovalRecords(
  contractId: string,
  payload: ApprovalRecordsImportRequest,
): Promise<ApprovalRecordsImportResponse> {
  const { data } = await http.post<ApprovalRecordsImportResponse>(
    `/contracts/${encodeURIComponent(contractId)}/approval-records/import`,
    payload,
  )
  return data
}

/** 2.3 合同问答（POST /api/contracts/{id}/qa） */
export async function askContract(
  contractId: string,
  payload: ContractQaRequest,
): Promise<ContractQaResponse> {
  const { data } = await http.post<ContractQaResponse>(
    `/contracts/${encodeURIComponent(contractId)}/qa`,
    payload,
  )
  return data
}

/** 2.4 风险检查（POST /api/contracts/{id}/risk-check；请求体为空） */
export async function checkContractRisk(
  contractId: string,
): Promise<ContractRiskCheckResponse> {
  const { data } = await http.post<ContractRiskCheckResponse>(
    `/contracts/${encodeURIComponent(contractId)}/risk-check`,
  )
  return data
}

/** 2.5 审批辅助（POST /api/contracts/{id}/approval-assist） */
export async function assistApproval(
  contractId: string,
  payload: ApprovalAssistRequest,
): Promise<ApprovalAssistResponse> {
  const { data } = await http.post<ApprovalAssistResponse>(
    `/contracts/${encodeURIComponent(contractId)}/approval-assist`,
    payload,
  )
  return data
}
