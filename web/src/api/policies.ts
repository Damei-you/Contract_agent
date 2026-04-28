import { http } from './http'
import type {
  PolicyKnowledgeImportRequest,
  PolicyKnowledgeImportResponse,
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
