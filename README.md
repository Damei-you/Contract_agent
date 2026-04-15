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

- 健康检查：

```bash
curl http://localhost:8088/api/ai/ping
```

- 模型聊天测试：

```bash
curl -X POST http://localhost:8088/api/ai/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"请总结一个财务合同审批的关注点\"}"
```

- 导入合同与条款分块：

```bash
curl -X POST http://localhost:8088/api/contracts/import \
  -H "Content-Type: application/json" \
  -d "{
    \"id\":\"CTR-MIN-001\",
    \"type\":\"procurement\",
    \"partyAName\":\"甲方公司\",
    \"partyBName\":\"乙方公司\",
    \"currency\":\"CNY\",
    \"amountExTax\":100000,
    \"taxRatePct\":13,
    \"amountIncTax\":113000,
    \"signDate\":\"2026-01-10\",
    \"effectiveDate\":\"2026-01-10\",
    \"endDate\":\"2026-12-31\",
    \"riskTier\":\"MEDIUM\",
    \"chunks\":[
      {\"id\":\"ch-1\",\"clauseCode\":\"PAY\",\"clauseTitle\":\"付款条件\",\"clauseCategory\":\"财务\",\"textForEmbedding\":\"验收后30天付款\"},
      {\"id\":\"ch-2\",\"clauseCode\":\"LIA\",\"clauseTitle\":\"违约责任\",\"clauseCategory\":\"法务\",\"textForEmbedding\":\"逾期付款按日万分之五支付违约金\"},
      {\"id\":\"ch-3\",\"clauseCode\":\"INV\",\"clauseTitle\":\"发票条款\",\"clauseCategory\":\"税务\",\"textForEmbedding\":\"付款前乙方需开具合法增值税专用发票\"}
    ]
  }"
```

- 合同问答：

```bash
curl -X POST http://localhost:8088/api/contracts/CTR-MIN-001/qa \
  -H "Content-Type: application/json" \
  -d "{\"question\":\"这个合同付款条件是什么？\"}"
```
