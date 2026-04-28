# 数据模型与表结构说明

本文档定义项目当前数据模型、数据库表结构与约束，作为开发、联调与运维的统一参考。

## 1. 模型分层与存储边界

- 业务权威数据（可重启恢复）：
  - `contracts`：合同主数据
  - `clause_chunks`：合同条款分块
  - `approval_records`：合同审批记录
  - `policy_knowledge`：政策/制度知识库条目
- 检索索引数据（派生数据）：
  - `vector_store`：pgvector 文档与向量（由 Spring AI 管理）

## 2. 领域对象到表的映射总览

- `Contract` -> `contracts`
- `ClauseChunk` -> `clause_chunks`
- `ApprovalRecord` -> `approval_records`
- `PolicyKnowledgeItem` -> `policy_knowledge`
- 向量文档（由 `ClauseChunk` 派生）-> `vector_store`
- 向量文档（由 `PolicyKnowledgeItem` 派生）-> `vector_store`

说明：`vector_store` 不作为业务事实来源，合同是否存在、条款原文权威归属仍在业务表；制度依据是否有效、字段含义与审批引用关系以 `policy_knowledge` 为准。

## 3. 表结构（业务库）

以下结构与 `src/main/resources/schema.sql` 一致。

### 3.1 `contracts`

用途：保存合同主数据，主键为合同 ID。

| 列名 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---:|---|---|
| `id` | `varchar(64)` | Y | - | 合同主键 |
| `contract_type` | `varchar(32)` | Y | - | 合同类型，映射 `ContractType` |
| `party_a_name` | `varchar(255)` | Y | - | 甲方名称 |
| `party_b_name` | `varchar(255)` | Y | - | 乙方名称 |
| `currency` | `varchar(8)` | Y | `CNY` | 币种 |
| `amount_ex_tax` | `numeric(19,2)` | Y | - | 不含税金额 |
| `tax_rate_pct` | `numeric(10,4)` | Y | - | 税率（百分比数值） |
| `amount_inc_tax` | `numeric(19,2)` | Y | - | 含税金额 |
| `sign_date` | `date` | Y | - | 签订日 |
| `effective_date` | `date` | Y | - | 生效日 |
| `end_date` | `date` | Y | - | 结束日 |
| `performance_site` | `text` | Y | `''` | 履约地点摘要 |
| `payment_terms_summary` | `text` | Y | `''` | 付款摘要 |
| `business_owner_dept` | `varchar(255)` | Y | `''` | 主办部门 |
| `risk_tier` | `varchar(16)` | Y | - | 风险档位，映射 `RiskSeverity` |
| `vector_doc_id` | `varchar(128)` | N | - | 向量文档关联 ID |
| `notes` | `text` | Y | `''` | 备注 |

约束与索引：

- 主键：`pk_contracts(id)`
- 建议（可选）：根据查询场景补充 `contract_type`、`risk_tier` 辅助索引。

### 3.2 `clause_chunks`

用途：保存每份合同下的条款分块，作为 RAG 原始文本来源。

| 列名 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---:|---|---|
| `contract_id` | `varchar(64)` | Y | - | 所属合同 ID，外键到 `contracts.id` |
| `chunk_id` | `varchar(64)` | Y | - | 合同内条款块 ID |
| `clause_code` | `varchar(64)` | Y | `''` | 条款编码 |
| `clause_title` | `varchar(512)` | Y | `''` | 条款标题 |
| `clause_category` | `varchar(128)` | Y | `''` | 条款分类 |
| `party_focus` | `varchar(32)` | Y | `''` | 责任侧重点（甲/乙/双方） |
| `risk_flag` | `varchar(16)` | Y | `LOW` | 条款风险等级，映射 `RiskSeverity` |
| `source_section` | `varchar(128)` | Y | `''` | 来源章节（如“第4条”） |
| `text_for_embedding` | `text` | Y | - | 检索正文 |
| `related_amount_field` | `varchar(64)` | Y | `''` | 金额关联字段名 |
| `review_priority` | `varchar(32)` | Y | `''` | 审阅优先级 |

约束与索引：

- 主键：`pk_clause_chunks(contract_id, chunk_id)`
- 外键：`fk_clause_chunks_contract_id -> contracts(id) ON DELETE CASCADE`
- 索引：`idx_clause_chunks_contract_id(contract_id)`

### 3.3 `approval_records`

用途：保存某合同下的审批历史，支持全量导入、审批摘要生成与后续模型辅助。

| 列名 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---:|---|---|
| `contract_id` | `varchar(64)` | Y | - | 所属合同 ID，外键到 `contracts.id` |
| `approval_record_id` | `varchar(64)` | Y | - | 审批记录 ID |
| `step_no` | `integer` | Y | - | 流程步骤序号 |
| `approver_role` | `varchar(255)` | Y | - | 审批角色 |
| `decision` | `varchar(32)` | Y | - | 审批结论，映射 `ApprovalDecision` |
| `decision_time` | `timestamp with time zone` | N | - | 决策时间 |
| `comment_summary` | `text` | Y | `''` | 审批意见摘要 |
| `linked_policy_ids_json` | `jsonb` | Y | `'[]'::jsonb` | 关联政策 ID 列表 |
| `linked_clause_chunk_ids_json` | `jsonb` | Y | `'[]'::jsonb` | 关联条款块 ID 列表 |
| `risk_items_json` | `jsonb` | Y | `'[]'::jsonb` | 结构化风险项数组 |
| `vector_doc_id` | `varchar(128)` | N | - | 审批记录对应向量文档 ID |

约束与索引：

- 主键：`pk_approval_records(contract_id, approval_record_id)`
- 外键：`fk_approval_records_contract_id -> contracts(id) ON DELETE CASCADE`
- 索引：`idx_approval_records_contract_id(contract_id)`
- 索引：`idx_approval_records_contract_step(contract_id, step_no)`

### 3.4 `policy_knowledge`

用途：保存跨合同共享的政策/制度知识库条目，支撑制度 RAG、风险项依据引用和审批辅助核对清单。

| 列名 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---:|---|---|
| `policy_id` | `varchar(64)` | Y | - | 制度条目主键，应稳定不变 |
| `policy_domain` | `varchar(64)` | Y | - | 制度领域，如财务合规、税务合规、资金支付 |
| `applies_to_contract_type` | `varchar(255)` | Y | - | 适用合同类型，多个值用 `;` 分隔 |
| `severity` | `varchar(16)` | Y | - | 严重度，映射 `RiskSeverity` |
| `trigger_keywords` | `text` | Y | `''` | 触发关键词，多个值用 `;` 分隔 |
| `control_objective` | `varchar(255)` | Y | `''` | 控制目标 |
| `policy_text_for_embedding` | `text` | Y | - | 用于向量化的制度条文摘要 |
| `required_evidence` | `text` | Y | `''` | 要求提供的材料/证据，多个值用 `;` 分隔 |
| `escalation_role` | `varchar(255)` | Y | `''` | 需要升级或会签的角色 |
| `vector_doc_id` | `varchar(128)` | N | - | 制度条目对应向量文档 ID |
| `updated_at` | `timestamp with time zone` | N | - | 最近导入或更新日期 |

约束与索引：

- 主键：`pk_policy_knowledge(policy_id)`
- 建议索引：`idx_policy_knowledge_domain(policy_domain)`
- 建议索引：`idx_policy_knowledge_severity(severity)`
- 建议索引：`idx_policy_knowledge_contract_type(applies_to_contract_type)`，用于导入校验和非向量兜底查询。

字段维护约束：

- `policy_id` 是审批记录和风险项引用制度依据的稳定键，已被引用后不建议改名。
- `policy_text_for_embedding` 应包含“触发条件 + 制度要求 + 例外/补救 + 证据”，避免只写标题导致召回不准。
- 大制度应拆成可引用的小条目，例如 `POL-TAX-001#S1`，便于风险项关联到具体依据。

## 4. 向量表结构（检索索引）

### 4.1 `vector_store`

用途：存储向量文档（文本 + embedding + metadata），由 Spring AI pgvector 自动维护。

关键点：

- 表通常在应用启动时自动初始化（`initialize-schema=true`）。
- `embedding` 向量维度必须与 `spring.ai.vectorstore.pgvector.dimensions` 一致。
- 合同通道检索时必须基于 `metadata.docType=contract_clause` 与 `metadata.contractId` 做合同级过滤。
- 制度通道检索时必须基于 `metadata.docType=policy` 做知识源过滤，并按当前合同类型收敛。
- 同一张 `vector_store` 可同时保存合同条款和制度条目，但必须通过 `docType` 区分来源。

说明：该表的具体列名与索引随 Spring AI 版本可能有小差异，以实际 DDL 为准。

### 4.2 向量文档映射规范

请以 `PG_VECTOR_MAPPING_SPEC.md` 为准，核心规则如下：

- 合同条款文档：
  - `id = contract:{contractId}:{chunkId}`
  - `content = 【{clauseTitle}】\n{textForEmbedding}`
  - `metadata` 最小集合：
    - `docType = contract_clause`
    - `contractId`
    - `chunkId`
    - `clauseTitle`
    - `clauseCode`
    - `clauseCategory`
- 制度条目文档：
  - `id = policy:{policyId}`
  - `content = 【{policyDomain}/{controlObjective}】\n{policyTextForEmbedding}`
  - `metadata` 最小集合：
    - `docType = policy`
    - `policyId`
    - `policyDomain`
    - `appliesToContractType`
    - `severity`
    - `triggerKeywords`
    - `requiredEvidence`
    - `escalationRole`

兼容说明：如果历史合同向量文档仍使用 `{contractId}:{chunkId}` 作为 ID，检索逻辑仍必须依赖 metadata 过滤，新增文档建议统一使用带命名空间的 ID，避免与 `policyId` 冲突。

### 4.3 风险项与制度依据关联

结构化风险项应同时保留合同依据和制度依据：

- `relatedClauseChunkIds`：合同条款块 ID，必须来自当前 `contractId`。
- `relatedPolicyIds`：制度条目 ID，必须可在 `policy_knowledge.policy_id` 回查。
- `requiredEvidence`：可由命中的制度条目生成审批核对清单。
- `escalationRole`：可由命中的制度条目推荐加签或升级角色。

制度依据只能解释当前合同事实，不应单独生成与合同无关的风险项。

## 5. 枚举与取值约束

### 5.1 `ContractType`

- 枚举：`PROCUREMENT`、`SERVICE`
- 显示名：`采购合同`、`服务合同`
- 入参解析支持枚举名、英文别名、中文显示名（由代码 `fromFlexible` 处理）。

### 5.2 `RiskSeverity`

- 枚举：`LOW`、`MEDIUM`、`HIGH`
- 显示名：`低`、`中`、`高`
- 入参解析支持枚举名与中文显示名（由 `fromDisplayName` 处理）。

## 6. 当前仓储实现与事务边界

- 生产默认（非 test profile）：`MybatisContractRepository`（`@Primary @Profile("!test")`）
- 测试：由测试代码提供最小 fake 仓储，生产代码不保留内存仓储实现。
- 事务建议：
  - 业务表写入（合同 + 条款）应同事务提交。
  - 向量入库属于派生索引，可采用“业务成功后写索引 + 失败补偿”策略。

## 7. 演进建议（后续）

- 如审批检索、报表查询继续增多，可将 JSONB 字段进一步拆分为子表。
- 通过 Flyway/Liquibase 管控 `schema.sql` 版本，避免环境漂移。
- 为高频检索字段补充索引并定期评估查询计划。
