# RAG 检索评测集

本目录用于评估合同条款通道、制度通道以及后续混合检索/重排序策略的效果。评测集和数据库运行数据解耦，避免当前开发库里的历史脏数据影响对比。

## 当前数据盘点

- `data/vectorization/01_contract_meta.csv`：2 份合同。
- `data/vectorization/02_clause_chunks.csv`：13 个条款块，其中服务合同只有 3 个条款，缺少付款、发票、验收等财务维度。
- `data/vectorization/03_approval_policy_extended.csv`：制度知识较完整，可复用 `POL-*` 编号。
- `data/vectorization/05_approval_records.csv`：审批记录可用于演示，但存在服务合同引用采购合同 `c004` 条款的情况，不适合作为检索评测金标。

因此本目录新增一套更稳定的评测 seed，专门覆盖付款、税务、验收、责任、数据安全、分包驻场、审批角色等检索场景。

## 文件说明

| 文件 | 用途 |
| --- | --- |
| `seed-contracts.json` | 两份评测合同，可逐条 POST 到 `/api/contracts/import`，已带 `overwriteConfirmed=true`。 |
| `seed-policies.json` | 评测用制度知识库，可 POST 到 `/api/policies/import`。 |
| `seed-approval-records.json` | 评测用审批历史，可按合同 POST 到 `/api/contracts/{id}/approval-records/import`。 |
| `rag-eval-cases.jsonl` | 评测问题集，每行一个 query/case。 |
| `rag-qrels.jsonl` | JSONL 版金标证据，包含 channel、docId、relevance，便于 Java/JUnit 读取。 |
| `rag-qrels.trec` | TREC qrels 格式，可直接给 `pytrec_eval`、`ranx`、`ir-measures` 使用。 |
| `import-eval-data.ps1` | 通过本地后端 API 导入 seed 数据，触发业务表和向量库写入。 |
| `reset-eval-db.sql` | 可选清库 SQL，仅用于一次性开发/测试库。 |

## 推荐导入顺序

1. 导入 `seed-policies.json` 到 `/api/policies/import`。
2. 将 `seed-contracts.json` 中的每个 `contracts[]` 元素逐条 POST 到 `/api/contracts/import`。
3. 将 `seed-approval-records.json` 中的每组记录 POST 到 `/api/contracts/{contractId}/approval-records/import`。

本地后端启动后，可直接运行：

```powershell
.\data\evaluation\import-eval-data.ps1 -BaseUrl http://localhost:8088
```

如需完全干净的库，可先在一次性开发库中执行 `reset-eval-db.sql`，再运行导入脚本。

## 执行 baseline 评测

导入 seed 后，执行当前检索策略评测：

```powershell
mvn -Dtest=RagRetrievalEvaluationIT "-Drag.eval.strategy=current-retriever" test
```

该命令不会调用最终问答接口，也不会让大模型生成答案；它只调用当前检索器：

- 合同通道：`RagRetriever#retrieve(contractId, query, topK)`
- 制度通道：`PolicyRagRetriever#retrieve(contractType, query, topK)`

输出目录：

| 文件 | 说明 |
| --- | --- |
| `target/rag-eval/{strategy}-run.csv` | 每个 case 的命中明细，适合人工检查。 |
| `target/rag-eval/{strategy}-run.trec` | TREC run 格式，适合接 `pytrec_eval`、`ranx`、`ir-measures`。 |
| `target/rag-eval/{strategy}-report.md` | 自动汇总的 Precision/Recall/MRR/nDCG 报告。 |

面试讲解时可以这样描述：

1. 先固定一套评测集：`rag-eval-cases.jsonl` 是问题，`rag-qrels.jsonl` 是人工标注的标准证据。
2. 评测阶段只跑当前检索器，不调用 LLM，避免回答生成随机性影响检索评估。
3. 每个问题得到一组按分数排序的 `chunkId/policyId`。
4. 将检索结果和 qrels 对齐，计算 `Recall@K`、`Precision@K`、`MRR@K`、`nDCG@K`。
5. 加混合检索或重排序后，用同一套 cases/qrels 再跑一次，对比两份报告即可量化提升。

## 启用阿里云 qwen3-rerank

系统默认只使用本地规则重排，避免未确认时产生外部模型调用费用。需要接入百炼文本排序时，先设置：

```powershell
$env:RAG_RERANK_ENABLED = "true"
$env:ALIYUN_BAILIAN_WORKSPACE_ID = "<你的百炼业务空间ID>"
$env:DASHSCOPE_API_KEY = "<你的百炼API Key>"
```

如需覆盖完整接口地址，也可以设置 `RAG_RERANK_ENDPOINT`，格式示例：

```text
https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-api/v1/reranks
```

开启后再跑同一套评测：

```powershell
mvn -Dtest=RagRetrievalEvaluationIT "-Drag.eval.strategy=aliyun-qwen3-rerank" "-Drag.eval.output-prefix=aliyun-qwen3-rerank" test
```

对比 `target/rag-eval/current-retriever-report.md` 和 `target/rag-eval/aliyun-qwen3-rerank-report.md`，重点看 `Recall@K`、`Precision@K`、`MRR@K`、`nDCG@K` 是否提升，同时关注报告里的 `latencyMs` 明细。

## 评测指标建议

- 合同条款通道：`Recall@8`、`MRR@4`、`nDCG@8`、`Precision@4`。
- 制度通道：`Recall@10`、`MRR@4`、`nDCG@8`、`PolicyDomain HitRate`。
- 综合检索：按 `docId` 前缀区分 `clause:` 与 `policy:`，分别计算后再汇总。
- 延迟：记录 `avg/p95 latencyMs`，避免重排序提升效果但不可用。

检索链路的设计说明、MMR 公式和面试讲解口径见 `docs/RAG_RETRIEVAL_OPTIMIZATION.md`。

建议验收线：

```text
candidate Recall@8 >= baseline Recall@8 + 0.10
candidate MRR@4 >= baseline MRR@4 * 1.15
candidate nDCG@8 >= baseline nDCG@8 + 0.08
candidate Precision@4 不低于 baseline
candidate P95 latencyMs < 500
跨合同误召回 = 0
```
