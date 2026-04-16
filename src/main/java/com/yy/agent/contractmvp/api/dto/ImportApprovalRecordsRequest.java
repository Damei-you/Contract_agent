package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 某合同审批记录的全量导入请求。
 */
public record ImportApprovalRecordsRequest(
        @NotEmpty @Valid List<ImportApprovalRecordDto> records
) {
}
