import { http } from './http'
import type {
  PolicyKnowledgeDetail,
  PolicyKnowledgeFileParseResponse,
  PolicyKnowledgeImportRequest,
  PolicyKnowledgeImportResponse,
  PolicyQaRequest,
  PolicyQaResponse,
} from '../types/contracts'

/**
 * 政策/制度知识库相关 API
 *
 * 对应后端接口：
 * - POST /api/policies/import（导入政策制度条目）
 *
 * 调用链：
 * pages/*.vue -> 本文件函数 -> api/http.ts 的 http 实例（baseURL=/api）
 * -> Vite 代理（vite.config.ts）-> 后端 http://localhost:8088
 */

/** 批量导入政策/制度条目（POST /api/policies/import） */
export async function importPolicyKnowledge(
  payload: PolicyKnowledgeImportRequest,
): Promise<PolicyKnowledgeImportResponse> {
  const { data } = await http.post<PolicyKnowledgeImportResponse>(
    '/policies/import',
    payload,
  )
  return data
}

/** 查询制度依据详情（GET /api/policies/{policyId}） */
export async function getPolicyKnowledge(
  policyId: string,
): Promise<PolicyKnowledgeDetail> {
  const { data } = await http.get<PolicyKnowledgeDetail>(
    `/policies/${encodeURIComponent(policyId)}`,
  )
  return data
}

/** 政策/制度问答（POST /api/policies/qa） */
export async function askPolicyKnowledge(
  payload: PolicyQaRequest,
): Promise<PolicyQaResponse> {
  const { data } = await http.post<PolicyQaResponse>('/policies/qa', payload)
  return data
}

/** 解析政策/制度文件为导入草稿（POST /api/policies/parse-file） */
export async function parsePolicyKnowledgeFile(
  file: File,
): Promise<PolicyKnowledgeFileParseResponse> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<PolicyKnowledgeFileParseResponse>(
    '/policies/parse-file',
    form,
  )
  return data
}
