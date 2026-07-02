package com.yy.agent.contract.mapper.model;

import com.yy.agent.contract.domain.ApprovalDecision;

import java.time.OffsetDateTime;

/**
 * 审批记录持久化行模型；列表/结构化字段以 JSON 字符串落库。
 */
public record ApprovalRecordRow(
        String contractId,
        String approvalRecordId,
        Integer stepNo,
        String approverRole,
        ApprovalDecision decision,
        OffsetDateTime decisionTime,
        String commentSummary,
        String linkedPolicyIdsJson,
        String linkedClauseChunkIdsJson,
        String riskItemsJson,
        String vectorDocId
) {
}
