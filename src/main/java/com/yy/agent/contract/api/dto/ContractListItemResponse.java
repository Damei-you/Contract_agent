package com.yy.agent.contract.api.dto;

import com.yy.agent.contract.domain.Contract;

/** 合同选择器使用的精简合同信息。 */
public record ContractListItemResponse(
        String id,
        String type,
        String partyAName,
        String partyBName
) {
    public static ContractListItemResponse from(Contract contract) {
        return new ContractListItemResponse(
                contract.id(),
                contract.type().name(),
                contract.partyAName(),
                contract.partyBName()
        );
    }
}
