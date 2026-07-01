package com.yy.agent.contractmvp.api.dto;

import com.yy.agent.contractmvp.ai.agent.AgentTrace;

import java.util.List;

/**
 * 合同问答响应：模型答案 + 命中的合同条款 id，以及可选的制度依据 id，便于前端高亮或溯源。
 *
 * @param answer              模型生成的回答正文
 * @param retrievedChunkIds   合同条款通道命中的 {@link com.yy.agent.contractmvp.domain.ClauseChunk#id()} 列表
 * @param retrievedPolicyIds  制度通道命中的 {@link com.yy.agent.contractmvp.domain.PolicyKnowledgeItem#policyId()} 列表
 * @param agentTrace          本轮问答使用的检索证据轨迹
 */
public record ContractQaResponse(
        String answer,
        List<String> retrievedChunkIds,
        List<String> retrievedPolicyIds,
        List<AgentTrace> agentTrace
) {

    /**
     * 兼容旧调用方：未显式传入 trace 时返回空轨迹列表。
     */
    public ContractQaResponse(
            String answer,
            List<String> retrievedChunkIds,
            List<String> retrievedPolicyIds
    ) {
        this(answer, retrievedChunkIds, retrievedPolicyIds, List.of());
    }

    /**
     * 紧凑构造：列表 null 转空列表，确保接口契约稳定。
     */
    public ContractQaResponse {
        if (answer == null) {
            answer = "";
        }
        retrievedChunkIds = retrievedChunkIds == null ? List.of() : List.copyOf(retrievedChunkIds);
        retrievedPolicyIds = retrievedPolicyIds == null ? List.of() : List.copyOf(retrievedPolicyIds);
        agentTrace = agentTrace == null ? List.of() : List.copyOf(agentTrace);
    }
}
