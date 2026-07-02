package com.yy.agent.contract.api.dto;

import com.yy.agent.contract.ai.agent.AgentTrace;

import java.util.List;

/**
 * 政策/制度问答响应：模型答案 + 命中的制度依据 id 与检索轨迹。
 *
 * @param answer             模型生成的回答正文
 * @param retrievedPolicyIds 制度通道命中的 {@link com.yy.agent.contract.domain.PolicyKnowledgeItem#policyId()} 列表
 * @param agentTrace         本轮问答使用的制度检索证据轨迹
 */
public record PolicyQaResponse(
        String answer,
        List<String> retrievedPolicyIds,
        List<AgentTrace> agentTrace
) {

    public PolicyQaResponse(String answer, List<String> retrievedPolicyIds) {
        this(answer, retrievedPolicyIds, List.of());
    }

    public PolicyQaResponse {
        if (answer == null) {
            answer = "";
        }
        retrievedPolicyIds = retrievedPolicyIds == null ? List.of() : List.copyOf(retrievedPolicyIds);
        agentTrace = agentTrace == null ? List.of() : List.copyOf(agentTrace);
    }
}
