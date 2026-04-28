# PG 向量检索映射规范（双通道 RAG）

面向 pgvector 改造，定义合同条款通道与政策/制度通道的固定映射规则。合同通道负责回答“这份合同写了什么”，制度通道负责回答“相关制度要求是什么、风险依据是什么”。

## 1. 映射粒度

- 合同条款：1 个 `chunk` -> 1 条向量文档（禁止一个 chunk 拆多条，或多 chunk 合一条）。
- 政策/制度：1 个 `policy_id` -> 1 条向量文档；如一份制度包含多个可独立引用条款，应先拆成多个稳定 `policy_id`。
- 同一 `vector_store` 中通过 `metadata.docType` 区分合同条款与制度条目。

## 2. 合同条款向量文档结构

- `id`：`contract:{contractId}:{chunkId}`
- `content`：`【{clauseTitle}】\n{textForEmbedding}`
- `metadata`（最小固定集合）：
  - `docType = contract_clause`
  - `contractId`
  - `chunkId`
  - `clauseTitle`
  - `clauseCode`
  - `clauseCategory`

### 2.1 字段映射来源

- `contractId` <- `ClauseChunk.contractId`
- `chunkId` <- `ClauseChunk.id`
- `clauseTitle` <- `ClauseChunk.clauseTitle`
- `clauseCode` <- `ClauseChunk.clauseCode`
- `clauseCategory` <- `ClauseChunk.clauseCategory`
- `textForEmbedding` <- `ClauseChunk.textForEmbedding`

## 3. 政策/制度向量文档结构

- `id`：`policy:{policyId}`
- `content`：`【{policyDomain}/{controlObjective}】\n{policyTextForEmbedding}`
- `metadata`（最小固定集合）：
  - `docType = policy`
  - `policyId`
  - `policyDomain`
  - `appliesToContractType`
  - `severity`
  - `triggerKeywords`
  - `requiredEvidence`
  - `escalationRole`

### 3.1 字段映射来源

- `policyId` <- `PolicyKnowledgeItem.policyId`
- `policyDomain` <- `PolicyKnowledgeItem.policyDomain`
- `appliesToContractType` <- `PolicyKnowledgeItem.appliesToContractType`
- `severity` <- `PolicyKnowledgeItem.severity`
- `triggerKeywords` <- `PolicyKnowledgeItem.triggerKeywords`
- `controlObjective` <- `PolicyKnowledgeItem.controlObjective`
- `policyTextForEmbedding` <- `PolicyKnowledgeItem.policyTextForEmbedding`
- `requiredEvidence` <- `PolicyKnowledgeItem.requiredEvidence`
- `escalationRole` <- `PolicyKnowledgeItem.escalationRole`

## 4. 检索约束（强制）

- 合同通道必须先按 `docType=contract_clause` 和 `contractId` 过滤候选集合，再在该合同范围内执行向量相似度排序并取 `topK`。
- 制度通道必须先按 `docType=policy` 过滤候选集合，再按当前合同类型匹配 `appliesToContractType`，最后执行向量相似度排序并取 `topK`。
- 禁止跨合同全库直接做 `topK` 后再按 `contractId` 二次过滤。
- 禁止制度通道直接替代合同通道生成合同事实；制度命中只能作为解释、校验、风险依据和审批清单来源。
- 风险检查和审批辅助必须保留通道来源，分别输出 `retrievedChunkIds` / `relatedClauseChunkIds` 与 `retrievedPolicyIds` / `relatedPolicyIds`。

## 5. 推荐 SQL 形态（pgvector）

### 5.1 合同条款通道

```sql
SELECT id, content, metadata
FROM vector_store
WHERE metadata->>'docType' = 'contract_clause'
  AND metadata->>'contractId' = :contractId
ORDER BY embedding <=> :queryEmbedding
LIMIT :topK;
```

### 5.2 政策/制度通道

```sql
SELECT id, content, metadata
FROM vector_store
WHERE metadata->>'docType' = 'policy'
  AND metadata->>'appliesToContractType' LIKE '%' || :contractTypeDisplayName || '%'
ORDER BY embedding <=> :queryEmbedding
LIMIT :topK;
```

说明：`appliesToContractType` 当前按 `;` 分隔字符串维护，后续如改为 JSONB 数组，应将 `LIKE` 替换为 JSONB 包含查询。

## 6. 写入幂等与分批策略（强制）

### 6.1 幂等：先 delete 再 add

`vector_store` 主键为业务派生 id（合同条款经 `contract:{contractId}:{chunkId}`、制度条目经 `policy:{policyId}` 转 name-based UUID）。Spring AI `PgVectorStore.add` 默认走 `INSERT`，重复 id 会触发主键冲突让整批回滚，造成「业务表 upsert 成功、向量库一条都没写」的不一致。

为此所有 ingestion service 都通过 `VectorBatchWriter` 写入，单批流程为：

1. 计算批内所有文档的派生 id；
2. 调用 `vectorStore.delete(ids)`：底层执行 `DELETE WHERE id IN (...)`，不存在的 id 被静默忽略；
3. 调用 `vectorStore.add(documents)` 写入最新内容。

效果：

- 重复导入相同 `chunkId`/`policyId` 时向量库稳定覆盖，最终一致。
- 业务表（`policy_knowledge`、`contracts`）已通过 `ON CONFLICT DO UPDATE` 做事务级幂等；向量库幂等让两端在重复导入下保持一致。

### 6.2 分批：满足上游 embedding 接口的 batch 上限

不同 embedding 模型对单次请求的输入条数限制不同，超过会被服务端 400 拒绝并整批失败：

| 模型 | 单次最大条数 |
|---|---|
| DashScope `text-embedding-v1/v2/v3`（OpenAI 兼容模式） | 10 |
| OpenAI `text-embedding-3-small/large` | 2048 |
| 其它自托管 / 第三方模型 | 视实现而定 |

由于 `VectorStore.add` 内部会一次性把整批文档喂给 embedding 调用，**应用层必须自行切片**。`VectorBatchWriter` 按 `app.embedding.batch-size`（默认 10，可由 `EMBEDDING_BATCH_SIZE` 环境变量覆盖）切分文档列表，对每个切片重复执行第 6.1 节的「delete + add」流程。

### 6.3 失败语义

- 单批 `add` 失败会立即向上抛异常；前序已成功的批次保留在向量库。
- 上层应用服务（如 `PolicyKnowledgeApplicationService`）应捕获异常并通过响应中的 `vectorIngestionWarning` 字段透出，不阻塞业务表已成功的事实。
- 客户端按相同请求体重试即可补偿：已成功批次会被 delete + add 覆盖，失败批次重新尝试，整体最终一致。

## 7. 双通道上下文拼装

Prompt 中必须明确区分两类上下文：

- `合同条款上下文`：只包含当前 `contractId` 命中的条款块，用于陈述合同事实。
- `制度依据上下文`：只包含适用当前合同类型的制度条目，用于解释风险原因、输出依据 ID、生成证据清单和审批升级建议。

模型输出要求：

- 合同问答：可返回 `retrievedChunkIds` 与 `retrievedPolicyIds`；如问题只涉及合同事实，制度依据可为空。
- 风险检查：每个 `riskItem` 应尽量同时包含 `relatedClauseChunkIds` 和 `relatedPolicyIds`。
- 审批辅助：`checklist` 优先由命中制度条目的 `requiredEvidence` 生成，`suggestion` 可引用 `escalationRole`。
