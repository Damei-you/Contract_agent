package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 全量导入单条审批记录。
 */
public record ImportApprovalRecordDto(
        String id,
        @Min(1) int stepNo,
        @NotBlank String approverRole,
        @NotBlank String decision,
        OffsetDateTime decisionTime,
        String commentSummary,
        List<String> linkedPolicyIds,
        List<String> linkedClauseChunkIds,
        @Valid List<ImportApprovalRiskItemDto> riskItems,
        String vectorDocId
) {
}
