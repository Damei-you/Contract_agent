package com.yy.agent.contractmvp.api.dto;

import com.yy.agent.contractmvp.domain.ClauseChunk;

/**
 * 合同条款块详情响应，供前端从 AgentTrace 溯源到具体合同条款正文。
 *
 * @param id               条款块主键
 * @param contractId       所属合同 id
 * @param clauseCode       条款编码
 * @param clauseTitle      条款标题
 * @param clauseCategory   条款分类
 * @param sourceSection    来源章节
 * @param textForEmbedding 条款正文
 */
public record ContractClauseChunkResponse(
        String id,
        String contractId,
        String clauseCode,
        String clauseTitle,
        String clauseCategory,
        String sourceSection,
        String textForEmbedding
) {

    public static ContractClauseChunkResponse from(ClauseChunk chunk) {
        return new ContractClauseChunkResponse(
                chunk.id(),
                chunk.contractId(),
                chunk.clauseCode(),
                chunk.clauseTitle(),
                chunk.clauseCategory(),
                chunk.sourceSection(),
                chunk.textForEmbedding()
        );
    }
}
