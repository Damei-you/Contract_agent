package com.yy.agent.contract.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagResultRerankerTest {

    @Test
    void rerankModelScoreCanPromoteCandidate() {
        RerankModelClient client = new StaticRerankModelClient(List.of(
                new RerankScore(0, 0.05),
                new RerankScore(1, 0.98)
        ));
        RagResultReranker reranker = new RagResultReranker(client, 0.90);

        List<RagDocument> result = reranker.rerankClauses(List.of(
                new RagDocument("pay-weak", "付款", "主题相近但不回答问题。", 0.90),
                new RagDocument("pay-answer", "验收付款", "付款前需要提交验收证明、交付清单和合规发票。", 0.10)
        ), "付款前需要满足什么条件", 1);

        assertThat(result).extracting(RagDocument::id).containsExactly("pay-answer");
    }

    @Test
    void fallbackToLocalRerankWhenModelUnavailable() {
        RagResultReranker reranker = new RagResultReranker(new StaticRerankModelClient(List.of(), false), 0.90);

        List<RagDocument> result = reranker.rerankClauses(List.of(
                new RagDocument("local-first", "付款", "付款前提交材料。", 0.90),
                new RagDocument("local-second", "其他", "争议解决。", 0.10)
        ), "付款前需要满足什么条件", 1);

        assertThat(result).extracting(RagDocument::id).containsExactly("local-first");
    }

    private record StaticRerankModelClient(List<RerankScore> scores, boolean available) implements RerankModelClient {

        private StaticRerankModelClient(List<RerankScore> scores) {
            this(scores, true);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public List<RerankScore> rerank(String query, List<String> documents) {
            return scores;
        }
    }
}
