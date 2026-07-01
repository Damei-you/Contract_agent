package com.yy.agent.contractmvp.ai.rag;

import com.yy.agent.contractmvp.domain.ContractType;

import java.util.List;

/**
 * 政策/制度通道 RAG 检索抽象：按合同类型限定可适用制度集合，并基于 query 做相似度排序。
 * <p>
 * 与 {@link RagRetriever}（合同条款通道）正交：合同通道按 {@code contractId} 限定单合同条款，
 * 制度通道按 {@code contractType} 限定全局制度知识库。两通道结果由
 * {@link com.yy.agent.contractmvp.ai.AiContractAssistant} 拼装进同一 Prompt。
 */
public interface PolicyRagRetriever {

    /**
     * 检索可适用当前合同类型的制度依据片段。
     *
     * @param contractType 当前合同类型，{@code null} 时实现应返回空列表
     * @param query        检索语句（用户问题或风险/审批拼接词）
     * @param topK         返回条数上限，实现侧应保证至少为 1
     * @return 命中列表，可为空
     */
    List<PolicyRagDocument> retrieve(ContractType contractType, String query, int topK);

    /**
     * 不限定合同类型检索制度依据片段，供“政策制度问答”等全局制度场景使用。
     *
     * @param query 检索语句
     * @param topK  返回条数上限，实现侧应保证至少为 1
     * @return 命中列表，可为空
     */
    default List<PolicyRagDocument> retrieve(String query, int topK) {
        return List.of();
    }
}
