package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 导入合同时的单条条款块 DTO，将映射为 {@link com.yy.agent.contractmvp.domain.ClauseChunk}。
 *
 * @param id               块 id，可空（服务端按序生成）
 * @param clauseCode       条款编码，可空
 * @param clauseTitle      标题，可空
 * @param clauseCategory   分类，可空
 * @param textForEmbedding 参与检索的正文，必填
 */
public record ImportChunkDto(
        String id,
        String clauseCode,
        String clauseTitle,
        String clauseCategory,
        @NotBlank String textForEmbedding
) {
}
