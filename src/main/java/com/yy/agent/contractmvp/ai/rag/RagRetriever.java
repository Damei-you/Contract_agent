package com.yy.agent.contractmvp.ai.rag;

import java.util.List;

/**
 * 条款检索抽象：按合同 id 限定范围，根据查询语句从向量索引返回若干 {@link RagDocument}。
 */
public interface RagRetriever {

    /**
     * 在指定合同的条款块集合中检索与 query 最相关的 topK 条。
     *
     * @param contractId 合同 id
     * @param query      用户问题或拼接的检索词
     * @param topK       返回条数上限（实现侧可保证至少为 1）
     * @return 命中列表，可为空（无条款数据时）
     */
    List<RagDocument> retrieve(String contractId, String query, int topK);
}
