package com.yy.agent.contractmvp.ai.rag;

import com.yy.agent.contractmvp.domain.ContractType;
import com.yy.agent.contractmvp.domain.PolicyKnowledgeItem;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

    /** 候选超采系数：制度通道在 docType 过滤后再做合同类型筛选，预先多取候选避免被过滤后不足 topK。 */
    private static final int CANDIDATE_OVERSAMPLE = 5;

    private final VectorStore vectorStore;

    public PgVectorPolicyRagRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<PolicyRagDocument> retrieve(ContractType contractType, String query, int topK) {
        if (contractType == null) {
            return List.of();
        }
        int k = Math.max(1, topK);
        String normalizedQuery = (query == null || query.isBlank()) ? "合同合规 制度依据" : query.trim();

        SearchRequest request = SearchRequest.builder()
                .query(normalizedQuery)
                .topK(k * CANDIDATE_OVERSAMPLE)
                .filterExpression("docType == 'policy'")
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        String contractTypeName = contractType.displayName();
        return documents.stream()
                .filter(d -> appliesToContractType(d, contractTypeName))
                .limit(k)
                .map(PgVectorPolicyRagRetriever::toPolicyRagDocument)
                .toList();
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
        List<String> evidence = PolicyKnowledgeItem.splitMulti(
                readMetadataAsString(document, "requiredEvidence", "")
        );
        String escalationRole = readMetadataAsString(document, "escalationRole", "");
        String text = document.getText() == null ? "" : document.getText();
        double score = document.getScore() == null ? 0.0 : document.getScore();
        return new PolicyRagDocument(
                policyId, policyDomain, controlObjective, severity,
                evidence, escalationRole, text, score
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
