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

/** 2.1 导入合同 */
export async function importContract(
  payload: ContractImportRequest,
): Promise<ContractImportResponse> {
  const { data } = await http.post<ContractImportResponse>(
    '/contracts/import',
    payload,
  )
  return data
}

/** 2.2 全量导入审批记录 */
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

/** 2.3 合同问答 */
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

/** 2.4 风险检查 */
export async function checkContractRisk(
  contractId: string,
): Promise<ContractRiskCheckResponse> {
  const { data } = await http.post<ContractRiskCheckResponse>(
    `/contracts/${encodeURIComponent(contractId)}/risk-check`,
  )
  return data
}

/** 2.5 审批辅助 */
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
