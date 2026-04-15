package com.yy.agent.contractmvp.ai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 PgVector 的 RAG 检索实现。
 * <p>
 * 检索约束：必须先按 contractId 过滤，再在候选集合中做向量相似度 topK。
 */
@Component
@Primary
public class PgVectorRagRetriever implements RagRetriever {

    private final VectorStore vectorStore;

    public PgVectorRagRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<RagDocument> retrieve(String contractId, String query, int topK) {
        int k = Math.max(1, topK);
        String normalizedQuery = (query == null || query.isBlank()) ? "合同条款" : query.trim();

        SearchRequest request = SearchRequest.builder()
                .query(normalizedQuery)
                .topK(k)
                .filterExpression("contractId == '" + escapeLiteral(contractId) + "'")
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream().map(PgVectorRagRetriever::toRagDocument).toList();
    }

    private static RagDocument toRagDocument(Document document) {
        String chunkId = readMetadataAsString(document, "chunkId", document.getId());
        String clauseTitle = readMetadataAsString(document, "clauseTitle", "");
        String text = document.getText() == null ? "" : document.getText();
        double score = document.getScore() == null ? 0.0 : document.getScore();
        return new RagDocument(chunkId, clauseTitle, text, score);
    }

    private static String readMetadataAsString(Document document, String key, String defaultValue) {
        Object value = document.getMetadata() == null ? null : document.getMetadata().get(key);
        if (value == null) {
            return defaultValue;
        }
        String s = value.toString();
        return s.isBlank() ? defaultValue : s;
    }

    private static String escapeLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
