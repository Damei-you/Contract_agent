# PG 向量检索映射规范（合同问答）

面向 pgvector 改造，定义如下固定映射规则。

## 1. 映射粒度

- 1 个 `chunk` -> 1 条向量文档（禁止一个 chunk 拆多条，或多 chunk 合一条）。

## 2. 向量文档结构

- `id`：`{contractId}:{chunkId}`
- `content`：`【{clauseTitle}】\n{textForEmbedding}`
- `metadata`（最小固定集合）：
  - `contractId`
  - `chunkId`
  - `clauseTitle`
  - `clauseCode`
  - `clauseCategory`

## 3. 字段映射来源

- `contractId` <- `ClauseChunk.contractId`
- `chunkId` <- `ClauseChunk.id`
- `clauseTitle` <- `ClauseChunk.clauseTitle`
- `clauseCode` <- `ClauseChunk.clauseCode`
- `clauseCategory` <- `ClauseChunk.clauseCategory`
- `textForEmbedding` <- `ClauseChunk.textForEmbedding`

## 4. 检索约束（强制）

- 必须先按 `contractId` 过滤候选集合，再在该合同范围内执行向量相似度排序并取 `topK`。
- 禁止跨合同全库直接做 `topK` 后再按 `contractId` 二次过滤。

## 5. 推荐 SQL 形态（pgvector）

```sql
SELECT id, content, metadata
FROM contract_clause_vectors
WHERE metadata->>'contractId' = :contractId
ORDER BY embedding <=> :queryEmbedding
LIMIT :topK;
```
