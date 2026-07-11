# Contract Agent API 文档

本文档描述合同文件解析、合同导入、政策/制度文件解析、政策/制度知识库导入、审批记录导入、问答、风险检查、审批辅助等核心接口的请求/响应示例，并给出错误码语义、幂等与重试建议。

## 1. 通用约定

- Base URL：`http://localhost:8088`
- Content-Type：`application/json`
- 合同路径前缀：`/api/contracts`
- 政策/制度路径前缀：`/api/policies`
- 导入、解析和模型生成类接口为 `POST`；证据详情查询接口为 `GET`

## 2. 导入合同

### 2.0a 查询合同列表

- 方法：`GET`
- URL：`/api/contracts`
- 用途：查询合同选择器所需的精简合同信息，结果按合同 id 排序。
- 响应示例：

```json
[
  {
    "id": "CTR-RAG-001",
    "type": "PROCUREMENT",
    "partyAName": "甲方公司",
    "partyBName": "乙方公司"
  }
]
```

- Method：`POST`
- URL：`/api/contracts/import`
- 用途：导入合同主数据与条款分块，触发后续检索所需数据准备。
- 导入语义：同一 `id` 已存在时，首次提交不写库并返回 `requiresConfirmation=true`；客户端确认后以相同请求体追加 `overwriteConfirmed=true`，服务端才覆盖主数据与条款，并按现有导入语义清空该合同审批记录。

### 2.0 解析合同文件为导入草稿

- Method：`POST`
- URL：`/api/contracts/parse-file`
- Content-Type：`multipart/form-data`
- 表单字段：`file`
- 用途：使用 Apache Tika 从 PDF、Word 或文本文件中抽取正文，并生成可编辑的合同导入草稿。该接口不直接落库，用户确认草稿后再提交 `POST /api/contracts/import`。

### 成功响应示例（200）

```json
{
  "document": {
    "filename": "采购合同.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "detectedContentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "size": 24576,
    "textLength": 4180,
    "warnings": ["未识别到不含税金额，请导入前补齐 amountExTax。"]
  },
  "draft": {
    "id": "CTR-FILE-A1B2C3D4E5F6",
    "type": "procurement",
    "partyAName": "甲方公司",
    "partyBName": "乙方公司",
    "currency": "CNY",
    "amountExTax": null,
    "taxRatePct": 13,
    "amountIncTax": 565000,
    "signDate": "2026-04-16",
    "effectiveDate": "2026-04-16",
    "endDate": "2027-04-15",
    "performanceSite": "上海",
    "paymentTermsSummary": "甲方在收到发票及验收证明后30个自然日内付款",
    "businessOwnerDept": "",
    "riskTier": "MEDIUM",
    "vectorDocId": null,
    "notes": "由文件解析生成草稿，来源文件：采购合同.docx。请确认主数据后再导入。",
    "chunks": [
      {
        "id": "CH-FILE-001",
        "clauseCode": "CH-001",
        "clauseTitle": "付款条件",
        "clauseCategory": "财务",
        "textForEmbedding": "第一条 付款条件\n甲方在收到发票及验收证明后30个自然日内付款。"
      }
    ],
    "overwriteConfirmed": false
  },
  "chunkCount": 1
}
```

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
  ],
  "overwriteConfirmed": false
}
```

### 成功响应示例（200）

```json
{
  "contractId": "CTR-RAG-001",
  "imported": true,
  "overwritten": false,
  "requiresConfirmation": false,
  "message": "合同已导入。"
}
```

### 覆盖确认响应示例（200，不写库）

```json
{
  "contractId": "CTR-RAG-001",
  "imported": false,
  "overwritten": false,
  "requiresConfirmation": true,
  "message": "合同 ID 已存在。确认后将覆盖合同主数据和条款，并清空该合同审批记录。"
}
```

### 确认覆盖后的响应示例（200）

```json
{
  "contractId": "CTR-RAG-001",
  "imported": true,
  "overwritten": true,
  "requiresConfirmation": false,
  "message": "合同已覆盖导入。"
}
```

## 3. 导入政策/制度知识库

- Method：`POST`
- URL：`/api/policies/import`
- 用途：导入跨合同共享的政策/制度条目，触发制度 RAG 所需数据准备。风险检查和审批辅助可通过 `policyId` 引用具体制度依据。
- 导入语义：建议支持按 `policyId` 覆盖更新；`policyId` 应稳定不变。

### 3.0 解析政策/制度文件为导入草稿

- Method：`POST`
- URL：`/api/policies/parse-file`
- Content-Type：`multipart/form-data`
- 表单字段：`file`
- 用途：使用 Apache Tika 从 PDF、Word 或文本文件中抽取正文，并按条款标题切分为可编辑的制度知识库导入草稿。该接口不直接落库，用户确认草稿后再提交 `POST /api/policies/import`。

### 成功响应示例（200）

```json
{
  "document": {
    "filename": "财务制度.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "detectedContentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "size": 20480,
    "textLength": 3200,
    "warnings": []
  },
  "draft": {
    "policies": [
      {
        "policyId": "POL-FILE-6F2A91B1-001",
        "policyDomain": "财务合规",
        "appliesToContractType": "procurement",
        "severity": "HIGH",
        "triggerKeywords": "预付款;保函",
        "controlObjective": "采购合同预付款比例不得超过合同总金额的30%，且需提供等额保函",
        "policyTextForEmbedding": "第一条 采购合同预付款比例不得超过合同总金额的30%，且需提供等额保函。",
        "requiredEvidence": "采购合同预付款比例不得超过合同总金额的30%，且需提供等额保函",
        "escalationRole": "",
        "vectorDocId": null,
        "updatedAt": null
      }
    ]
  },
  "policyCount": 1
}
```

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

## 4.1 政策制度问答

- Method：`POST`
- URL：`/api/policies/qa`
- 用途：对政策/制度知识库执行 RAG 检索并生成回答；不依赖具体合同，可选传入合同类型收敛适用制度范围。

### 请求示例

```json
{
  "question": "付款节点和发票合规有哪些制度要求？",
  "contractType": "procurement"
}
```

### 成功响应示例（200）

```json
{
  "answer": "采购合同付款前应核验验收材料、对账单和合法有效发票；如制度要求开票时点或审批窗口，应在付款节点前完成留痕。",
  "retrievedPolicyIds": ["POL-PAY-001", "POL-TAX-001"],
  "agentTrace": [
    {
      "agentName": "PolicyEvidenceAgent",
      "summary": "已按合同类型范围「采购合同」检索制度依据，命中 2 条。",
      "retrievedChunkIds": [],
      "retrievedPolicyIds": ["POL-PAY-001", "POL-TAX-001"]
    }
  ]
}
```

## 5. 合同问答

- Method：`POST`
- URL：`/api/contracts/{id}/qa`
- 用途：对指定合同执行问答。默认只检索当前合同条款；传入 `includePolicyEvidence=true` 时，额外按当前合同类型检索适用制度依据。

### 请求示例

```json
{
  "question": "这个合同付款条件是什么？",
  "includePolicyEvidence": false
}
```

### 成功响应示例（200）

```json
{
  "answer": "根据合同条款，甲方在收到发票及验收证明后30个自然日内付款。",
  "retrievedChunkIds": ["c001"],
  "retrievedPolicyIds": [],
  "agentTrace": [
    {
      "agentName": "ContractFactAgent",
      "summary": "已按问题检索合同条款，命中 1 个合同条款片段。",
      "retrievedChunkIds": ["c001"],
      "retrievedPolicyIds": []
    }
  ]
}
```

### 同时引用制度依据的请求示例

```json
{
  "question": "这个合同付款条件是什么？",
  "includePolicyEvidence": true
}
```

此时响应会额外返回制度依据命中，例如：

```json
{
  "retrievedPolicyIds": ["POL-PAY-001"],
  "agentTrace": [
    {
      "agentName": "PolicyEvidenceAgent",
      "summary": "已按问题和合同类型检索制度依据，命中 1 条制度依据。",
      "retrievedChunkIds": [],
      "retrievedPolicyIds": ["POL-PAY-001"]
    }
  ]
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
  ],
  "agentTrace": [
    {
      "agentName": "ContractFactAgent",
      "summary": "已加载合同事实，并命中 1 个合同条款片段。",
      "retrievedChunkIds": ["c001"],
      "retrievedPolicyIds": []
    },
    {
      "agentName": "PolicyEvidenceAgent",
      "summary": "已按合同类型「采购合同」命中 1 条制度依据。",
      "retrievedChunkIds": [],
      "retrievedPolicyIds": ["POL-PAY-001"]
    }
  ]
}
```

### 6.1 查询合同条款块详情

- Method：`GET`
- URL：`/api/contracts/{id}/chunks/{chunkId}`
- 用途：按合同问答、风险检查或审批辅助返回的 `agentTrace[].retrievedChunkIds` 查询具体条款正文。

### 成功响应示例（200）

```json
{
  "id": "c001",
  "contractId": "CTR-RAG-001",
  "clauseCode": "PAY",
  "clauseTitle": "付款条件",
  "clauseCategory": "财务",
  "sourceSection": "第三条",
  "textForEmbedding": "甲方在收到发票及验收证明后30个自然日内付款。"
}
```

### 6.2 查询制度依据详情

- Method：`GET`
- URL：`/api/policies/{policyId}`
- 用途：按合同问答、政策制度问答、风险检查或审批辅助返回的 `agentTrace[].retrievedPolicyIds` 查询具体制度依据正文。

### 成功响应示例（200）

```json
{
  "policyId": "POL-PAY-001",
  "policyDomain": "财务合规",
  "appliesToContractType": "采购合同;服务合同",
  "severity": "HIGH",
  "triggerKeywords": "付款;验收;发票",
  "controlObjective": "付款条件应包含验收、发票、对账等可验证要素",
  "policyTextForEmbedding": "付款条件应明确验收、发票、对账等可验证要素，并保留付款审批证据。",
  "requiredEvidence": "付款条款截图;验收单;发票信息",
  "escalationRole": "财务负责人",
  "vectorDocId": "doc_pol_POL-PAY-001",
  "updatedAt": "2026-04-16T10:00:00+08:00"
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
  "retrievedPolicyIds": ["POL-PAY-001", "POL-TAX-001"],
  "agentTrace": [
    {
      "agentName": "ContractFactAgent",
      "summary": "已加载合同事实，并命中 1 个合同条款片段。",
      "retrievedChunkIds": ["c001"],
      "retrievedPolicyIds": []
    },
    {
      "agentName": "PolicyEvidenceAgent",
      "summary": "已按合同类型「采购合同」命中 2 条制度依据。",
      "retrievedChunkIds": [],
      "retrievedPolicyIds": ["POL-PAY-001", "POL-TAX-001"]
    }
  ]
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
  - 调用 `/api/contracts/{id}/chunks/{chunkId}` 或 `/api/policies/{policyId}` 时证据 id 不存在。
- 客户端处理：先确认合同是否已成功导入，必要时先调用导入接口。

### 409 Conflict

- 含义：资源冲突。
- 常见触发：
  - 制度导入策略为“禁止覆盖”时传入已存在的 `policyId`。
- 客户端处理：更换资源 ID，或执行你方定义的“更新/覆盖”流程。
- 注意：合同导入同 `id` 重复提交不再返回 409，而是返回 200 + `requiresConfirmation=true`，等待客户端二次确认。

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
  - 当前语义为“覆盖前确认”：同一 `id` 首次重复提交返回 200 + `requiresConfirmation=true`，不写库。
  - 客户端确认后携带 `overwriteConfirmed=true` 重试同一请求体，服务端按 `id` 幂等覆盖主数据与条款。
  - 覆盖导入会清空该合同已有审批记录；客户端应在确认提示中明确告知用户。
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
