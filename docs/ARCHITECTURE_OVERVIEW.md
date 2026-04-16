# Contract Agent MVP 架构总览

本文档用于统一说明本项目的系统边界、核心流程和关键模块职责，作为后续维护与扩展的基线。

## 1. 系统边界

### 1.1 对外能力（In Scope）

- 合同导入：接收合同主数据与条款分块，生成合同记录与可检索数据。
- 合同问答：针对指定合同执行 RAG 检索并生成回答。
- 风险检查：基于合同摘要、检索上下文、审批历史生成结构化风险结果。
- 审批辅助：基于审批角色与关注点生成建议与清单。

### 1.2 系统外部依赖（Out of Process）

- LLM / Embedding 服务：通过 OpenAI 兼容接口调用聊天模型与嵌入模型。
- PostgreSQL + pgvector：用于向量数据存储与相似度检索（`vector_store`）。
- 业务数据库持久化层：用于保存合同主数据/条款/审批记录（重启后可恢复）。

### 1.3 当前边界约束

- 向量检索范围必须按 `contractId` 限定，禁止跨合同召回。
- RAG 只负责“相关片段召回”，最终答案生成由聊天模型完成。
- 合同主数据与向量数据是两套职责：前者负责业务事实，后者负责语义检索。

## 2. 核心流程（导入 -> 入向量 -> 检索 -> 问答）

### 2.1 导入与入向量流程

1. 客户端调用 `POST /api/contracts/import` 提交合同主数据与 `chunks`。
2. `ContractApplicationService#importContract` 做参数规范化、ID 生成与冲突校验。
3. `ContractRepository` 写入合同主数据并保存条款分块（以及初始化审批记录）。
4. `ContractVectorIngestionService` 按映射规范将每个 chunk 写入向量库：
   - `id = {contractId}:{chunkId}`
   - `content = 【clauseTitle】 + textForEmbedding`
   - `metadata` 至少包含 `contractId/chunkId/clauseTitle/clauseCode/clauseCategory`
5. 导入完成后，业务库与向量库都应可查询到该合同对应数据。

### 2.2 检索与问答流程

1. 客户端调用 `POST /api/contracts/{id}/qa` 提交问题。
2. `ContractApplicationService#qa` 先校验合同存在（防止无效合同检索）。
3. `AiContractAssistant#answerQuestion` 调用 `RagRetriever#retrieve(contractId, question, topK)`。
4. `PgVectorRagRetriever` 在 `vector_store` 中执行：
   - 按 `contractId` 过滤候选
   - 以 query embedding 做相似度排序
   - 返回 topK 命中并映射为 `RagDocument`
5. `AiContractAssistant` 将 RAG 上下文 + 合同摘要拼装到 Prompt，请求聊天模型生成答案。
6. 返回回答文本与命中条款 ID 列表给客户端。

## 3. 关键模块职责

### 3.1 `ContractApplicationService`

- 用例编排入口，负责把“导入、问答、风险、审批辅助”流程串起来。
- 负责业务级校验（如合同是否存在、ID 冲突）和请求 DTO 到领域对象的映射。
- 在导入链路中负责触发向量入库服务，保证业务数据与检索数据一致演进。
- 不负责底层存储实现与模型协议细节。

### 3.2 `RagRetriever`

- RAG 检索抽象接口，只定义“输入合同 ID + 查询 + topK，输出命中片段列表”。
- 解耦上层 AI 编排与检索实现，支持关键词检索、pgvector 检索等实现替换。
- 保证返回统一结构 `RagDocument`，供 `AiContractAssistant` 构建上下文。
- 约束检索语义：在指定合同范围内召回最相关条款片段。

### 3.3 `ContractRepository`

- 业务数据访问抽象，负责合同主数据、条款块、审批记录的读写。
- 支撑问答前合同存在性校验、合同摘要生成、条款回查等业务查询。
- 屏蔽存储实现差异（内存 Mock / JDBC / MyBatis / JPA），保持上层签名稳定。
- 不承担向量检索职责；向量检索由 `RagRetriever` 体系负责。

## 4. 模块交互关系（简化）

- Controller -> `ContractApplicationService`
- `ContractApplicationService` -> `ContractRepository`
- `ContractApplicationService` -> `ContractVectorIngestionService`（仅导入）
- `ContractApplicationService` -> `AiContractAssistant`
- `AiContractAssistant` -> `RagRetriever`
- `AiContractAssistant` -> `ContractToolExecutor` -> `ContractRepository`

## 5. 维护与扩展建议

- 先守住接口边界：新增功能优先扩展实现类，不改抽象签名。
- 业务数据与向量数据双写流程要有失败策略（回滚、补偿或重试）并记录日志。
- 新增检索策略（混合检索、重排）时，尽量在 `RagRetriever` 内部完成，避免污染应用服务层。
- 重启不丢数据依赖业务库持久化实现，不应由向量库反推恢复合同主数据。
