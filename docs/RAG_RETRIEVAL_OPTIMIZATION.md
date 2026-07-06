# RAG 检索链路优化说明

本文档说明合同审核场景下 RAG 检索链路的设计、重排序策略、MMR 多样性截断和离线评测方法，便于面试讲解和后续维护。

## 1. 为什么单纯向量检索不够

向量检索负责快速找到语义相近的候选片段，但“语义相近”不一定等于“业务上最能回答问题”。在合同审核场景中，付款方式、付款节点、付款审批、发票要求、违约责任等内容经常共享相似词，纯向量排序容易把主题接近但证据价值较弱的片段排到前面。

常见问题包括：

- embedding 会压缩文本细节，金额、日期、否定词、适用条件等信息不一定能被精细区分。
- 多个条款语义接近，向量相似度只能粗排，难以判断哪个片段真正回答当前问题。
- chunk 可能包含多个业务点，向量表示会变成混合语义。
- 合同审核依赖 `contractId`、合同类型、制度领域等业务约束，不能只靠向量距离判断。

因此系统采用两阶段检索：先扩大召回候选，再通过重排序和多样性截断选出最终证据。

## 2. 总体链路

```text
用户问题
  |
  v
Query 扩展
  |
  v
pgvector 候选召回
  |
  +--> 合同条款通道：docType=contract_clause && contractId=当前合同
  |
  +--> 制度知识通道：docType=policy，并按合同类型过滤
  |
  v
本地业务打分 + qwen3-rerank 模型打分
  |
  v
融合分数排序
  |
  v
MMR 多样性截断
  |
  v
TopK 证据进入 Prompt / AgentTrace
```

合同条款通道入口为 `PgVectorRagRetriever#retrieve`，制度知识通道入口为 `PgVectorPolicyRagRetriever#retrieve`。两个通道都会先扩大候选集，再调用 `RagResultReranker` 做重排和 MMR 截断。

## 3. 候选召回

合同条款通道先按当前合同收敛范围：

```java
SearchRequest request = SearchRequest.builder()
        .query(expandedQuery)
        .topK(candidates)
        .filterExpression("docType == 'contract_clause' && contractId == '" + escapeLiteral(contractId) + "'")
        .build();
```

制度知识通道先限制 `docType=policy`，再在 Java 侧按当前合同类型过滤 `appliesToContractType`。

最终 TopK 不直接由向量库返回，而是先取更大的候选集：

```java
private int candidateTopK(int finalTopK) {
    return Math.min(maxCandidateK, Math.max(candidateK, finalTopK * 5));
}
```

这样可以让向量检索承担“尽量别漏”的召回职责，让后续 rerank 承担“谁更相关”的精排职责。

## 4. 重排序策略

`RagResultReranker` 会先计算本地业务相关性分数，再按需融合 qwen3-rerank 模型分数。

本地打分主要包含：

- pgvector 返回的原始相似度分数。
- query 关键词在标题、正文、制度触发词、证据要求中的命中加分。
- 制度领域、制度编号、严重等级等业务元数据加分。

如果 qwen3-rerank 可用，则调用模型对 `query + candidate document` 做成对相关性判断。融合公式为：

```java
return modelWeight * modelScore + (1.0 - modelWeight) * localScore;
```

默认配置下 `modelWeight=0.70`，表示模型重排分数占主要权重，本地规则用于保留业务先验和兜底能力。如果模型不可用或调用失败，则自动 fallback 到本地重排。

## 5. MMR 多样性截断

MMR 的目标是避免最终 TopK 被同一类证据占满。例如 Top4 全是付款条款的相邻 chunk，虽然相似度都高，但给大模型的信息重复，会浪费上下文窗口。

当前实现是简化版 MMR：先根据条款或制度元数据给候选分组，再在每轮选择时同时考虑相关性和同组惩罚。

核心公式：

```java
double mmr = MMR_LAMBDA * candidate.score()
        - (1.0 - MMR_LAMBDA) * diversityPenalty
        - groupCrowdingPenalty;
```

其中：

- `candidate.score()` 是融合后的相关性分数。
- `MMR_LAMBDA=0.95`，说明系统优先保证相关性，多样性只做轻量约束。
- `diversityPenalty` 用于惩罚与已选结果同组的候选。
- `groupCrowdingPenalty` 用于避免同一组结果出现过多。

合同条款会按内容归入 `payment`、`tax`、`delivery_acceptance`、`liability`、`termination`、`security` 等组；制度知识主要按 `policyDomain` 分组。

面试中可以概括为：

> 向量检索负责扩大召回，rerank 负责提高相关性排序，MMR 负责让最终 TopK 既准确又不重复。当前实现中 lambda 设为 0.95，是因为合同审核场景更重视证据准确性，多样性只是为了避免上下文被同类 chunk 占满。

## 6. 离线评测

评测数据位于 `data/evaluation`：

- `rag-eval-cases.jsonl`：检索问题集。
- `rag-qrels.jsonl`：人工标注的标准证据。
- `rag-qrels.trec`：TREC qrels 格式，便于接入通用 IR 评测工具。

评测流程：

1. 固定同一套 cases 和 qrels。
2. 只调用检索器，不调用最终 LLM 生成，避免生成随机性影响评测。
3. 收集每个问题的 TopK 检索结果。
4. 对齐金标证据，计算 `Recall@K`、`Precision@K`、`MRR@K`、`nDCG@K`。
5. 对比纯向量、向量 + rerank、向量 + rerank + MMR 的报告。

常用命令：

```powershell
mvn -Dtest=RagRetrievalEvaluationIT "-Drag.eval.strategy=current-retriever" test
```

启用 qwen3-rerank 后：

```powershell
$env:RAG_RERANK_ENABLED = "true"
$env:ALIYUN_BAILIAN_WORKSPACE_ID = "<workspace-id>"
$env:DASHSCOPE_API_KEY = "<api-key>"
mvn -Dtest=RagRetrievalEvaluationIT "-Drag.eval.strategy=aliyun-qwen3-rerank" "-Drag.eval.output-prefix=aliyun-qwen3-rerank" test
```

## 7. 面试回答口径

可以用下面这段作为项目讲解：

> 我没有直接用向量检索 TopK 作为最终上下文，而是把 RAG 拆成召回、重排、多样性截断和离线评测几个环节。召回阶段用 pgvector 在当前合同或适用制度范围内扩大候选，保证正确证据尽量进入候选集；重排阶段融合本地业务规则和 qwen3-rerank 成对相关性分数，把真正能回答问题的片段排前；最后用 MMR 轻量惩罚同组重复证据，避免 TopK 都来自同一类条款。这样能让进入大模型的上下文更准确、更少重复，也能通过 Recall、Precision、MRR 等指标量化验证。
