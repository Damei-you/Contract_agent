package com.yy.agent.contract.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 政策/制度问答请求体。
 *
 * @param question     用户自然语言问题，必填
 * @param contractType 可选合同类型，用于收敛适用制度范围；支持 procurement/service/采购合同/服务合同
 */
public record PolicyQaRequest(
        @NotBlank String question,
        String contractType
) {

    public PolicyQaRequest {
        question = question == null ? "" : question.trim();
        contractType = contractType == null ? "" : contractType.trim();
    }
}
