# Contract Agent API 文档

本文档描述合同导入、政策/制度知识库导入、审批记录导入、问答、风险检查、审批辅助 6 个核心接口的请求/响应示例，并给出错误码语义、幂等与重试建议。

## 1. 通用约定

- Base URL：`http://localhost:8088`
- Content-Type：`application/json`
- 合同路径前缀：`/api/contracts`
- 政策/制度路径前缀：`/api/policies`
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

## 3. 导入政策/制度知识库

- Method：`POST`
- URL：`/api/policies/import`
- 用途：导入跨合同共享的政策/制度条目，触发制度 RAG 所需数据准备。风险检查和审批辅助可通过 `policyId` 引用具体制度依据。
- 导入语义：建议支持按 `policyId` 覆盖更新；`policyId` 应稳定不变。

### 请求示例

```json
{
  "policies": [
    {
      "policyId": "POL-FIN-001",
      "policyDomain": "财务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "预付;全额预付;验收前支付100%;先款后货;先款后服务",
      "controlObjective": "付款风险",
      "policyTextForEmbedding": "原则上应避免无担保的高比例预付或验收前支付100%。如业务确需预付，应要求乙方提供保函/履约保证金/等额担保，并在合同中设置里程碑验收与分期付款。",
      "requiredEvidence": "付款计划表;担保文件;业务必要性说明;里程碑/验收约定",
      "escalationRole": "财务负责人"
    },
    {
      "policyId": "POL-TAX-001",
      "policyDomain": "税务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "含税价;不含税;发票类型;税率;征收率",
      "controlObjective": "进项抵扣与发票合规",
      "policyTextForEmbedding": "合同应明确不含税金额、适用税率/征收率、发票类型与开票信息。对混合销售、差额计税、跨境服务等特殊情形，应明确税务处理边界与责任分担。",
      "requiredEvidence": "税务条款截图;税率政策依据/口径说明;开票信息",
      "escalationRole": "税务岗/财务负责人"
    }
  ]
}
```

### 成功响应示例（200）

```json
{
  "importedCount": 2,
  "policyIds": ["POL-FIN-001", "POL-TAX-001"]
}
```

### 向量同步降级响应示例（200，业务表已成功）

业务表 `policy_knowledge` 是事务化幂等写入；向量库为派生检索索引，写入异常（如嵌入服务波动、`vector_store` 写入失败等）不会让接口返回 500，而是通过 `vectorIngestionWarning` 字段提示客户端，可基于相同请求体重试 `POST /api/policies/import`（请求是幂等的）以补偿向量同步。

```json
{
  "importedCount": 2,
  "policyIds": ["POL-FIN-001", "POL-TAX-001"],
  "vectorIngestionWarning": "Vector store sync failed: DataIntegrityViolationException: ... . Business table updated; retry import to re-sync."
}
```

> 写入策略：`PolicyVectorIngestionService` 与 `ContractVectorIngestionService` 都通过 `VectorBatchWriter` 写入：按 `app.embedding.batch-size`（默认 10，适配 DashScope 通用 embedding 单次 ≤10 条限制）切片，每个切片在 `add` 前先按业务派生 id `delete`，保证既能满足上游 embedding API 批次上限，又能让重复 `policyId`/`chunkId` 多次导入对向量库幂等。

## 4. 全量导入审批记录

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

## 5. 合同问答

- Method：`POST`
- URL：`/api/contracts/{id}/qa`
- 用途：对指定合同执行双通道 RAG 检索并生成回答。合同通道限定在当前 `contractId`，制度通道限定为当前合同类型适用制度。

### 请求示例

```json
{
  "question": "这个合同付款条件是什么？"
}
```

### 成功响应示例（200）

```json
{
  "answer": "根据合同条款，甲方在收到发票及验收证明后30个自然日内付款。该付款安排与制度要求中“付款条件应包含验收、发票、对账等可验证要素”的原则一致。",
  "retrievedChunkIds": ["c001"],
  "retrievedPolicyIds": ["POL-PAY-001"]
}
```

## 6. 风险检查

- Method：`POST`
- URL：`/api/contracts/{id}/risk-check`
- 用途：返回合同风险总结与结构化风险条目。每个风险项应尽量关联触发条款与具体制度依据。
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
      "relatedPolicyIds": ["POL-PAY-001"],
      "requiredEvidence": ["付款条款截图", "控制点清单", "验收/对账要求"],
      "escalationRole": "财务负责人"
    }
  ]
}
```

## 7. 审批辅助

- Method：`POST`
- URL：`/api/contracts/{id}/approval-assist`
- 用途：结合审批角色、关注点、合同条款命中和制度依据命中返回建议和核对清单。

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
  ],
  "retrievedChunkIds": ["c001"],
  "retrievedPolicyIds": ["POL-PAY-001", "POL-TAX-001"]
}
```

## 8. 错误码语义

### 400 Bad Request

- 含义：请求参数校验失败或 JSON 结构错误。
- 常见触发：
  - 必填字段缺失（如 `question` 为空、`approverRole` 为空）。
  - 制度导入缺少 `policyId`、`policyDomain` 或 `policyTextForEmbedding`。
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
  - 制度导入策略为“禁止覆盖”时传入已存在的 `policyId`。
- 客户端处理：更换合同 ID，或执行你方定义的“更新/覆盖”流程（若后续支持）。

### 500 Internal Server Error

- 含义：服务端内部异常（模型调用、数据库连接、向量检索失败等）。
- 客户端处理：可按重试策略重试；若持续失败，记录请求体与 trace 信息后排查。
- 注意：`POST /api/policies/import` 的向量同步异常不会触发 500，会通过 200 响应中的 `vectorIngestionWarning` 字段提示，业务表已确认成功。

### 503 Service Unavailable

- 含义：依赖资源未就绪，请求在当前部署形态下无法处理。
- 常见触发：
  - 调用 `POST /api/policies/import` 时未配置 PostgreSQL 业务库或 MyBatis Mapper（`Policy knowledge repository is not available`）。
- 客户端处理：检查后端 `application.yml` 中数据源与 MyBatis 配置是否生效；本地或测试环境暂不支持该接口。

## 9. 幂等与重试建议

### 9.1 幂等性建议

- `POST /import`：
  - 当前语义为“创建”，同一 `id` 重复提交会返回 409，不是天然幂等更新。
  - 建议客户端自行保证唯一 `id`，或后续增加显式“覆盖导入”接口。
- `POST /api/policies/import`：
  - 业务表 `policy_knowledge` 与向量库 `vector_store` 均按 `policyId` 幂等覆盖，重复提交相同 `policyId` 会以最新内容更新两端。
  - 若返回 200 且带 `vectorIngestionWarning`，表示业务表已落库但向量库未同步成功，**可基于相同请求体直接重试**触发向量库再写入。
  - 同一请求内出现重复 `policyId` 时，后出现的条目会覆盖先前条目（last-wins）。
- `POST /{id}/qa`、`/risk-check`、`/approval-assist`：
  - 业务上是“读+生成”，可重复调用。
  - 模型输出可能有轻微随机性，不保证文本完全一致。

### 9.2 重试策略建议

- 适合重试：网络抖动、上游模型瞬时失败、5xx。
- 不适合直接重试：400、404、409（先修复输入或状态）。
- 建议参数：
  - 最大重试次数：`2~3`
  - 退避：指数退避（例如 `500ms -> 1s -> 2s`）
  - 超时：客户端请求超时建议 `15~30s`

### 9.3 生产实践建议

- 为导入接口增加 `requestId`（调用方生成）并做去重，可提升“超时后重试”安全性。
- 为全链路日志记录 `contractId`、接口名、traceId，便于定位 404/409/500 根因。
- 为制度导入记录 `policyId`、导入批次号、覆盖数量和失败行号，便于追踪制度依据变更对风险结果的影响。
