package com.yy.agent.contractmvp.api.dto;

import java.util.List;

/**
 * 审批辅助响应：模型给出的结论建议与可执行核对清单，并附本次双通道 RAG 命中来源便于溯源。
 *
 * @param suggestion          对当前审批节点的建议结论
 * @param checklist           待核对事项列表（模型 JSON 中的字符串数组）
 * @param retrievedChunkIds   合同条款通道命中 id 列表
 * @param retrievedPolicyIds  制度通道命中 id 列表
 */
public record ApprovalAssistResponse(
        String suggestion,
        List<String> checklist,
        List<String> retrievedChunkIds,
        List<String> retrievedPolicyIds
) {

    /**
     * 紧凑构造：列表 null 转空列表，建议正文 null 转空串，确保接口契约稳定。
     */
    public ApprovalAssistResponse {
        if (suggestion == null) {
            suggestion = "";
        }
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
        retrievedChunkIds = retrievedChunkIds == null ? List.of() : List.copyOf(retrievedChunkIds);
        retrievedPolicyIds = retrievedPolicyIds == null ? List.of() : List.copyOf(retrievedPolicyIds);
    }
}
