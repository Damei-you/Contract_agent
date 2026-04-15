package com.yy.agent.contractmvp.ai.rag;

import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.repository.ContractRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 基于简单分词 + 子串匹配的 RAG 实现：将 query 按空白与常见标点切分为 token，
 * 在条款标题与正文中统计命中次数作为 score；无命中时退回合同内前几条条款。
 */
@Component
public class KeywordRagRetriever implements RagRetriever {

    private final ContractRepository contractRepository;

    /**
     * @param contractRepository 读取某合同下全部 {@link com.yy.agent.contractmvp.domain.ClauseChunk}
     */
    public KeywordRagRetriever(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /**
     * {@inheritDoc}
     * <p>
     * query 为空时返回合同内前 {@code topK} 条条款，score 置极小正值；无任何条款时返回空列表。
     */
    @Override
    public List<RagDocument> retrieve(String contractId, String query, int topK) {
        int k = Math.max(1, topK);
        List<ClauseChunk> chunks = contractRepository.findChunksByContractId(contractId);
        if (chunks.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return chunks.stream()
                    .limit(k)
                    .map(c -> new RagDocument(c.id(), c.clauseTitle(), c.textForEmbedding(), 0.01))
                    .collect(Collectors.toList());
        }
        List<String> tokens = tokenize(query);
        List<Scored> scored = new ArrayList<>();
        for (ClauseChunk c : chunks) {
            String haystack = (c.clauseTitle() + "\n" + c.textForEmbedding()).toLowerCase(Locale.ROOT);
            double score = 0;
            for (String t : tokens) {
                if (t.isBlank()) {
                    continue;
                }
                if (haystack.contains(t.toLowerCase(Locale.ROOT))) {
                    score += 1;
                }
            }
            if (score > 0) {
                scored.add(new Scored(c, score));
            }
        }
        if (scored.isEmpty()) {
            return chunks.stream()
                    .limit(k)
                    .map(c -> new RagDocument(c.id(), c.clauseTitle(), c.textForEmbedding(), 0.01))
                    .collect(Collectors.toList());
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(k)
                .map(s -> new RagDocument(
                        s.chunk().id(),
                        s.chunk().clauseTitle(),
                        s.chunk().textForEmbedding(),
                        s.score()))
                .collect(Collectors.toList());
    }

    /** 将查询按空白与中英文标点切分为 token 列表。 */
    private static List<String> tokenize(String query) {
        return Arrays.stream(query.split("[\\s,，。；;、]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 检索排序用中间结构：条款块与其关键词得分。
     *
     * @param chunk 领域条款块
     * @param score 命中得分
     */
    private record Scored(ClauseChunk chunk, double score) {
    }
}
