package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 合同问答请求体。
 *
 * @param question 用户自然语言问题，必填
 */
public record ContractQaRequest(@NotBlank String question) {
}
