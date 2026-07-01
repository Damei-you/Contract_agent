package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 合同问答请求体。
 *
 * @param question              用户自然语言问题，必填
 * @param includePolicyEvidence 是否同时检索并引用制度依据，默认 false
 */
public record ContractQaRequest(
        @NotBlank String question,
        boolean includePolicyEvidence
) {

    public ContractQaRequest(String question) {
        this(question, false);
    }
}
