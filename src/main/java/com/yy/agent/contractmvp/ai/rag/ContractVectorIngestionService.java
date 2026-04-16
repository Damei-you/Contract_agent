package com.yy.agent.contractmvp.ai.rag;

import com.yy.agent.contractmvp.domain.ClauseChunk;
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
 * 合同条款向量入库服务：将每个 {@link ClauseChunk} 映射为一条向量文档并写入 {@link VectorStore}。
 * <p>
 * 映射规则见 {@code PG_VECTOR_MAPPING_SPEC.md}：
 * id={contractId}:{chunkId}，content=【clauseTitle】\n{textForEmbedding}，metadata 为最小固定集合。
 */
@Service
@Profile("!test")
public class ContractVectorIngestionService {

    private final VectorStore vectorStore;

    public ContractVectorIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 将合同条款块批量写入向量库。
     *
     * @param chunks 同一合同下的条款块
     */
    public void ingest(List<ClauseChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<Document> documents = chunks.stream()
                .map(ContractVectorIngestionService::toDocument)
                .toList();
        vectorStore.add(documents);
    }

    private static Document toDocument(ClauseChunk chunk) {
        String businessId = chunk.contractId() + ":" + chunk.id();
        // PgVectorStore 默认将文档 id 作为 UUID 处理，这里用稳定的 name-based UUID 映射业务键。
        String id = UUID.nameUUIDFromBytes(businessId.getBytes(StandardCharsets.UTF_8)).toString();
        String content = "【" + chunk.clauseTitle() + "】\n" + chunk.textForEmbedding();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contractId", chunk.contractId());
        metadata.put("chunkId", chunk.id());
        metadata.put("clauseTitle", chunk.clauseTitle());
        metadata.put("clauseCode", chunk.clauseCode());
        metadata.put("clauseCategory", chunk.clauseCategory());

        return Document.builder()
                .id(id)
                .text(content)
                .metadata(metadata)
                .build();
    }
}
