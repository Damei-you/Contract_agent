# AI And RAG Rules

## Retrieval Modes

- 合同问答默认合同条款单通道：`RagRetriever#retrieve(contractId, question, topK)`。
- 合同问答只有在 `includePolicyEvidence=true` 时才额外调用制度通道。
- 政策制度问答只检索制度知识库，不读取合同条款。
- 风险检查和审批辅助固定合同 + 制度双通道，并保留 AgentTrace。

## Source Boundaries

- 合同条款是当前合同事实。
- 制度依据是公司内部规范，用于解释、校验、提示或生成核对项。
- Prompt 必须明确区分两类来源，禁止把制度要求写成合同已约定内容。
- 当制度上下文为空时，不做制度合规判断。

## AgentTrace

- `ContractFactAgent` 表示合同事实/条款检索，回填 `retrievedChunkIds`。
- `PolicyEvidenceAgent` 表示制度依据检索，回填 `retrievedPolicyIds`。
- `RiskReviewAgent` / `ApprovalAdviceAgent` 可描述生成动作，但前端证据面板通常只展示带证据 id 的 trace。
- Trace summary 要描述真实动作和命中数量，不夸大模型推理能力。

## Evidence IDs

- 合同条款证据 id 是 `clause_chunks.id` / `chunkId`。
- 制度依据证据 id 是 `policy_knowledge.policy_id` / `policyId`。
- `RiskItem.code` 是模型生成的风险编码，不可用于证据回查。
- 模型输出相关 id 时，后端应尽量从检索结果列表派生或校验，避免凭空 id。

## Prompt And Parsing

- 风险检查要求 JSON 输出，解析失败时允许把原文落入 summary 并返回空风险列表。
- 审批辅助要求 suggestion + checklist；模型解析失败时可以返回原文 suggestion 和空 checklist。
- 不要在 Prompt 中引入当前业务场景之外的制度要求。
- 需要新增模型输出字段时，同步 DTO、前端类型、API 文档和测试。

## Vector Retrieval

- 合同向量文档必须带 `docType=contract_clause`、`contractId`、`chunkId` 等 metadata。
- 制度向量文档必须带 `docType=policy`、`policyId`、`policyDomain`、`appliesToContractType`、`severity` 等 metadata。
- 合同通道严禁跨合同召回。
- 制度通道在合同相关场景按合同类型收敛；全局政策制度问答可以不限定合同类型。
