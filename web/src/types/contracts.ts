// 与 docs/API_REFERENCE.md 对齐的请求/响应 DTO
// 所有风险等级字段沿用 "LOW" | "MEDIUM" | "HIGH"，保留 string 以兼容后端未来扩展
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | string
export type RiskTier = 'LOW' | 'MEDIUM' | 'HIGH' | string

// ============ 2. 导入合同 ============

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

export interface ContractQaRequest {
  question: string
}

export interface ContractQaResponse {
  answer: string
  retrievedChunkIds: string[]
}

// ============ 5. 风险检查 ============

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

export interface ApprovalAssistRequest {
  approverRole: string
  focus: string
}

export interface ApprovalAssistResponse {
  suggestion: string
  checklist: string[]
}
