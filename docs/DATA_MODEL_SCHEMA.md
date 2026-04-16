# 数据模型与表结构说明

本文档定义项目当前数据模型、数据库表结构与约束，作为开发、联调与运维的统一参考。

## 1. 模型分层与存储边界

- 业务权威数据（可重启恢复）：
  - `contracts`：合同主数据
  - `clause_chunks`：合同条款分块
- 检索索引数据（派生数据）：
  - `vector_store`：pgvector 文档与向量（由 Spring AI 管理）
- 当前状态：
  - `ApprovalRecord` 领域对象已存在，但审批表尚未落库（仓储暂返回空列表）。

## 2. 领域对象到表的映射总览

- `Contract` -> `contracts`
- `ClauseChunk` -> `clause_chunks`
- 向量文档（由 `ClauseChunk` 派生）-> `vector_store`

说明：`vector_store` 不作为业务事实来源，合同是否存在、条款原文权威归属仍在业务表。

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

## 4. 向量表结构（检索索引）

### 4.1 `vector_store`

用途：存储向量文档（文本 + embedding + metadata），由 Spring AI pgvector 自动维护。

关键点：

- 表通常在应用启动时自动初始化（`initialize-schema=true`）。
- `embedding` 向量维度必须与 `spring.ai.vectorstore.pgvector.dimensions` 一致。
- 本项目检索时必须基于 `metadata.contractId` 做合同级过滤。

说明：该表的具体列名与索引随 Spring AI 版本可能有小差异，以实际 DDL 为准。

### 4.2 向量文档映射规范

请以 `PG_VECTOR_MAPPING_SPEC.md` 为准，核心规则如下：

- `id = {contractId}:{chunkId}`
- `content = 【{clauseTitle}】\n{textForEmbedding}`
- `metadata` 最小集合：
  - `contractId`
  - `chunkId`
  - `clauseTitle`
  - `clauseCode`
  - `clauseCategory`

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
- 测试/演示：`MockContractRepository`（内存）
- 事务建议：
  - 业务表写入（合同 + 条款）应同事务提交。
  - 向量入库属于派生索引，可采用“业务成功后写索引 + 失败补偿”策略。

## 7. 演进建议（后续）

- 增加审批持久化表（如 `approval_records` 及关联子表）以消除当前“审批记录不落库”缺口。
- 通过 Flyway/Liquibase 管控 `schema.sql` 版本，避免环境漂移。
- 为高频检索字段补充索引并定期评估查询计划。
