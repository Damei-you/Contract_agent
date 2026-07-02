package com.yy.agent.contract.api.dto;

/**
 * 审批记录全量导入结果。
 */
public record ImportApprovalRecordsResponse(
        String contractId,
        int importedCount
) {
}
