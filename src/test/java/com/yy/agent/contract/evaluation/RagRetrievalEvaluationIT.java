package com.yy.agent.contract.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yy.agent.contract.ContractAgentApplication;
import com.yy.agent.contract.ai.rag.PolicyRagDocument;
import com.yy.agent.contract.ai.rag.PolicyRagRetriever;
import com.yy.agent.contract.ai.rag.RagDocument;
import com.yy.agent.contract.ai.rag.RagRetriever;
import com.yy.agent.contract.domain.ContractType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索 baseline 评测。
 * <p>
 * 该测试不会调用大模型生成最终答案，只调用当前无重排序的合同/制度 retriever，
 * 再将命中 id 与 data/evaluation/rag-qrels.jsonl 中的金标证据计算 Precision/Recall/MRR/nDCG。
 * <p>
 * 运行前需先导入 data/evaluation 下的 seed 数据，并确保 PostgreSQL/pgvector、embedding API 可用。
 * 正常单元测试不会自动执行本类；需要显式运行：
 * {@code .\mvnw.cmd -Dtest=RagRetrievalEvaluationIT test}
 */
@SpringBootTest(classes = ContractAgentApplication.class, properties = {
        "spring.main.lazy-initialization=true"
})
class RagRetrievalEvaluationIT {

    private static final String STRATEGY = System.getProperty("rag.eval.strategy", "current-retriever");
    private static final String OUTPUT_PREFIX = System.getProperty("rag.eval.output-prefix", STRATEGY);
    private static final int DEFAULT_EVAL_TOP_K = 8;
    private static final Path EVAL_DIR = Path.of("data", "evaluation");
    private static final Path OUTPUT_DIR = Path.of("target", "rag-eval");

    @Autowired
    private RagRetriever ragRetriever;

    @Autowired
    private PolicyRagRetriever policyRagRetriever;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void runBaselineVectorRetrievalEvaluation() throws IOException {
        List<EvalCase> cases = readJsonLines(EVAL_DIR.resolve("rag-eval-cases.jsonl"), EvalCase.class);
        List<Qrel> qrels = readJsonLines(EVAL_DIR.resolve("rag-qrels.jsonl"), Qrel.class);
        Map<String, String> chunkContractIds = readChunkContractIds(EVAL_DIR.resolve("seed-contracts.json"));
        List<String> seedPolicyIds = readPolicyIds(EVAL_DIR.resolve("seed-policies.json"));

        List<RunHit> hits = runBaseline(cases);
        assertThat(hits)
                .as("No retrieval hits returned. Import data/evaluation seed data before running this IT.")
                .isNotEmpty();

        Files.createDirectories(OUTPUT_DIR);
        writeCsv(OUTPUT_DIR.resolve(OUTPUT_PREFIX + "-run.csv"), hits);
        writeTrec(OUTPUT_DIR.resolve(OUTPUT_PREFIX + "-run.trec"), hits);
        writeReport(OUTPUT_DIR.resolve(OUTPUT_PREFIX + "-report.md"), cases, qrels, hits, chunkContractIds, seedPolicyIds);
    }

    private List<RunHit> runBaseline(List<EvalCase> cases) {
        List<RunHit> hits = new ArrayList<>();
        for (EvalCase c : cases) {
            int topK = Math.max(DEFAULT_EVAL_TOP_K, c.finalTopKOrDefault());
            if (shouldRunClause(c)) {
                long started = System.nanoTime();
                List<RagDocument> docs = ragRetriever.retrieve(c.contractId(), c.query(), topK);
                long latencyMs = elapsedMs(started);
                for (int i = 0; i < docs.size(); i++) {
                    RagDocument d = docs.get(i);
                    hits.add(new RunHit(
                            c.caseId(), c.scenarioOrDefault(), "clause",
                            d.id(), i + 1, d.score(), latencyMs
                    ));
                }
            }
            if (shouldRunPolicy(c)) {
                long started = System.nanoTime();
                List<PolicyRagDocument> docs = retrievePolicyDocs(c, topK);
                long latencyMs = elapsedMs(started);
                for (int i = 0; i < docs.size(); i++) {
                    PolicyRagDocument d = docs.get(i);
                    hits.add(new RunHit(
                            c.caseId(), c.scenarioOrDefault(), "policy",
                            d.policyId(), i + 1, d.score(), latencyMs
                    ));
                }
            }
        }
        return hits;
    }

    private List<PolicyRagDocument> retrievePolicyDocs(EvalCase c, int topK) {
        if (c.contractType() == null || c.contractType().isBlank()) {
            return policyRagRetriever.retrieve(c.query(), topK);
        }
        return policyRagRetriever.retrieve(ContractType.fromFlexible(c.contractType()), c.query(), topK);
    }

    private static boolean shouldRunClause(EvalCase c) {
        return c.contractId() != null
                && !c.contractId().isBlank()
                && !"policy_qa".equals(c.scenarioOrDefault());
    }

    private static boolean shouldRunPolicy(EvalCase c) {
        return Boolean.TRUE.equals(c.includePolicyEvidence()) || "policy_qa".equals(c.scenarioOrDefault());
    }

    private void writeReport(
            Path path,
            List<EvalCase> cases,
            List<Qrel> qrels,
            List<RunHit> hits,
            Map<String, String> chunkContractIds,
            List<String> seedPolicyIds
    ) throws IOException {
        Metrics clauseAt4 = computeMetrics(cases, qrels, hits, "clause", 4);
        Metrics clauseAt8 = computeMetrics(cases, qrels, hits, "clause", 8);
        Metrics policyAt4 = computeMetrics(cases, qrels, hits, "policy", 4);
        Metrics policyAt8 = computeMetrics(cases, qrels, hits, "policy", 8);
        int crossContractViolations = countCrossContractViolations(cases, hits, chunkContractIds);
        long unknownClauseHits = countUnknownHits(hits, "clause", chunkContractIds.keySet().stream().toList());
        long unknownPolicyHits = countUnknownHits(hits, "policy", seedPolicyIds);

        StringBuilder sb = new StringBuilder();
        sb.append("# RAG Retrieval Evaluation\n\n");
        sb.append("- Generated at: ").append(OffsetDateTime.now()).append("\n");
        sb.append("- Strategy: `").append(STRATEGY).append("`\n");
        sb.append("- Cases: ").append(cases.size()).append("\n");
        sb.append("- Qrels: ").append(qrels.size()).append("\n");
        sb.append("- Hits: ").append(hits.size()).append("\n");
        sb.append("- Cross-contract clause hits: ").append(crossContractViolations).append("\n\n");
        sb.append("- Unknown clause hits outside eval seed: ").append(unknownClauseHits).append("\n");
        sb.append("- Unknown policy hits outside eval seed: ").append(unknownPolicyHits).append("\n\n");

        sb.append("## Metrics\n\n");
        sb.append("| Channel | K | Cases | Precision@K | Recall@K | MRR@K | nDCG@K |\n");
        sb.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendMetricRow(sb, "clause", 4, clauseAt4);
        appendMetricRow(sb, "clause", 8, clauseAt8);
        appendMetricRow(sb, "policy", 4, policyAt4);
        appendMetricRow(sb, "policy", 8, policyAt8);

        sb.append("\n## How To Read\n\n");
        sb.append("- `Recall@K`: 金标证据中有多少出现在前 K 个检索结果里，越高说明漏召回越少。\n");
        sb.append("- `Precision@K`: 前 K 个检索结果里有多少是金标相关证据，越高说明噪声越少。\n");
        sb.append("- `MRR@K`: 第一个相关证据排得越靠前越高，适合衡量排序首位质量。\n");
        sb.append("- `nDCG@K`: 考虑相关性等级和排序位置，适合多证据问题。\n\n");

        sb.append("## Output Files\n\n");
        sb.append("- `").append(OUTPUT_PREFIX).append("-run.csv`: 每个 case 的命中明细，便于人工查看。\n");
        sb.append("- `").append(OUTPUT_PREFIX).append("-run.trec`: TREC run 格式，可交给 pytrec_eval/ranx/ir-measures。\n");
        sb.append("- `").append(OUTPUT_PREFIX).append("-report.md`: 当前报告。\n");

        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void appendMetricRow(StringBuilder sb, String channel, int k, Metrics m) {
        sb.append("| ")
                .append(channel)
                .append(" | ")
                .append(k)
                .append(" | ")
                .append(m.caseCount())
                .append(" | ")
                .append(format(m.precision()))
                .append(" | ")
                .append(format(m.recall()))
                .append(" | ")
                .append(format(m.mrr()))
                .append(" | ")
                .append(format(m.ndcg()))
                .append(" |\n");
    }

    private static Metrics computeMetrics(
            List<EvalCase> cases,
            List<Qrel> qrels,
            List<RunHit> hits,
            String channel,
            int k
    ) {
        Map<String, Map<String, Integer>> relevanceByCase = qrels.stream()
                .filter(q -> channel.equals(q.channel()))
                .collect(Collectors.groupingBy(
                        Qrel::caseId,
                        LinkedHashMap::new,
                        Collectors.toMap(Qrel::docId, Qrel::relevance, Math::max, LinkedHashMap::new)
                ));

        Map<String, List<RunHit>> hitsByCase = hits.stream()
                .filter(h -> channel.equals(h.channel()))
                .collect(Collectors.groupingBy(RunHit::caseId, LinkedHashMap::new, Collectors.toList()));

        int caseCount = 0;
        double precisionSum = 0.0;
        double recallSum = 0.0;
        double mrrSum = 0.0;
        double ndcgSum = 0.0;

        for (EvalCase c : cases) {
            Map<String, Integer> rel = relevanceByCase.get(c.caseId());
            if (rel == null || rel.isEmpty()) {
                continue;
            }
            caseCount++;
            List<RunHit> ranked = hitsByCase.getOrDefault(c.caseId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(RunHit::rank))
                    .limit(k)
                    .toList();

            int relevantHits = 0;
            double reciprocalRank = 0.0;
            double dcg = 0.0;
            for (int i = 0; i < ranked.size(); i++) {
                int rank = i + 1;
                int relevance = rel.getOrDefault(ranked.get(i).docId(), 0);
                if (relevance > 0) {
                    relevantHits++;
                    if (reciprocalRank == 0.0) {
                        reciprocalRank = 1.0 / rank;
                    }
                }
                dcg += gain(relevance) / log2(rank + 1.0);
            }

            precisionSum += relevantHits / (double) k;
            recallSum += relevantHits / (double) rel.size();
            mrrSum += reciprocalRank;
            ndcgSum += dcg / idealDcg(rel, k);
        }

        if (caseCount == 0) {
            return new Metrics(0, 0, 0, 0, 0);
        }
        return new Metrics(
                caseCount,
                precisionSum / caseCount,
                recallSum / caseCount,
                mrrSum / caseCount,
                ndcgSum / caseCount
        );
    }

    private static double idealDcg(Map<String, Integer> relevanceByDoc, int k) {
        List<Integer> ideal = relevanceByDoc.values().stream()
                .sorted(Comparator.reverseOrder())
                .limit(k)
                .toList();
        double idcg = 0.0;
        for (int i = 0; i < ideal.size(); i++) {
            idcg += gain(ideal.get(i)) / log2(i + 2.0);
        }
        return idcg == 0.0 ? 1.0 : idcg;
    }

    private static int countCrossContractViolations(
            List<EvalCase> cases,
            List<RunHit> hits,
            Map<String, String> chunkContractIds
    ) {
        Map<String, String> caseContractIds = cases.stream()
                .filter(c -> c.contractId() != null && !c.contractId().isBlank())
                .collect(Collectors.toMap(EvalCase::caseId, EvalCase::contractId));
        int violations = 0;
        for (RunHit h : hits) {
            if (!"clause".equals(h.channel())) {
                continue;
            }
            String expectedContractId = caseContractIds.get(h.caseId());
            String actualContractId = chunkContractIds.get(h.docId());
            if (expectedContractId != null && actualContractId != null && !expectedContractId.equals(actualContractId)) {
                violations++;
            }
        }
        return violations;
    }

    private static long countUnknownHits(List<RunHit> hits, String channel, List<String> knownDocIds) {
        return hits.stream()
                .filter(h -> channel.equals(h.channel()))
                .filter(h -> !knownDocIds.contains(h.docId()))
                .count();
    }

    private static double gain(int relevance) {
        return Math.pow(2.0, relevance) - 1.0;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private <T> List<T> readJsonLines(Path path, Class<T> type) throws IOException {
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> readValue(s, type))
                    .toList();
        }
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> readChunkContractIds(Path path) throws IOException {
        JsonNode root = objectMapper.readTree(path.toFile());
        Map<String, String> chunkContractIds = new HashMap<>();
        for (JsonNode contract : root.withArray("contracts")) {
            String contractId = contract.path("id").asText();
            for (JsonNode chunk : contract.withArray("chunks")) {
                chunkContractIds.put(chunk.path("id").asText(), contractId);
            }
        }
        return chunkContractIds;
    }

    private List<String> readPolicyIds(Path path) throws IOException {
        JsonNode root = objectMapper.readTree(path.toFile());
        List<String> policyIds = new ArrayList<>();
        for (JsonNode policy : root.withArray("policies")) {
            policyIds.add(policy.path("policyId").asText());
        }
        return policyIds;
    }

    private static void writeCsv(Path path, List<RunHit> hits) throws IOException {
        StringBuilder sb = new StringBuilder("caseId,scenario,channel,rank,docId,score,latencyMs,strategy\n");
        for (RunHit h : hits) {
            sb.append(csv(h.caseId())).append(',')
                    .append(csv(h.scenario())).append(',')
                    .append(csv(h.channel())).append(',')
                    .append(h.rank()).append(',')
                    .append(csv(h.docId())).append(',')
                    .append(h.score()).append(',')
                    .append(h.latencyMs()).append(',')
                    .append(csv(STRATEGY)).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeTrec(Path path, List<RunHit> hits) throws IOException {
        StringBuilder sb = new StringBuilder();
        Map<String, List<RunHit>> byCase = hits.stream()
                .collect(Collectors.groupingBy(RunHit::caseId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<RunHit>> entry : byCase.entrySet()) {
            List<RunHit> ranked = entry.getValue().stream()
                    .sorted(Comparator.comparing(RunHit::channel).thenComparingInt(RunHit::rank))
                    .toList();
            for (int i = 0; i < ranked.size(); i++) {
                RunHit h = ranked.get(i);
                sb.append(h.caseId()).append(" Q0 ")
                        .append(h.channel()).append(':').append(h.docId())
                        .append(' ').append(i + 1)
                        .append(' ').append(h.score())
                        .append(' ').append(STRATEGY)
                        .append('\n');
            }
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        String s = Objects.toString(value, "");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private record EvalCase(
            String caseId,
            String scenario,
            String contractId,
            String contractType,
            String query,
            Boolean includePolicyEvidence,
            Integer finalTopK,
            String approverRole,
            String focus,
            String notes
    ) {
        String scenarioOrDefault() {
            return scenario == null || scenario.isBlank() ? "contract_qa" : scenario;
        }

        int finalTopKOrDefault() {
            return finalTopK == null || finalTopK <= 0 ? 4 : finalTopK;
        }
    }

    private record Qrel(String caseId, String channel, String docId, int relevance) {
    }

    private record RunHit(
            String caseId,
            String scenario,
            String channel,
            String docId,
            int rank,
            double score,
            long latencyMs
    ) {
    }

    private record Metrics(
            int caseCount,
            double precision,
            double recall,
            double mrr,
            double ndcg
    ) {
    }
}
