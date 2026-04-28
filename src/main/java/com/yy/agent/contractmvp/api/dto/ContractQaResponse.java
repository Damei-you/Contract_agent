package com.yy.agent.contractmvp.api.dto;

import java.util.List;

/**
 * 合同问答响应：模型答案 + 双通道 RAG 命中的合同条款 id 与制度依据 id，便于前端高亮或溯源。
 *
 * @param answer              模型生成的回答正文
 * @param retrievedChunkIds   合同条款通道命中的 {@link com.yy.agent.contractmvp.domain.ClauseChunk#id()} 列表
 * @param retrievedPolicyIds  制度通道命中的 {@link com.yy.agent.contractmvp.domain.PolicyKnowledgeItem#policyId()} 列表
 */
public record ContractQaResponse(
        String answer,
        List<String> retrievedChunkIds,
        List<String> retrievedPolicyIds
) {

    /**
     * 紧凑构造：列表 null 转空列表，确保接口契约稳定。
     */
    public ContractQaResponse {
        retrievedChunkIds = retrievedChunkIds == null ? List.of() : List.copyOf(retrievedChunkIds);
        retrievedPolicyIds = retrievedPolicyIds == null ? List.of() : List.copyOf(retrievedPolicyIds);
    }
}
