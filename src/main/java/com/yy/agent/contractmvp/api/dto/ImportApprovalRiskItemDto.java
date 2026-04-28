package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 审批记录导入时附带的结构化风险项。
 *
 * @param code                   风险编码
 * @param severity               严重度（{@code HIGH/MEDIUM/LOW} 或中文）
 * @param detail                 详情
 * @param relatedClauseChunkIds  关联合同条款 id 列表，可空
 * @param relatedPolicyIds       关联制度条目 id 列表，可空
 * @param requiredEvidence       建议核对/补充的材料清单，可空
 * @param escalationRole         建议升级/会签角色，可空
 */
public record ImportApprovalRiskItemDto(
        @NotBlank String code,
        @NotBlank String severity,
        String detail,
        List<String> relatedClauseChunkIds,
        List<String> relatedPolicyIds,
        List<String> requiredEvidence,
        String escalationRole
) {
}
