# contract-agent-mvp

基于 Spring Boot + Spring AI 的财务合同审批 Agent MVP。

## 1）当前最小合同问答范围

本阶段已实现：

- 导入合同与条款分块：`/api/contracts/import`
- 按合同 ID 提问：`/api/contracts/{id}/qa`
- 内存仓储 + 关键词检索 + 大模型回答

本阶段暂不包含：

- pgvector / 向量索引
- 检索重排与效果优化
- 性能与稳定性增强

## 2）模型配置

启动前请配置环境变量：

- `OPENAI_BASE_URL`（OpenAI 兼容端点，例如 `https://api.openai.com` 或其他厂商兼容地址）
- `OPENAI_API_KEY`（或 `API_KEY`）
- `OPENAI_CHAT_MODEL`（例如：`gpt-4o-mini`）

## 3）启动项目

```bash
mvn spring-boot:run
```

默认端口是 `8088`。

## 4）接口测试

- ApiPost 环境变量（建议）：
  - `{{baseUrl}} = http://localhost:8088`

- 健康检查
  - Method: `GET`
  - URL: `{{baseUrl}}/api/ai/ping`
  - Headers: 无
  - Body: 无

- 模型聊天测试
  - Method: `POST`
  - URL: `{{baseUrl}}/api/ai/chat`
  - Headers:
    - `Content-Type: application/json`
  - Body（raw / JSON）：

```json
{
  "message": "请总结一个财务合同审批的关注点"
}
```

- 导入合同与条款分块
  - Method: `POST`
  - URL: `{{baseUrl}}/api/contracts/import`
  - Headers:
    - `Content-Type: application/json`
  - Body（raw / JSON）：

```json
{
  "id": "CTR-MIN-001",
  "type": "procurement",
  "partyAName": "甲方公司",
  "partyBName": "乙方公司",
  "currency": "CNY",
  "amountExTax": 100000,
  "taxRatePct": 13,
  "amountIncTax": 113000,
  "signDate": "2026-01-10",
  "effectiveDate": "2026-01-10",
  "endDate": "2026-12-31",
  "riskTier": "MEDIUM",
  "chunks": [
    {
      "id": "ch-1",
      "clauseCode": "PAY",
      "clauseTitle": "付款条件",
      "clauseCategory": "财务",
      "textForEmbedding": "验收合格后30个自然日内，甲方支付当期应付合同款。"
    },
    {
      "id": "ch-2",
      "clauseCode": "LIA",
      "clauseTitle": "违约责任",
      "clauseCategory": "法务",
      "textForEmbedding": "任一方逾期履约的，应按逾期金额日万分之五向守约方支付违约金。"
    },
    {
      "id": "ch-3",
      "clauseCode": "INV",
      "clauseTitle": "发票条款",
      "clauseCategory": "税务",
      "textForEmbedding": "乙方应在每次付款前向甲方开具合法有效的增值税专用发票。"
    },
    {
      "id": "ch-4",
      "clauseCode": "ACC",
      "clauseTitle": "验收标准",
      "clauseCategory": "履约",
      "textForEmbedding": "交付物须满足附件技术规范，并经甲方书面验收确认后视为验收通过。"
    },
    {
      "id": "ch-5",
      "clauseCode": "WAR",
      "clauseTitle": "质保条款",
      "clauseCategory": "售后",
      "textForEmbedding": "乙方提供12个月质量保证期，质保期内免费修复非人为故障。"
    },
    {
      "id": "ch-6",
      "clauseCode": "TERM",
      "clauseTitle": "合同终止",
      "clauseCategory": "法务",
      "textForEmbedding": "任一方重大违约且在收到书面通知后15日内未整改的，守约方有权解除合同。"
    },
    {
      "id": "ch-7",
      "clauseCode": "CONF",
      "clauseTitle": "保密义务",
      "clauseCategory": "合规",
      "textForEmbedding": "双方对在履约过程中获知的商业秘密承担保密义务，未经许可不得披露。"
    },
    {
      "id": "ch-8",
      "clauseCode": "DISP",
      "clauseTitle": "争议解决",
      "clauseCategory": "法务",
      "textForEmbedding": "因本合同产生争议，双方应先协商；协商不成提交合同签署地人民法院诉讼。"
    }
  ]
}
```

- 合同问答
  - Method: `POST`
  - URL: `{{baseUrl}}/api/contracts/CTR-MIN-001/qa`
  - Headers:
    - `Content-Type: application/json`
  - Body（raw / JSON）：

```json
{
  "question": "这个合同付款条件是什么？"
}
```

- 导入合同与条款分块（向量检索测试新示例）
  - Method: `POST`
  - URL: `{{baseUrl}}/api/contracts/import`
  - Headers:
    - `Content-Type: application/json`
  - Body（raw / JSON）：

```json
{
  "id": "CTR-RAG-TEST-002",
  "type": "service",
  "partyAName": "甲方测试集团",
  "partyBName": "乙方测试服务商",
  "currency": "CNY",
  "amountExTax": 880000,
  "taxRatePct": 6,
  "amountIncTax": 932800,
  "signDate": "2026-04-15",
  "effectiveDate": "2026-04-16",
  "endDate": "2027-04-15",
  "riskTier": "HIGH",
  "chunks": [
    {
      "id": "pay-01",
      "clauseCode": "PAY",
      "clauseTitle": "付款计划",
      "clauseCategory": "财务",
      "textForEmbedding": "本合同按里程碑付款：首付款30%，中期验收后40%，终验后30%。"
    },
    {
      "id": "inv-01",
      "clauseCode": "INV",
      "clauseTitle": "开票规则",
      "clauseCategory": "税务",
      "textForEmbedding": "乙方应在每个付款节点前5个工作日开具合法有效增值税专用发票。"
    },
    {
      "id": "acc-01",
      "clauseCode": "ACC",
      "clauseTitle": "验收与整改",
      "clauseCategory": "履约",
      "textForEmbedding": "甲方在收到交付物后10个工作日内组织验收，不通过项由乙方在7日内整改。"
    },
    {
      "id": "sla-01",
      "clauseCode": "SLA",
      "clauseTitle": "服务等级",
      "clauseCategory": "运维",
      "textForEmbedding": "系统可用性不低于99.9%，重大故障4小时内恢复，普通故障24小时内恢复。"
    },
    {
      "id": "sec-01",
      "clauseCode": "SEC",
      "clauseTitle": "数据安全",
      "clauseCategory": "合规",
      "textForEmbedding": "乙方应落实访问控制和日志留存，未经授权不得复制、导出或泄露甲方数据。"
    },
    {
      "id": "lia-01",
      "clauseCode": "LIA",
      "clauseTitle": "违约责任",
      "clauseCategory": "法务",
      "textForEmbedding": "乙方逾期交付的，每逾期一日按当期服务费万分之五支付违约金。"
    },
    {
      "id": "term-01",
      "clauseCode": "TERM",
      "clauseTitle": "提前终止",
      "clauseCategory": "法务",
      "textForEmbedding": "任一方重大违约且在收到通知后15日内未纠正的，守约方有权提前终止合同。"
    },
    {
      "id": "ip-01",
      "clauseCode": "IPR",
      "clauseTitle": "知识产权归属",
      "clauseCategory": "法务",
      "textForEmbedding": "项目成果中为甲方定制开发部分的知识产权归甲方所有，通用能力归乙方所有。"
    }
  ]
}
```
