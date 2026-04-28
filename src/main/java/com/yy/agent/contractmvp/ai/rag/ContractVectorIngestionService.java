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
 * id={@code contract:{contractId}:{chunkId}}（再经 name-based UUID 转换），
 * content={@code 【clauseTitle】\n{textForEmbedding}}，
 * metadata 至少包含 {@code docType=contract_clause}、contractId、chunkId、clauseTitle、clauseCode、clauseCategory，
 * 以便在与制度通道共用 vector_store 时按 {@code docType} 区分来源。
 * <p>
 * 写入由 {@link VectorBatchWriter} 统一处理：按配置批大小切片（适配 DashScope 通用 embedding ≤10 条/批的限制），
 * 每批先按业务派生 id 删除再插入，保证重复导入同一合同 chunk 时幂等覆盖。
 */
@Service
@Profile("!test")
public class ContractVectorIngestionService {

    /** 合同条款向量文档 id 前缀，避免与 {@code policy:} 命名空间冲突。 */
    private static final String ID_NAMESPACE = "contract:";

    private final VectorBatchWriter vectorBatchWriter;

    public ContractVectorIngestionService(VectorBatchWriter vectorBatchWriter) {
        this.vectorBatchWriter = vectorBatchWriter;
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
        vectorBatchWriter.upsert(documents);
    }

    private static Document toDocument(ClauseChunk chunk) {
        String businessId = ID_NAMESPACE + chunk.contractId() + ":" + chunk.id();
        // PgVectorStore 默认将文档 id 作为 UUID 处理，这里用稳定的 name-based UUID 映射业务键。
        String id = UUID.nameUUIDFromBytes(businessId.getBytes(StandardCharsets.UTF_8)).toString();
        String content = "【" + chunk.clauseTitle() + "】\n" + chunk.textForEmbedding();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("docType", "contract_clause");
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
