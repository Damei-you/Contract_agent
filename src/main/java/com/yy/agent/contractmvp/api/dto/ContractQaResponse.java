package com.yy.agent.contractmvp.api.dto;

import java.util.List;

/**
 * 合同问答响应：模型答案 + 本次 RAG 命中的条款块 id，便于前端高亮或溯源。
 *
 * @param answer             模型生成的回答正文
 * @param retrievedChunkIds 检索到的 {@link com.yy.agent.contractmvp.domain.ClauseChunk#id()} 列表
 */
public record ContractQaResponse(String answer, List<String> retrievedChunkIds) {
}
