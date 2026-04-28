package com.yy.agent.contractmvp.ai.rag;

import com.yy.agent.contractmvp.domain.PolicyKnowledgeItem;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 政策/制度向量入库服务：将每个 {@link PolicyKnowledgeItem} 映射为一条向量文档并写入 {@link VectorStore}。
 * <p>
 * 映射规则见 {@code PG_VECTOR_MAPPING_SPEC.md}：
 * id={@code policy:{policyId}}（再经 name-based UUID 转换），
 * content={@code 【policyDomain/controlObjective】\n{policyTextForEmbedding}}，
 * metadata 至少包含 {@code docType=policy} 与 policyId、policyDomain、appliesToContractType、severity、
 * triggerKeywords、requiredEvidence、escalationRole。
 * <p>
 * 写入由 {@link VectorBatchWriter} 统一处理：按配置批大小切片（适配 DashScope 通用 embedding ≤10 条/批的限制），
 * 每批先按业务派生 id 删除再插入，避免主键冲突让整批回滚，并保证多次导入相同 {@code policyId} 时稳定覆盖。
 */
@Service
@Profile("!test")
public class PolicyVectorIngestionService {

    /** 用于稳定生成向量库 UUID 的命名空间，避免与合同条款 namespace 冲突。 */
    private static final String ID_NAMESPACE = "policy:";

    private final VectorBatchWriter vectorBatchWriter;

    public PolicyVectorIngestionService(VectorBatchWriter vectorBatchWriter) {
        this.vectorBatchWriter = vectorBatchWriter;
    }

    /**
     * 将制度条目批量写入向量库。
     *
     * @param items 制度条目列表
     */
    public void ingest(List<PolicyKnowledgeItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Document> documents = items.stream()
                .map(PolicyVectorIngestionService::toDocument)
                .toList();
        vectorBatchWriter.upsert(documents);
    }

    private static Document toDocument(PolicyKnowledgeItem item) {
        String businessId = ID_NAMESPACE + item.policyId();
        // PgVectorStore 默认将文档 id 作为 UUID 处理，这里用稳定的 name-based UUID 映射业务键。
        String id = UUID.nameUUIDFromBytes(businessId.getBytes(StandardCharsets.UTF_8)).toString();
        String header = item.policyDomain();
        if (item.controlObjective() != null && !item.controlObjective().isBlank()) {
            header = header + "/" + item.controlObjective();
        }
        String content = "【" + header + "】\n" + item.policyTextForEmbedding();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("docType", "policy");
        metadata.put("policyId", item.policyId());
        metadata.put("policyDomain", item.policyDomain());
        metadata.put("appliesToContractType", item.appliesToContractType());
        metadata.put("severity", item.severity().name());
        metadata.put("triggerKeywords", item.triggerKeywords());
        metadata.put("controlObjective", item.controlObjective());
        metadata.put("requiredEvidence", item.requiredEvidence());
        metadata.put("escalationRole", item.escalationRole());

        return Document.builder()
                .id(id)
                .text(content)
                .metadata(metadata)
                .build();
    }
}
