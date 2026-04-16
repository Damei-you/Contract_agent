package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 审批记录导入时附带的结构化风险项。
 */
public record ImportApprovalRiskItemDto(
        @NotBlank String code,
        @NotBlank String severity,
        String detail,
        List<String> relatedClauseChunkIds,
        List<String> relatedPolicyIds
) {
}
