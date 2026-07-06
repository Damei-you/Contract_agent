# Contract Agent 架构总览

本文档用于统一说明本项目的系统边界、核心流程和关键模块职责，作为后续维护与扩展的基线。

## 1. 系统边界

### 1.1 对外能力（In Scope）

- 合同导入：接收合同主数据与条款分块，生成合同记录与可检索数据。
- 政策/制度知识库导入：接收制度条目数据，生成可检索的制度依据知识库。
- 合同问答：针对指定合同执行 RAG 检索并生成回答。
- 风险检查：基于合同摘要、合同检索上下文、制度知识库、审批历史生成结构化风险结果。
- 审批辅助：基于审批角色、关注点、合同范围上下文与制度依据生成建议与清单。

### 1.2 系统外部依赖（Out of Process）

- LLM / Embedding 服务：通过 OpenAI 兼容接口调用聊天模型与嵌入模型。
- PostgreSQL + pgvector：用于向量数据存储与相似度检索（`vector_store`）。
- 业务数据库持久化层：用于保存合同主数据/条款/审批记录/制度知识库（重启后可恢复）。

### 1.3 当前边界约束

- 合同通道的向量检索范围必须按 `contractId` 限定，禁止跨合同召回。
- 政策/制度知识库不按 `contractId` 限定，但必须按合同类型、制度领域、触发关键词等 metadata 做候选收敛。
- 合同问答默认只召回指定合同条款，用户显式开启 `includePolicyEvidence` 时才额外召回适用制度依据；风险检查、审批辅助固定采用合同 + 制度双通道 RAG。
- RAG 只负责“相关片段召回”，最终答案生成由聊天模型完成。
- 合同主数据与向量数据是两套职责：前者负责业务事实，后者负责语义检索。
- 制度知识库是跨合同共享的业务规则来源，风险项中的 `relatedPolicyIds` 应尽量回填到具体制度条目 ID。

## 2. 核心流程（导入 -> 入向量 -> 检索 -> 问答）

### 2.1 导入与入向量流程

1. 客户端调用 `POST /api/contracts/import` 提交合同主数据与 `chunks`。
2. `ContractApplicationService#importContract` 做参数规范化、ID 生成与冲突校验。
3. `ContractRepository` 写入合同主数据并保存条款分块（以及初始化审批记录）。
4. `ContractVectorIngestionService` 按映射规范将每个 chunk 写入向量库：
   - `id = contract:{contractId}:{chunkId}`
   - `content = 【clauseTitle】 + textForEmbedding`
   - `metadata` 至少包含 `docType=contract_clause/contractId/chunkId/clauseTitle/clauseCode/clauseCategory`
5. 导入完成后，业务库与向量库都应可查询到该合同对应数据。

### 2.2 政策/制度知识库导入流程

1. 客户端调用 `POST /api/policies/import` 提交制度条目列表，或由离线任务读取 `data/vectorization/03_approval_policy_extended.csv`。
2. `PolicyKnowledgeApplicationService#importPolicies` 做字段规范化、`policyId` 去重与覆盖策略校验。
3. `PolicyKnowledgeRepository` 保存制度条目权威数据，字段包括制度领域、适用合同类型、严重度、触发关键词、控制目标、证据要求和升级角色。
4. `PolicyVectorIngestionService` 将每条制度条目写入向量库：
   - `id = policy:{policyId}`
   - `content = 【{policyDomain}/{controlObjective}】 + policyTextForEmbedding`
   - `metadata` 至少包含 `docType=policy/policyId/policyDomain/appliesToContractType/severity/triggerKeywords`
5. 导入完成后，制度表与向量库都应可按 `policyId` 回查，便于模型输出可引用依据。

### 2.3 合同问答检索流程

1. 客户端调用 `POST /api/contracts/{id}/qa` 提交问题；`includePolicyEvidence` 缺省为 `false`。
2. `ContractApplicationService#qa` 先校验合同存在（防止无效合同检索）。
3. `AiContractAssistant#answerQuestion` 总是构建合同通道检索请求：
   - 合同通道：`RagRetriever#retrieve(contractId, question, topK)`。
   - 制度通道：仅当 `includePolicyEvidence=true` 时调用 `PolicyRagRetriever#retrieve(contractType, question, topK)`。
4. 合同通道在 `vector_store` 中执行：
   - 按 `contractId` 过滤候选
   - 以 query embedding 做相似度排序
   - 返回 topK 命中并映射为 `RagDocument`
5. 制度通道开启时，在 `vector_store` 中执行：
   - 按 `docType=policy` 过滤候选
   - 按 `appliesToContractType` 与当前合同类型收敛
   - 结合 query embedding、触发关键词、制度领域做相似度排序
   - 返回 topK 命中并映射为制度依据文档
6. `AiContractAssistant` 将“合同摘要 + 合同条款上下文 + 可选制度依据上下文”拼装到 Prompt，请求聊天模型生成答案。
7. 返回回答文本、命中条款 ID 列表与命中制度 ID 列表给客户端；默认情况下 `retrievedPolicyIds` 为空。

### 2.4 风险检查与审批辅助的双通道约束

- 风险检查必须输出结构化风险项，并将每个风险项同时关联：
  - `relatedClauseChunkIds`：触发风险的合同条款。
  - `relatedPolicyIds`：支撑判断的制度依据。
- 审批辅助必须基于审批角色过滤或重排制度依据，例如财务角色优先召回财务合规、税务合规、资金支付类制度。
- 模型不得仅凭制度知识库生成与当前合同无关的风险；制度通道只能解释、校验或补充合同通道中已出现的条款与事实。
- 当合同通道无相关条款命中时，风险项应标记为“需人工复核”，避免把通用制度要求误判为合同实际风险。

## 3. 关键模块职责

### 3.1 `ContractApplicationService`

- 用例编排入口，负责把“导入、问答、风险、审批辅助”流程串起来。
- 负责业务级校验（如合同是否存在、ID 冲突）和请求 DTO 到领域对象的映射。
- 在导入链路中负责触发向量入库服务，保证业务数据与检索数据一致演进。
- 不负责底层存储实现与模型协议细节。

### 3.2 `RagRetriever`

- RAG 检索抽象接口，只定义“输入合同 ID + 查询 + topK，输出命中片段列表”。
- 解耦上层 AI 编排与检索实现，当前由 pgvector 检索实现承载。
- 保证返回统一结构 `RagDocument`，供 `AiContractAssistant` 构建上下文。
- 约束检索语义：在指定合同范围内召回最相关条款片段。

### 3.3 `PolicyRagRetriever`

- 政策/制度检索抽象接口，定义“输入合同类型 + 查询 + topK，输出制度依据片段列表”。
- 负责制度通道召回、过滤与重排，不读取合同业务表。
- 保证返回结构中包含 `policyId`、`policyDomain`、`severity`、`requiredEvidence`、`escalationRole`，供风险检查和审批辅助生成依据。
- 约束检索语义：只召回当前合同类型可适用的制度条目。

### 3.4 `PolicyKnowledgeRepository`

- 政策/制度知识库的数据访问抽象，负责制度条目的导入、覆盖、查询与回查。
- 保存跨合同共享的业务规则权威数据，向量库仅作为派生索引。
- 支撑 `relatedPolicyIds` 的依据回显、核对清单生成和审批升级路径推导。

### 3.5 `ContractRepository`

- 业务数据访问抽象，负责合同主数据、条款块、审批记录的读写。
- 支撑问答前合同存在性校验、合同摘要生成、条款回查等业务查询。
- 屏蔽存储实现差异（内存 Mock / JDBC / MyBatis / JPA），保持上层签名稳定。
- 不承担向量检索职责；向量检索由 `RagRetriever` 体系负责。

## 4. 模块交互关系（简化）

- Controller -> `ContractApplicationService`
- Policy Controller -> `PolicyKnowledgeApplicationService`
- `ContractApplicationService` -> `ContractRepository`
- `ContractApplicationService` -> `ContractVectorIngestionService`（仅导入）
- `ContractApplicationService` -> `AiContractAssistant`
- `AiContractAssistant` -> `RagRetriever`
- `AiContractAssistant` -> `PolicyRagRetriever`
- `AiContractAssistant` -> `ContractToolExecutor` -> `ContractRepository`
- `PolicyKnowledgeApplicationService` -> `PolicyKnowledgeRepository`
- `PolicyKnowledgeApplicationService` -> `PolicyVectorIngestionService`

## 5. 维护与扩展建议

- 先守住接口边界：新增功能优先扩展实现类，不改抽象签名。
- 业务数据与向量数据双写流程要有失败策略（回滚、补偿或重试）并记录日志。
- 新增检索策略（混合检索、重排）时，尽量在 `RagRetriever` 内部完成，避免污染应用服务层。
- RAG 检索优化细节集中维护在 `docs/RAG_RETRIEVAL_OPTIMIZATION.md`，包括候选召回、qwen3-rerank 融合、MMR 多样性截断和离线评测口径。
- 重启不丢数据依赖业务库持久化实现，不应由向量库反推恢复合同主数据。
- 制度知识库导入应支持稳定 `policyId`，否则历史审批记录中的 `linkedPolicyIds` 与风险项依据会失效。
- 双通道 RAG 的 Prompt 应明确区分“合同事实”和“制度依据”，避免模型把制度要求误写成合同已约定内容。
