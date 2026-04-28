/**
 * 类型层（与后端 API 文档对齐的 DTO）
 *
 * 作用：
 * - 为 API 封装层（src/api/contracts.ts）提供入参/出参的类型约束
 * - 为页面层（src/pages/*.vue）提供结构化数据的类型提示（减少“字段名拼错/类型传错”）
 *
 * 数据来源：
 * - docs/API_REFERENCE.md（请求/响应示例）
 * - docs/FRONTEND_DEV_GUIDE.md（推荐的数据模型）
 *
 * 约定说明：
 * - 风险等级字段（severity/riskTier）沿用 "LOW" | "MEDIUM" | "HIGH"
 * - 同时保留 string 以兼容后端未来扩展枚举值（避免前端因新增枚举直接编译报错）
 */

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | string
export type RiskTier = 'LOW' | 'MEDIUM' | 'HIGH' | string

// ============ 2. 导入合同 ============
/**
 * “导入合同”是整个前端联调的起点：
 * - 其他 4 个接口（qa/risk-check/approval-assist/approval-records/import）都要求合同已存在
 * - 因此通常先调用 POST /api/contracts/import，拿到 contractId，再去调用其他接口
 */

export interface ContractChunk {
  id: string
  clauseCode?: string
  clauseTitle?: string
  clauseCategory?: string
  textForEmbedding: string
}

export interface ContractImportRequest {
  id: string
  type?: string
  partyAName?: string
  partyBName?: string
  currency?: string
  amountExTax?: number
  taxRatePct?: number
  amountIncTax?: number
  /** YYYY-MM-DD */
  signDate?: string
  /** YYYY-MM-DD */
  effectiveDate?: string
  /** YYYY-MM-DD */
  endDate?: string
  performanceSite?: string
  paymentTermsSummary?: string
  businessOwnerDept?: string
  riskTier?: RiskTier
  vectorDocId?: string
  notes?: string
  chunks: ContractChunk[]
}

export interface ContractImportResponse {
  contractId: string
}

// ============ 3. 导入审批记录 ============
/**
 * 审批记录导入是“全量替换”语义：
 * - 前端会把用户提供的 records 整体提交给后端，后端按合同维度替换历史审批数据
 * - 因为结构层级较深（records[].riskItems[] 等），页面里先用 JSON 文本域方式最省力
 */

export interface ApprovalRiskItem {
  code: string
  severity: Severity
  detail: string
  relatedClauseChunkIds?: string[]
  relatedPolicyIds?: string[]
}

export interface ApprovalRecord {
  id: string
  stepNo?: number
  approverRole?: string
  decision?: string
  /** ISO-8601 with offset，例如 2026-04-16T10:00:00+08:00 */
  decisionTime?: string
  commentSummary?: string
  linkedPolicyIds?: string[]
  linkedClauseChunkIds?: string[]
  riskItems?: ApprovalRiskItem[]
  vectorDocId?: string
}

export interface ApprovalRecordsImportRequest {
  records: ApprovalRecord[]
}

export interface ApprovalRecordsImportResponse {
  contractId: string
  importedCount: number
}

// ============ 4. 合同问答 ============
/**
 * 问答接口是“读 + 生成”：
 * - 输入一个 question，返回 answer + retrievedChunkIds（命中的条款 chunk id 列表）
 * - 目前后端不提供 chunk 内容回查，所以前端先展示 chunkId（联调足够）
 */

export interface ContractQaRequest {
  question: string
}

export interface ContractQaResponse {
  answer: string
  retrievedChunkIds: string[]
}

// ============ 5. 风险检查 ============
/**
 * 风险检查接口无请求体：
 * - 只要提供 contractId，后端返回 summary + riskItems（结构化风险条目）
 * - 页面可先做最小展示：摘要 + 列表（code/severity/detail）
 */

export interface ContractRiskItem {
  code: string
  severity: Severity
  detail: string
  relatedClauseChunkIds: string[]
  relatedPolicyIds: string[]
}

export interface ContractRiskCheckResponse {
  summary: string
  riskItems: ContractRiskItem[]
}

// ============ 6. 审批辅助 ============
/**
 * 审批辅助接口：
 * - 输入 approverRole（审批角色）和 focus（关注点）
 * - 返回 suggestion（建议）和 checklist（核对清单）
 */

export interface ApprovalAssistRequest {
  approverRole: string
  focus: string
}

export interface ApprovalAssistResponse {
  suggestion: string
  checklist: string[]
}
