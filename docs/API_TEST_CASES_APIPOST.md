# Contract Agent MVP 接口测试用例（ApiPost）

- Base URL：`http://localhost:8088`
- 通用 Header：`Content-Type: application/json`
- 说明：以下用例按 ApiPost 可直接复制的方式给出（Method + URL + Body + 预期）

---

## 一、`POST /api/contracts/import`（导入合同）

### 用例 1：正常导入（带 chunks）
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/import`
- Body（raw/json）：
```json
{
  "id": "CTR-RAG-TEST-001",
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
  "vectorDocId": "doc_ctr_test_001",
  "notes": "测试导入",
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
    },
    {
      "id": "c003",
      "clauseCode": "DEL",
      "clauseTitle": "交付期限",
      "clauseCategory": "履约",
      "textForEmbedding": "乙方应于2026年05月30日前完成全部货物交付。"
    },
    {
      "id": "c004",
      "clauseCode": "DIS",
      "clauseTitle": "争议解决",
      "clauseCategory": "法务",
      "textForEmbedding": "双方因合同产生的争议应协商解决，协商不成时提交上海仲裁委员会仲裁。"
    },
    {
      "id": "c005",
      "clauseCode": "CONF",
      "clauseTitle": "保密条款",
      "clauseCategory": "法律",
      "textForEmbedding": "双方对在履行合同过程中获悉的商业秘密承担保密义务。"
    }
  ]
}
```
- 预期：`200`，返回 `contractId = CTR-RAG-TEST-001`

### 用例 2：重复 id 导入
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/import`
- Body：与用例 1 相同
- 预期：`409 Conflict`，提示 `Contract already exists`

### 用例 3：缺少必填字段
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/import`
- Body（缺少 `partyAName`）：
```json
{
  "id": "CTR-RAG-TEST-ERR-001",
  "type": "procurement",
  "partyBName": "乙方公司",
  "currency": "CNY",
  "amountExTax": 1000,
  "taxRatePct": 13,
  "amountIncTax": 1130,
  "signDate": "2026-04-16",
  "effectiveDate": "2026-04-16",
  "endDate": "2026-05-16",
  "chunks": [
    {
      "id": "c001",
      "clauseCode": "PAY",
      "clauseTitle": "付款条件",
      "clauseCategory": "财务",
      "textForEmbedding": "test"
    }
  ]
}
```
- 预期：`400 Bad Request`

---

## 二、`POST /api/contracts/{id}/approval-records/import`（全量导入审批记录）

### 用例 1：正常全量导入
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/approval-records/import`
- Body：
```json
{
  "records": [
    {
      "id": "AR-001",
      "stepNo": 1,
      "approverRole": "法务",
      "decision": "CONDITIONAL_APPROVED",
      "decisionTime": "2026-04-16T10:00:00+08:00",
      "commentSummary": "建议补充解除条款对等性。",
      "linkedPolicyIds": ["POL-LEGAL-001"],
      "linkedClauseChunkIds": ["c002"],
      "riskItems": [
        {
          "code": "TERMINATION_BALANCE",
          "severity": "MEDIUM",
          "detail": "解除条件需双方对等。",
          "relatedClauseChunkIds": ["c002"],
          "relatedPolicyIds": ["POL-LEGAL-001"]
        }
      ],
      "vectorDocId": "doc_ar_001"
    },
    {
      "id": "AR-002",
      "stepNo": 2,
      "approverRole": "财务经理",
      "decision": "APPROVED",
      "decisionTime": "2026-04-16T11:00:00+08:00",
      "commentSummary": "付款条件清晰，可通过。",
      "linkedPolicyIds": ["POL-TAX-001"],
      "linkedClauseChunkIds": ["c001"],
      "riskItems": []
    }
  ]
}
```
- 预期：`200`，`importedCount = 2`

### 用例 2：合同不存在
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-NOT-EXIST/approval-records/import`
- Body：同用例 1
- 预期：`404 Not Found`

### 用例 3：stepNo 非法
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/approval-records/import`
- Body：
```json
{
  "records": [
    {
      "id": "AR-ERR-001",
      "stepNo": 0,
      "approverRole": "法务",
      "decision": "APPROVED"
    }
  ]
}
```
- 预期：`400 Bad Request`

### 用例 4：重复导入覆盖验证
- 先执行一次用例 1，再用以下 body 再导入
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/approval-records/import`
- Body：
```json
{
  "records": [
    {
      "id": "AR-NEW-001",
      "stepNo": 1,
      "approverRole": "总监",
      "decision": "APPROVED",
      "commentSummary": "覆盖旧记录"
    }
  ]
}
```
- 预期：`200` 且 `importedCount = 1`（语义是全量替换）

---

## 三、`POST /api/contracts/{id}/qa`（合同问答）

### 用例 1：正常问答
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/qa`
- Body：
```json
{
  "question": "这个合同付款条件是什么？"
}
```
- 预期：`200`，返回 `answer` 与 `retrievedChunkIds`

### 用例 2：合同不存在
- URL：`{{baseUrl}}/api/contracts/CTR-NOT-EXIST/qa`
- Body：
```json
{
  "question": "付款条件是什么？"
}
```
- 预期：`404`

### 用例 3：question 为空
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/qa`
- Body：
```json
{
  "question": ""
}
```
- 预期：`400`

---

## 四、`POST /api/contracts/{id}/risk-check`（风险检查）

### 用例 1：正常风险检查
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/risk-check`
- Body：空
- 预期：`200`，包含 `summary` 和 `riskItems`

### 用例 2：合同不存在
- URL：`{{baseUrl}}/api/contracts/CTR-NOT-EXIST/risk-check`
- Body：空
- 预期：`404`

### 用例 3：审批记录已导入后再检查
- 前置：先执行审批记录导入用例
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/risk-check`
- 预期：`200`，风险结果应可结合审批历史（不报 500）

---

## 五、`POST /api/contracts/{id}/approval-assist`（审批辅助）

### 用例 1：正常审批辅助
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/approval-assist`
- Body：
```json
{
  "approverRole": "财务经理",
  "focus": "付款节点"
}
```
- 预期：`200`，包含 `suggestion` 与 `checklist`

### 用例 2：approverRole 为空
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/approval-assist`
- Body：
```json
{
  "approverRole": "",
  "focus": "付款节点"
}
```
- 预期：`400`

### 用例 3：合同不存在
- URL：`{{baseUrl}}/api/contracts/CTR-NOT-EXIST/approval-assist`
- Body：
```json
{
  "approverRole": "财务经理",
  "focus": "付款节点"
}
```
- 预期：`404`

### 用例 4：仅必填字段
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/approval-assist`
- Body：
```json
{
  "approverRole": "法务"
}
```
- 预期：`200`

---

## 附：ApiPost 环境变量建议

- `baseUrl = http://localhost:8088`
- 可新增 `contractId = CTR-RAG-TEST-001`，URL 中统一写 `{{contractId}}`
