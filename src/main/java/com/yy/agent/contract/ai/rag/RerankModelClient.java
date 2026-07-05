package com.yy.agent.contract.ai.rag;

import java.util.List;

/**
 * 二阶段精排模型客户端。
 * <p>
 * 向量检索负责从 pgvector 中召回候选文档，本接口只对这些候选做 query-document 相关性重排。
 */
interface RerankModelClient {

    boolean isAvailable();

    List<RerankScore> rerank(String query, List<String> documents);
}
