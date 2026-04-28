# contract-agent-mvp

基于 Spring Boot + Spring AI 的财务合同审批 Agent MVP。

## 1）当前最小合同问答范围

本阶段已实现：

- 导入合同与条款分块：`/api/contracts/import`
- 按合同 ID 提问：`/api/contracts/{id}/qa`
- PostgreSQL/MyBatis 持久化 + pgvector 检索 + 大模型回答

本阶段暂不包含：

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