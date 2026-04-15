package com.yy.agent.contractmvp.ai.rag;

/**
 * RAG 检索命中片段：将条款块以统一形态返回给编排层。
 *
 * @param id    条款块 id
 * @param title 条款标题（或章节名）
 * @param text  参与检索的正文
 * @param score 相关性分数（关键词命中次数或向量相似度，具体由实现定义）
 */
public record RagDocument(String id, String title, String text, double score) {
}
