# Contract Agent API 文档

本文档描述合同导入、审批记录导入、问答、风险检查、审批辅助 5 个核心接口的请求/响应示例，并给出错误码语义、幂等与重试建议。

## 1. 通用约定

- Base URL：`http://localhost:8088`
- Content-Type：`application/json`
- 路径前缀：`/api/contracts`
- 当前接口均为 `POST`

## 2. 导入合同

- Method：`POST`
- URL：`/api/contracts/import`
- 用途：导入合同主数据与条款分块，触发后续检索所需数据准备。

### 请求示例

```json
{
  "id": "CTR-RAG-001",
  "type": "procurement",
  "partyAName": "甲方公司",
  "partyBName": "乙方公司",
  "currency": "CNY",
  "amountExTax": 500000,
  "taxRatePct": 13,
  "amountIncTax": 565000,
  "signDate": "2026-04-16",
  "effectiveDate": "2026-04-16",
  "endDate": "2027-04-15",
  "performanceSite": "上海",
  "paymentTermsSummary": "验收后30日付款",
  "businessOwnerDept": "采购部",
  "riskTier": "MEDIUM",
  "vectorDocId": "doc_ctr_001",
  "notes": "首版导入",
  "chunks": [
    {
      "id": "c001",
      "clauseCode": "PAY",
      "clauseTitle": "付款条件",
      "clauseCategory": "财务",
      "textForEmbedding": "甲方在收到发票及验收证明后30个自然日内付款。"
    },
    {
      "id": "c002",
      "clauseCode": "LIA",
      "clauseTitle": "违约责任",
      "clauseCategory": "法务",
      "textForEmbedding": "逾期履约按日万分之五承担违约金。"
    }
  ]
}
```

### 成功响应示例（200）

```json
{
  "contractId": "CTR-RAG-001"
}
```

## 3. 全量导入审批记录

- Method：`POST`
- URL：`/api/contracts/{id}/approval-records/import`
- 用途：对指定合同全量替换审批历史，供补录、迁移或批量初始化使用。

### 请求示例

```json
{
  "records": [
    {
      "id": "AR-001",
      "stepNo": 2,
      "approverRole": "财务经理",
      "decision": "APPROVED",
      "decisionTime": "2026-04-16T10:00:00+08:00",
      "commentSummary": "付款条件清晰，可通过。",
      "linkedPolicyIds": ["POL-TAX-001"],
      "linkedClauseChunkIds": ["c001", "c002"],
      "riskItems": [
        {
          "code": "PAYMENT_REVIEW",
          "severity": "LOW",
          "detail": "付款闭环已确认。",
          "relatedClauseChunkIds": ["c001"],
          "relatedPolicyIds": []
        }
      ],
      "vectorDocId": "doc_ar_001"
    }
  ]
}
```

### 成功响应示例（200）

```json
{
  "contractId": "CTR-RAG-001",
  "importedCount": 1
}
```

## 4. 合同问答

- Method：`POST`
- URL：`/api/contracts/{id}/qa`
- 用途：对指定合同执行 RAG 检索并生成回答。

### 请求示例

```json
{
  "question": "这个合同付款条件是什么？"
}
```

### 成功响应示例（200）

```json
{
  "answer": "根据合同条款，甲方在收到发票及验收证明后30个自然日内付款。",
  "retrievedChunkIds": ["c001"]
}
```

## 4. 风险检查

- Method：`POST`
- URL：`/api/contracts/{id}/risk-check`
- 用途：返回合同风险总结与结构化风险条目。
- 请求体：无

### 成功响应示例（200）

```json
{
  "summary": "当前合同总体风险中等，重点关注违约责任与付款触发条件。",
  "riskItems": [
    {
      "code": "PAYMENT_TRIGGER",
      "severity": "MEDIUM",
      "detail": "付款触发条件依赖验收文件完整性，建议补充异常分支。",
      "relatedClauseChunkIds": ["c001"],
      "relatedPolicyIds": []
    }
  ]
}
```

## 5. 审批辅助

- Method：`POST`
- URL：`/api/contracts/{id}/approval-assist`
- 用途：结合审批角色与关注点返回建议和核对清单。

### 请求示例

```json
{
  "approverRole": "财务经理",
  "focus": "付款节点和发票合规"
}
```

### 成功响应示例（200）

```json
{
  "suggestion": "建议有条件通过，需补充发票异常场景与延期责任。",
  "checklist": [
    "核对付款触发条件是否闭环",
    "确认发票类型与抬头信息",
    "核对逾期违约责任是否对等"
  ]
}
```

## 6. 错误码语义

### 400 Bad Request

- 含义：请求参数校验失败或 JSON 结构错误。
- 常见触发：
  - 必填字段缺失（如 `question` 为空、`approverRole` 为空）。
  - 字段类型不匹配（如金额传字符串）。
  - 日期格式错误。
- 客户端处理：修正请求后重试，不要无脑重放同样请求。

### 404 Not Found

- 含义：合同不存在。
- 常见触发：
  - 调用 `/{id}/qa`、`/{id}/risk-check`、`/{id}/approval-assist` 时 `id` 不存在。
- 客户端处理：先确认合同是否已成功导入，必要时先调用导入接口。

### 409 Conflict

- 含义：资源冲突。
- 常见触发：
  - 导入接口传入的 `id` 已存在（`Contract already exists`）。
- 客户端处理：更换合同 ID，或执行你方定义的“更新/覆盖”流程（若后续支持）。

### 500 Internal Server Error

- 含义：服务端内部异常（模型调用、数据库连接、向量检索失败等）。
- 客户端处理：可按重试策略重试；若持续失败，记录请求体与 trace 信息后排查。

## 7. 幂等与重试建议

### 7.1 幂等性建议

- `POST /import`：
  - 当前语义为“创建”，同一 `id` 重复提交会返回 409，不是天然幂等更新。
  - 建议客户端自行保证唯一 `id`，或后续增加显式“覆盖导入”接口。
- `POST /{id}/qa`、`/risk-check`、`/approval-assist`：
  - 业务上是“读+生成”，可重复调用。
  - 模型输出可能有轻微随机性，不保证文本完全一致。

### 7.2 重试策略建议

- 适合重试：网络抖动、上游模型瞬时失败、5xx。
- 不适合直接重试：400、404、409（先修复输入或状态）。
- 建议参数：
  - 最大重试次数：`2~3`
  - 退避：指数退避（例如 `500ms -> 1s -> 2s`）
  - 超时：客户端请求超时建议 `15~30s`

### 7.3 生产实践建议

- 为导入接口增加 `requestId`（调用方生成）并做去重，可提升“超时后重试”安全性。
- 为全链路日志记录 `contractId`、接口名、traceId，便于定位 404/409/500 根因。
