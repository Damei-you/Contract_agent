package com.yy.agent.contractmvp.api.dto;

/**
 * 审批记录全量导入结果。
 */
public record ImportApprovalRecordsResponse(
        String contractId,
        int importedCount
) {
}
