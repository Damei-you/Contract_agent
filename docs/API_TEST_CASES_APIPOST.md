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

## 二、`POST /api/policies/import`（导入政策/制度知识库）

### 用例 1：正常导入制度条目
- Method：`POST`
- URL：`{{baseUrl}}/api/policies/import`
- Body：
```json
{
  "policies": [
    {
      "policyId": "POL-PAY-001",
      "policyDomain": "资金支付",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "付款条件;先付款;见票付款;见票即付;无条件付款",
      "controlObjective": "支付条件与控制",
      "policyTextForEmbedding": "付款条件应至少包含可验证要素（验收合格、交付物清单、合规发票、对账确认）。对无条件付款、见票即付且不要求验收的条款应要求补充控制条件或降低比例。",
      "requiredEvidence": "付款条款截图;控制点清单;验收/对账要求",
      "escalationRole": "财务负责人"
    },
    {
      "policyId": "POL-PAY-002",
      "policyDomain": "资金支付",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "MEDIUM",
      "triggerKeywords": "付款周期;收到发票后N天;账期",
      "controlObjective": "资金计划与逾期风险",
      "policyTextForEmbedding": "付款周期应与公司资金计划匹配并留有验票、对账、审批窗口。对过短账期或不合理付款时限，应协商调整或增加资料完整性前提。",
      "requiredEvidence": "付款周期条款截图;资金计划说明",
      "escalationRole": "财务经理"
    },
    {
      "policyId": "POL-PAY-003",
      "policyDomain": "资金支付",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "MEDIUM",
      "triggerKeywords": "分期付款;里程碑付款;尾款",
      "controlObjective": "降低履约风险",
      "policyTextForEmbedding": "建议采用分期或里程碑付款并保留尾款形成履约约束；尾款释放应与最终验收、缺陷修复或质保条件绑定。",
      "requiredEvidence": "里程碑/付款计划;尾款释放条件",
      "escalationRole": "财务经理/业务负责人"
    },
    {
      "policyId": "POL-TAX-001",
      "policyDomain": "税务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "含税价;不含税;发票类型;税率;征收率",
      "controlObjective": "进项抵扣与发票合规",
      "policyTextForEmbedding": "合同应明确不含税金额、适用税率或征收率、发票类型与开票信息。特殊税务情形应明确处理边界与责任分担。",
      "requiredEvidence": "税务条款截图;税率政策依据/口径说明;开票信息",
      "escalationRole": "税务岗/财务负责人"
    },
    {
      "policyId": "POL-TAX-002",
      "policyDomain": "税务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "专票;进项抵扣;不得抵扣;普票",
      "controlObjective": "抵扣资格与成本",
      "policyTextForEmbedding": "如业务需要进项抵扣，应约定对方开具增值税专用发票的义务与时点；若仅能开普票或免税票据，应评估税负影响并在价格中体现，避免事后争议。",
      "requiredEvidence": "开票资质说明;发票类型约定;税负测算",
      "escalationRole": "税务岗/财务经理"
    },
    {
      "policyId": "POL-TAX-003",
      "policyDomain": "税务合规",
      "appliesToContractType": "服务合同",
      "severity": "MEDIUM",
      "triggerKeywords": "跨境;境外;外汇;代扣代缴;预提税;非居民",
      "controlObjective": "跨境税务边界",
      "policyTextForEmbedding": "涉及境外主体或跨境服务时，应明确服务发生地、税务居民身份、是否需代扣代缴及税费承担方式，并配套所需材料，避免税务风险与外汇合规问题。",
      "requiredEvidence": "跨境条款截图;主体税务资料;代扣代缴口径;外汇材料清单",
      "escalationRole": "税务岗/财务负责人"
    },
    {
      "policyId": "POL-FIN-001",
      "policyDomain": "财务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "预付;全额预付;验收前支付100%;先款后货;先款后服务",
      "controlObjective": "付款风险",
      "policyTextForEmbedding": "原则上应避免无担保的高比例预付或验收前支付100%。如业务确需预付，应要求提供保函/履约保证金/等额担保，并设置里程碑验收与分期付款（或抵扣/退还机制），降低资金占用与履约风险。",
      "requiredEvidence": "付款计划表;担保文件;业务必要性说明;里程碑/验收约定",
      "escalationRole": "财务负责人"
    },
    {
      "policyId": "POL-FIN-003",
      "policyDomain": "财务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "个人收款;私人账户;收款账户变更;第三方代收;挂靠",
      "controlObjective": "收款合规与反舞弊",
      "policyTextForEmbedding": "付款应仅向合同相对方对公账户支付。涉及第三方代收、收款账户频繁变更或要求向个人账户付款的，需核验主体资质与受益所有人信息，并由财务/法务审批通过后方可执行。",
      "requiredEvidence": "收款账户证明;主体资质/营业执照;受益所有人说明;变更函",
      "escalationRole": "财务负责人/法务"
    },
    {
      "policyId": "POL-FIN-006",
      "policyDomain": "财务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "价格可调;调价机制;随行就市;单方调价;指数联动",
      "controlObjective": "价格控制与预算",
      "policyTextForEmbedding": "存在调价机制时需明确触发条件、周期、计算公式与上限，并与预算/采购审批一致。对乙方单方调价或缺少上限的条款应补充约束或改为双方确认。",
      "requiredEvidence": "调价条款截图;审批记录/预算依据;价格指数来源",
      "escalationRole": "财务负责人/采购负责人"
    },
    {
      "policyId": "POL-LEGAL-001",
      "policyDomain": "法务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "HIGH",
      "triggerKeywords": "无限责任;惩罚性赔偿;放弃抗辩",
      "controlObjective": "责任上限与可保性",
      "policyTextForEmbedding": "责任条款应设置合理上限并与可投保范围匹配；避免接受明显不对等的无限责任或惩罚性赔偿。对知识产权侵权等高风险点应约定补救流程与责任分担。",
      "requiredEvidence": "责任限制条款比对;可投保性评估（如适用）",
      "escalationRole": "法务"
    },
    {
      "policyId": "POL-LEGAL-002",
      "policyDomain": "法务合规",
      "appliesToContractType": "采购合同;服务合同",
      "severity": "MEDIUM",
      "triggerKeywords": "单方解除;随时解除;提前通知解除;自动续期",
      "controlObjective": "解除与续期风险",
      "policyTextForEmbedding": "解除条件应对等、明确且可执行。对单方随时解除、自动续期且缺少提前通知或退出机制的条款，应补充对等条件或退出窗口。",
      "requiredEvidence": "解除/续期条款截图;对等性改写建议",
      "escalationRole": "法务"
    },
    {
      "policyId": "POL-PROC-001",
      "policyDomain": "采购履约",
      "appliesToContractType": "采购合同",
      "severity": "MEDIUM",
      "triggerKeywords": "验收;异议期;质保;视为验收",
      "controlObjective": "资产与费用确认",
      "policyTextForEmbedding": "验收标准、异议期、质保起算点应清晰可执行，避免视为验收条款在质量问题未解决时损害甲方权益。对默认验收应补充例外与证据要求。",
      "requiredEvidence": "验收标准附件;异议流程;质保条款",
      "escalationRole": "业务负责人"
    },
    {
      "policyId": "POL-PROC-002",
      "policyDomain": "采购履约",
      "appliesToContractType": "采购合同",
      "severity": "MEDIUM",
      "triggerKeywords": "交付;迟延;物流;风险转移",
      "controlObjective": "交付与风险控制",
      "policyTextForEmbedding": "应明确交付地点、交付方式、风险转移时点与迟延责任（违约金/替代采购/解除条件），避免货损争议与交付不可控。",
      "requiredEvidence": "交付条款截图;物流/签收要求;迟延救济",
      "escalationRole": "采购负责人"
    },
    {
      "policyId": "POL-SVC-001",
      "policyDomain": "服务履约",
      "appliesToContractType": "服务合同",
      "severity": "HIGH",
      "triggerKeywords": "SLA;可用性;数据;外包;分包;驻场",
      "controlObjective": "连续性与数据安全",
      "policyTextForEmbedding": "服务合同应明确 SLA/可用性指标、违约救济、数据归属与删除、分包限制、驻场与远程访问安全要求。涉及数据处理时建议补充数据处理附录（DPA）与安全条款。",
      "requiredEvidence": "SOW;SLA附件;数据处理/安全附件;分包清单",
      "escalationRole": "信息安全/法务"
    },
    {
      "policyId": "POL-SVC-003",
      "policyDomain": "服务履约",
      "appliesToContractType": "服务合同",
      "severity": "MEDIUM",
      "triggerKeywords": "数据归属;数据导出;删除;备份",
      "controlObjective": "数据资产保护",
      "policyTextForEmbedding": "应明确数据归属、备份策略、合同终止后的数据导出与删除时限及验证方式，避免供应商锁定与数据遗失。",
      "requiredEvidence": "数据条款截图;导出/删除方案",
      "escalationRole": "信息安全/业务负责人"
    }
  ]
}
```
- 预期：`200`，返回 `importedCount = 15`，`policyIds` 长度为 `15`（按导入顺序）

### 用例 2：缺少 policyId
- Method：`POST`
- URL：`{{baseUrl}}/api/policies/import`
- Body：
```json
{
  "policies": [
    {
      "policyDomain": "资金支付",
      "appliesToContractType": "采购合同",
      "severity": "HIGH",
      "policyTextForEmbedding": "付款条件应包含验收、发票、对账等可验证要素。"
    }
  ]
}
```
- 预期：`400 Bad Request`

### 用例 3：重复导入覆盖验证
- 前置：先执行一次制度导入用例 1
- Method：`POST`
- URL：`{{baseUrl}}/api/policies/import`
- Body：将 `POL-PAY-001` 的 `requiredEvidence` 改为 `"付款条款截图;验收单;发票"`
- 预期：`200`，同一 `policyId` 被覆盖更新；业务表 `policy_knowledge` 与向量库 `vector_store` 同步覆盖（向量入库实现采用「先 delete 再 add」幂等策略，重复 `policyId` 不会触发主键冲突让整批回滚）

### 用例 4：向量库降级兜底（响应中带 vectorIngestionWarning）
- 触发条件：嵌入服务超时、`vector_store` 写入异常等运行时故障
- 预期响应（200）：

```json
{
  "importedCount": 15,
  "policyIds": ["POL-PAY-001", "POL-TAX-001", "..."],
  "vectorIngestionWarning": "Vector store sync failed: ... . Business table updated; retry import to re-sync."
}
```
- 客户端处理：业务表数据已成功，按相同请求体重试同一接口即可补偿向量同步；不需要清理 `policy_knowledge`

---

## 三、`POST /api/contracts/{id}/approval-records/import`（全量导入审批记录）

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

## 四、`POST /api/contracts/{id}/qa`（合同问答）

### 用例 1：正常问答
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/qa`
- Body：
```json
{
  "question": "这个合同付款条件是什么？"
}
```
- 预期：`200`，返回 `answer`、`retrievedChunkIds`；如命中制度依据，同时返回 `retrievedPolicyIds`

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

## 五、`POST /api/contracts/{id}/risk-check`（风险检查）

### 用例 1：正常风险检查
- Method：`POST`
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/risk-check`
- Body：空
- 预期：`200`，包含 `summary` 和 `riskItems`；风险项中的 `relatedClauseChunkIds` 来自当前合同，`relatedPolicyIds` 来自制度知识库

### 用例 2：合同不存在
- URL：`{{baseUrl}}/api/contracts/CTR-NOT-EXIST/risk-check`
- Body：空
- 预期：`404`

### 用例 3：审批记录已导入后再检查
- 前置：先执行审批记录导入用例
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/risk-check`
- 预期：`200`，风险结果应可结合审批历史（不报 500）

### 用例 4：制度知识库已导入后再检查
- 前置：先执行政策/制度知识库导入用例
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/risk-check`
- 预期：`200`，风险项应尽量返回 `relatedPolicyIds`，并可包含 `requiredEvidence`、`escalationRole`

---

## 六、`POST /api/contracts/{id}/approval-assist`（审批辅助）

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
- 预期：`200`，包含 `suggestion`、`checklist`；如命中制度依据，同时返回 `retrievedPolicyIds`

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

### 用例 5：基于制度依据生成财务核对清单
- 前置：先执行政策/制度知识库导入用例
- URL：`{{baseUrl}}/api/contracts/CTR-RAG-TEST-001/approval-assist`
- Body：
```json
{
  "approverRole": "财务负责人",
  "focus": "付款条件、验收和发票"
}
```
- 预期：`200`，`checklist` 优先包含制度条目中的 `requiredEvidence`，`retrievedPolicyIds` 包含资金支付或税务合规制度

---

## 附：ApiPost 环境变量建议

- `baseUrl = http://localhost:8088`
- 可新增 `contractId = CTR-RAG-TEST-001`，URL 中统一写 `{{contractId}}`
