package com.yy.agent.contract.ai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 PgVector 的 RAG 检索实现。
 * <p>
 * 检索约束：必须先按 contractId 过滤，再在候选集合中做向量相似度 topK。
 */
@Component
@Primary
@Profile("!test")
public class PgVectorRagRetriever implements RagRetriever {

    private final VectorStore vectorStore;
    private final int candidateK;
    private final int maxCandidateK;

    public PgVectorRagRetriever(
            VectorStore vectorStore,
            @Value("${app.rag.clause-candidate-k:32}") int candidateK,
            @Value("${app.rag.max-candidate-k:40}") int maxCandidateK
    ) {
        this.vectorStore = vectorStore;
        this.candidateK = Math.max(1, candidateK);
        this.maxCandidateK = Math.max(this.candidateK, maxCandidateK);
    }

    @Override
    public List<RagDocument> retrieve(String contractId, String query, int topK) {
        int k = Math.max(1, topK);
        int candidates = candidateTopK(k);
        String expandedQuery = RagQueryExpander.expand(query, "合同条款");

        // 先按合同和文档类型收敛候选，再扩大 candidateK 做向量召回，最后在 Java 侧重排和多样性截断。
        SearchRequest request = SearchRequest.builder()
                .query(expandedQuery)
                .topK(candidates)
                .filterExpression("docType == 'contract_clause' && contractId == '" + escapeLiteral(contractId) + "'")
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<RagDocument> candidatesDocs = documents.stream()
                .map(PgVectorRagRetriever::toRagDocument)
                .toList();
        return RagResultReranker.rerankClauses(candidatesDocs, query, k);
    }

    private int candidateTopK(int finalTopK) {
        return Math.min(maxCandidateK, Math.max(candidateK, finalTopK * 5));
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
