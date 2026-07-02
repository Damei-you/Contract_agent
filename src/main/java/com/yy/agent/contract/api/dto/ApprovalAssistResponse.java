package com.yy.agent.contract.api.dto;

import com.yy.agent.contract.ai.agent.AgentTrace;

import java.util.List;

/**
 * 审批辅助响应：模型给出的结论建议、可执行核对清单、双通道 RAG 命中来源，以及多 Agent 协作轨迹。
 *
 * @param suggestion         对当前审批节点的建议结论
 * @param checklist          待核对事项列表，来自模型 JSON 中的字符串数组
 * @param retrievedChunkIds  合同条款通道命中 id 列表
 * @param retrievedPolicyIds 制度通道命中 id 列表
 * @param agentTrace         多 Agent 执行轨迹，按编排顺序排列
 */
public record ApprovalAssistResponse(
        String suggestion,
        List<String> checklist,
        List<String> retrievedChunkIds,
        List<String> retrievedPolicyIds,
        List<AgentTrace> agentTrace
) {

    /**
     * 兼容旧调用方的构造器：未显式传入 trace 时返回空轨迹列表。
     *
     * @param suggestion         对当前审批节点的建议结论
     * @param checklist          待核对事项列表
     * @param retrievedChunkIds  合同条款通道命中 id 列表
     * @param retrievedPolicyIds 制度通道命中 id 列表
     */
    public ApprovalAssistResponse(
            String suggestion,
            List<String> checklist,
            List<String> retrievedChunkIds,
            List<String> retrievedPolicyIds
    ) {
        this(suggestion, checklist, retrievedChunkIds, retrievedPolicyIds, List.of());
    }

    /**
     * 紧凑构造：列表字段 null 转为空列表，建议正文 null 转为空串，确保接口契约稳定。
     */
    public ApprovalAssistResponse {
        if (suggestion == null) {
            suggestion = "";
        }
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
        retrievedChunkIds = retrievedChunkIds == null ? List.of() : List.copyOf(retrievedChunkIds);
        retrievedPolicyIds = retrievedPolicyIds == null ? List.of() : List.copyOf(retrievedPolicyIds);
        agentTrace = agentTrace == null ? List.of() : List.copyOf(agentTrace);
    }
}
