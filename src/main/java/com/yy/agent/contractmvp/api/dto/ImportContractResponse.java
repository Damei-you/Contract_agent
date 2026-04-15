package com.yy.agent.contractmvp.api.dto;

/**
 * 导入合同成功后的响应，仅返回落库的主键 id。
 *
 * @param contractId 新建或指定的合同 id
 */
public record ImportContractResponse(String contractId) {
}
