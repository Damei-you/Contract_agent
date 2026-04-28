package com.yy.agent.contractmvp.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用向量库批量写入工具：所有 ingestion service 都通过它把文档写入 {@link VectorStore}，
 * 统一处理「分批 + 先 delete 再 add」两个关键约束。
 *
 * <h3>为什么需要应用级分批</h3>
 * 上游 embedding 服务（如 DashScope 通用 text-embedding 系列）对单次请求的输入条数有硬限制
 * （如 {@code input.contents.size <= 10}），超过会返回 HTTP 400 {@code InvalidParameter}。
 * Spring AI 的 {@code VectorStore.add} 默认会一次性把整批文档送进 embedding 调用，
 * 因此应用层必须自己切片，以满足模型批次上限。
 *
 * <h3>幂等写入</h3>
 * pgvector 主键冲突会让单次 {@code INSERT} 整批回滚，因此每批先按业务派生 id 调
 * {@link VectorStore#delete(List)}（不存在的 id 静默忽略），再 {@link VectorStore#add(List)}。
 * 这样重复导入相同 id 会稳定覆盖，不会因为已有记录把整批写崩。
 *
 * <h3>故障语义</h3>
 * 任意一个批次抛异常都会向上传播，已成功的前序批次保留在向量库；调用方应捕获异常并提供软降级
 * （如 {@code PolicyKnowledgeApplicationService#ingestVectorsSafely}）。客户端基于相同请求体重试时，
 * 已成功批次会被 delete + add 覆盖，失败批次重新尝试，整体仍然可达成最终一致。
 */
@Component
@Profile("!test")
public class VectorBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(VectorBatchWriter.class);

    private final VectorStore vectorStore;
    private final int batchSize;

    /**
     * @param vectorStore 底层向量库
     * @param batchSize   单次 embedding/写入的最大文档数；DashScope 通用 embedding 系列上限为 10，
     *                    OpenAI text-embedding-3 系列可达 2048，需根据实际模型设置
     */
    public VectorBatchWriter(
            VectorStore vectorStore,
            @Value("${app.embedding.batch-size:10}") int batchSize
    ) {
        this.vectorStore = vectorStore;
        // 兜底：配置 0/负值会陷入死循环或单次过大；至少 1 条一批
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * 按配置 {@link #batchSize} 切分文档列表，逐批先 delete 再 add，达到「分批 + 幂等」效果。
     * 任意批次失败立即抛出，已成功批次保留以便重试覆盖。
     *
     * @param documents 待写入向量库的文档列表
     */
    public void upsert(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        int total = documents.size();
        for (int from = 0; from < total; from += batchSize) {
            int to = Math.min(from + batchSize, total);
            List<Document> batch = documents.subList(from, to);
            List<String> batchIds = batch.stream().map(Document::getId).toList();
            deleteIgnoringMissing(batchIds, from, to);
            vectorStore.add(batch);
            log.debug("Vector ingestion batch [{}, {}) of {} succeeded.", from, to, total);
        }
    }

    /**
     * 删除指定 id 集合，不存在的 id 由底层 SQL 静默忽略；少数 {@link VectorStore} 实现在
     * id 完全不存在时仍会抛异常，这里只记录 warn，不阻塞后续 {@code add}（add 真正失败才会向上抛出）。
     */
    private void deleteIgnoringMissing(List<String> ids, int from, int to) {
        try {
            vectorStore.delete(ids);
        } catch (Exception ex) {
            log.warn("vectorStore.delete failed for batch [{}, {}): {}", from, to, ex.toString());
        }
    }
}
