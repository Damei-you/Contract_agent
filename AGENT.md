# Agent Rules

本文件记录长期有效、代码里不容易直接推断、猜错会影响结果的项目规则。进入本仓库后先读本文件；涉及具体领域时，再读 `.codex/` 下对应细则。

## Scope

- `AGENT.md` 是全局总纲，适用于后端、前端、文档、测试和 AI/RAG 改动。
- `.codex/backend.md` 适用于 Spring Boot、DTO、Service、Repository、MyBatis、测试。
- `.codex/frontend.md` 适用于 Vue、TypeScript、API 封装、页面交互。
- `.codex/ai-rag.md` 适用于 Prompt、RAG、AgentTrace、向量检索、证据溯源。

## Product Truths

- 合同问答默认只检索当前合同条款。只有请求体 `includePolicyEvidence=true` 或前端勾选“同时引用制度依据”时，才额外检索制度依据。
- 政策制度问答是独立模块，只检索制度知识库，不读取具体合同条款。
- 风险检查和审批辅助固定使用合同条款 + 制度依据双通道，因为它们的目标是合规对照和审批建议。
- `RiskItem.code` 是模型生成的风险编码，不是数据库主键。可点击、可回查的证据主键是合同条款 `chunkId` 和制度 `policyId`。
- `agentTrace` 是给人工审查和前端溯源用的执行轨迹。凡是模型结论引用了合同条款或制度依据，都应尽量回填 `retrievedChunkIds` / `retrievedPolicyIds`。
- 合同导入是“覆盖前确认”语义：同一合同 `id` 首次重复导入只返回确认提示；用户确认后才覆盖合同主数据和条款，并清空该合同审批记录。不要改回简单 409。
- 制度知识库按 `policyId` 幂等覆盖。业务表是权威数据，`vector_store` 是派生检索索引。

## Engineering Rules

- 保持清晰分层：Controller 只处理 HTTP 入口，Application Service 编排业务流程，Repository/Mapper 负责持久化。
- 行为变更必须同步考虑测试和接口文档。API 请求/响应字段、枚举、错误语义发生变化时，更新 `docs/API_REFERENCE.md`。
- 不要把制度要求写成合同事实。合同条款描述“当前合同约定”，制度依据描述“公司规则/校验要求”。
- 不要吞异常。要么带上下文记录并继续软降级，要么映射为明确 HTTP 错误。
- 避免硬编码密钥、服务地址和魔法值；使用配置项、常量或领域枚举。
- 优先做幂等、可重试、确定性的实现，尤其是导入、向量写入和 ID 生成。

## Code Comment Rules

- 注释用于解释“不直观的意图、业务约束、边界语义、降级原因”，不要复述代码表面动作。
- Public DTO、Controller API、复杂 Service 方法保留简洁 Javadoc，说明业务语义、关键参数和返回契约。
- 普通函数内部只在复杂逻辑前加短注释；能靠命名读懂的代码不加注释。
- 注释必须随行为变化同步更新。发现误导性注释时，应和代码一起修正。
- 新增业务规则优先中文。
- 禁止留下临时注释、调试注释、TODO 占位，除非明确包含负责人/后续条件/触发场景。

## Local Hygiene

- 不要提交或依赖 `.cursor/`；本仓库的 agent 规则统一放在 `AGENT.md` 和 `.codex/`。
- 不要随意重排、格式化无关文件。工作区可能有用户或之前任务留下的改动，先确认再动。
- 删除文件前确认目标路径在工作区内；Windows 下优先使用 PowerShell 原生命令和 `-LiteralPath`。
