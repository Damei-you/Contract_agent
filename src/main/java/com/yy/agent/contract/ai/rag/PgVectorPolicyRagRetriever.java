package com.yy.agent.contract.ai.rag;

import com.yy.agent.contract.domain.ContractType;
import com.yy.agent.contract.domain.PolicyKnowledgeItem;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 PgVector 的政策/制度通道 RAG 检索实现。
 * <p>
 * 检索约束：先按 {@code metadata.docType=policy} 过滤候选，再在 Java 侧按当前合同类型匹配
 * {@code metadata.appliesToContractType}（{@code ;} 分隔字符串）后取 topK，避免向量库需要 LIKE 操作符。
 */
@Component
@Profile("!test")
public class PgVectorPolicyRagRetriever implements PolicyRagRetriever {

    private final VectorStore vectorStore;
    private final RagResultReranker reranker;
    private final int candidateK;
    private final int maxCandidateK;

    public PgVectorPolicyRagRetriever(
            VectorStore vectorStore,
            RagResultReranker reranker,
            @Value("${app.rag.policy-candidate-k:40}") int candidateK,
            @Value("${app.rag.max-candidate-k:40}") int maxCandidateK
    ) {
        this.vectorStore = vectorStore;
        this.reranker = reranker;
        this.candidateK = Math.max(1, candidateK);
        this.maxCandidateK = Math.max(this.candidateK, maxCandidateK);
    }

    @Override
    public List<PolicyRagDocument> retrieve(ContractType contractType, String query, int topK) {
        if (contractType == null) {
            return List.of();
        }
        int k = Math.max(1, topK);
        List<Document> documents = searchPolicyDocuments(query, candidateTopK(k));
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        String contractTypeName = contractType.displayName();
        List<PolicyRagDocument> candidates = documents.stream()
                .filter(d -> appliesToContractType(d, contractTypeName))
                .map(PgVectorPolicyRagRetriever::toPolicyRagDocument)
                .toList();
        return reranker.rerankPolicies(candidates, query, k);
    }

    @Override
    public List<PolicyRagDocument> retrieve(String query, int topK) {
        int k = Math.max(1, topK);
        List<Document> documents = searchPolicyDocuments(query, candidateTopK(k));
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<PolicyRagDocument> candidates = documents.stream()
                .map(PgVectorPolicyRagRetriever::toPolicyRagDocument)
                .toList();
        return reranker.rerankPolicies(candidates, query, k);
    }

    private List<Document> searchPolicyDocuments(String query, int topK) {
        String expandedQuery = RagQueryExpander.expand(query, "制度依据 合规要求");
        SearchRequest request = SearchRequest.builder()
                .query(expandedQuery)
                .topK(Math.max(1, topK))
                .filterExpression("docType == 'policy'")
                .build();
        return vectorStore.similaritySearch(request);
    }

    private int candidateTopK(int finalTopK) {
        return Math.min(maxCandidateK, Math.max(candidateK, finalTopK * 5));
    }

    private static boolean appliesToContractType(Document document, String contractTypeName) {
        String applies = readMetadataAsString(document, "appliesToContractType", "");
        if (applies.isBlank()) {
            return false;
        }
        for (String t : PolicyKnowledgeItem.splitMulti(applies)) {
            if (t.equals(contractTypeName)) {
                return true;
            }
        }
        return false;
    }

    private static PolicyRagDocument toPolicyRagDocument(Document document) {
        String policyId = readMetadataAsString(document, "policyId", document.getId());
        String policyDomain = readMetadataAsString(document, "policyDomain", "");
        String controlObjective = readMetadataAsString(document, "controlObjective", "");
        String severity = readMetadataAsString(document, "severity", "");
        List<String> triggerKeywords = PolicyKnowledgeItem.splitMulti(
                readMetadataAsString(document, "triggerKeywords", "")
        );
        List<String> evidence = PolicyKnowledgeItem.splitMulti(
                readMetadataAsString(document, "requiredEvidence", "")
        );
        String escalationRole = readMetadataAsString(document, "escalationRole", "");
        String text = document.getText() == null ? "" : document.getText();
        double score = document.getScore() == null ? 0.0 : document.getScore();
        return new PolicyRagDocument(
                policyId, policyDomain, controlObjective, severity,
                triggerKeywords, evidence, escalationRole, text, score
        );
    }

    private static String readMetadataAsString(Document document, String key, String defaultValue) {
        Object value = document.getMetadata() == null ? null : document.getMetadata().get(key);
        if (value == null) {
            return defaultValue;
        }
        String s = value.toString();
        return s.isBlank() ? defaultValue : s;
    }
}
