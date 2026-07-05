package com.yy.agent.contract.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 本地重排序与多样性保留。
 * <p>
 * 先基于向量分数、关键词命中、业务领域/制度元数据计算业务相关性分数，再用简化 MMR 选择结果：
 * 在保证高相关的同时，避免前几条都来自同一条款领域或同一制度领域。
 */
@Component
class RagResultReranker {

    private static final Logger log = LoggerFactory.getLogger(RagResultReranker.class);
    private static final double MMR_LAMBDA = 0.95;

    private final RerankModelClient rerankModelClient;
    private final double modelWeight;

    @Autowired
    RagResultReranker(
            ObjectProvider<RerankModelClient> rerankModelClientProvider,
            @Value("${app.rag.rerank.model-weight:0.70}") double modelWeight
    ) {
        this(rerankModelClientProvider.getIfAvailable(), modelWeight);
    }

    RagResultReranker(RerankModelClient rerankModelClient, double modelWeight) {
        this.rerankModelClient = rerankModelClient;
        this.modelWeight = clamp(modelWeight);
    }

    List<RagDocument> rerankClauses(List<RagDocument> candidates, String query, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<Integer, Double> modelScores = modelScores(query, candidates.stream()
                .map(RagResultReranker::clauseModelText)
                .toList());
        List<Scored<RagDocument>> scored = IntStream.range(0, candidates.size())
                .mapToObj(index -> {
                    RagDocument doc = candidates.get(index);
                    double localScore = clauseScore(doc, query);
                    return new Scored<>(doc, fusedScore(localScore, modelScores.get(index)), clauseGroup(doc));
                })
                .sorted(Comparator.comparingDouble(Scored<RagDocument>::score).reversed())
                .toList();
        return selectMmr(scored, Math.max(1, topK)).stream()
                .map(s -> new RagDocument(s.item().id(), s.item().title(), s.item().text(), s.score()))
                .toList();
    }

    List<PolicyRagDocument> rerankPolicies(List<PolicyRagDocument> candidates, String query, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<Integer, Double> modelScores = modelScores(query, candidates.stream()
                .map(RagResultReranker::policyModelText)
                .toList());
        List<Scored<PolicyRagDocument>> scored = IntStream.range(0, candidates.size())
                .mapToObj(index -> {
                    PolicyRagDocument doc = candidates.get(index);
                    double localScore = policyScore(doc, query);
                    return new Scored<>(doc, fusedScore(localScore, modelScores.get(index)), policyGroup(doc));
                })
                .sorted(Comparator.comparingDouble(Scored<PolicyRagDocument>::score).reversed())
                .toList();
        return selectMmr(scored, Math.max(1, topK)).stream()
                .map(s -> {
                    PolicyRagDocument d = s.item();
                    return new PolicyRagDocument(
                            d.policyId(),
                            d.policyDomain(),
                            d.controlObjective(),
                            d.severity(),
                            d.triggerKeywords(),
                            d.requiredEvidence(),
                            d.escalationRole(),
                            d.text(),
                            s.score()
                    );
                })
                .toList();
    }

    private Map<Integer, Double> modelScores(String query, List<String> documents) {
        if (rerankModelClient == null || !rerankModelClient.isAvailable()) {
            return Map.of();
        }
        try {
            Map<Integer, Double> scores = new HashMap<>();
            for (RerankScore score : rerankModelClient.rerank(query, documents)) {
                if (score.index() >= 0 && score.index() < documents.size()) {
                    scores.put(score.index(), clamp(score.relevanceScore()));
                }
            }
            return scores;
        } catch (RuntimeException e) {
            log.warn("Rerank model call failed, fallback to local rerank: {}", e.getMessage());
            return Map.of();
        }
    }

    private double fusedScore(double localScore, Double modelScore) {
        if (modelScore == null) {
            return localScore;
        }
        return modelWeight * modelScore + (1.0 - modelWeight) * localScore;
    }

    private static <T> List<Scored<T>> selectMmr(List<Scored<T>> candidates, int topK) {
        List<Scored<T>> remaining = new ArrayList<>(dedupeByStableKey(candidates));
        List<Scored<T>> selected = new ArrayList<>();
        Map<String, Integer> groupCounts = new HashMap<>();
        while (!remaining.isEmpty() && selected.size() < topK) {
            Scored<T> best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;
            for (Scored<T> candidate : remaining) {
                double diversityPenalty = selected.stream()
                        .mapToDouble(s -> sameGroupPenalty(candidate.group(), s.group()))
                        .max()
                        .orElse(0.0);
                int sameGroupCount = groupCounts.getOrDefault(candidate.group(), 0);
                double groupCrowdingPenalty = sameGroupCount < 2 ? 0.0 : Math.min(0.08, 0.03 * (sameGroupCount - 1));
                double mmr = MMR_LAMBDA * candidate.score()
                        - (1.0 - MMR_LAMBDA) * diversityPenalty
                        - groupCrowdingPenalty;
                if (mmr > bestMmr) {
                    best = candidate;
                    bestMmr = mmr;
                }
            }
            selected.add(best);
            groupCounts.merge(best.group(), 1, Integer::sum);
            remaining.remove(best);
        }
        return selected;
    }

    private static <T> List<Scored<T>> dedupeByStableKey(List<Scored<T>> candidates) {
        Map<String, Scored<T>> deduped = new LinkedHashMap<>();
        for (Scored<T> candidate : candidates) {
            String key = stableKey(candidate.item());
            Scored<T> existing = deduped.get(key);
            if (existing == null || candidate.score() > existing.score()) {
                deduped.put(key, candidate);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private static String stableKey(Object item) {
        if (item instanceof RagDocument d) {
            return "clause:" + d.id();
        }
        if (item instanceof PolicyRagDocument d) {
            return "policy:" + d.policyId();
        }
        return item.toString();
    }

    private static double clauseScore(RagDocument doc, String query) {
        String haystack = (doc.title() + " " + doc.text()).toLowerCase();
        double score = doc.score();
        for (String term : RagQueryExpander.signalTerms(query)) {
            if (RagQueryExpander.containsIgnoreCase(haystack, term)) {
                score += 0.045;
            }
        }
        if (RagQueryExpander.containsIgnoreCase(query, doc.title())) {
            score += 0.08;
        }
        return score;
    }

    private static double policyScore(PolicyRagDocument doc, String query) {
        String haystack = String.join(" ",
                doc.policyDomain(),
                doc.controlObjective(),
                String.join(" ", doc.triggerKeywords()),
                String.join(" ", doc.requiredEvidence()),
                doc.escalationRole(),
                doc.text()
        ).toLowerCase();
        double score = doc.score();
        for (String term : RagQueryExpander.signalTerms(query)) {
            if (RagQueryExpander.containsIgnoreCase(haystack, term)) {
                score += 0.05;
            }
        }
        score += policyIdBoost(doc.policyId(), query);
        score += policyDomainBoost(doc.policyDomain(), query);
        if ("HIGH".equalsIgnoreCase(doc.severity())
                && (RagQueryExpander.containsIgnoreCase(query, "风险")
                || RagQueryExpander.containsIgnoreCase(query, "审批")
                || RagQueryExpander.containsIgnoreCase(query, "负责人"))) {
            score += 0.025;
        }
        return score;
    }

    private static double policyIdBoost(String policyId, String query) {
        if ("POL-FIN-003".equals(policyId) && containsAny(query, "账户", "账号", "收款", "代收", "个人账户")) {
            return 0.24;
        }
        if ("POL-PAY-003".equals(policyId) && containsAny(query, "里程碑", "分期", "三期", "尾款", "阶段")) {
            return 0.24;
        }
        if ("POL-ACC-001".equals(policyId) && containsAny(query, "里程碑", "验收", "对账", "服务确认", "SOW")) {
            return 0.12;
        }
        if ("POL-TAX-001".equals(policyId) && containsAny(query, "发票", "税率", "税务", "开票")) {
            return 0.16;
        }
        if ("POL-SVC-003".equals(policyId) && containsAny(query, "数据导出", "删除", "退出", "终止")) {
            return 0.18;
        }
        return 0.0;
    }

    private static double policyDomainBoost(String domain, String query) {
        if (domain == null) {
            return 0.0;
        }
        if (domain.contains("资金") && containsAny(query, "付款", "支付", "账户", "收款", "尾款", "账期")) {
            return 0.14;
        }
        if (domain.contains("税务") && containsAny(query, "税", "发票", "专票", "普票", "抵扣")) {
            return 0.14;
        }
        if (domain.contains("会计") && containsAny(query, "验收", "对账", "入账", "里程碑", "SOW")) {
            return 0.10;
        }
        if (domain.contains("采购") && containsAny(query, "交付", "验收", "质保", "迟延")) {
            return 0.11;
        }
        if (domain.contains("法务") && containsAny(query, "责任", "赔偿", "解除", "违约", "争议", "损失")) {
            return 0.12;
        }
        if (domain.contains("服务") && containsAny(query, "SLA", "数据", "安全", "分包", "驻场", "日志", "删除", "DPA")) {
            return 0.14;
        }
        return 0.0;
    }

    private static boolean containsAny(String query, String... terms) {
        for (String term : terms) {
            if (RagQueryExpander.containsIgnoreCase(query, term)) {
                return true;
            }
        }
        return false;
    }

    private static String clauseGroup(RagDocument doc) {
        String s = doc.title() + " " + doc.text();
        if (containsAny(s, "付款", "支付", "账户", "收款", "尾款", "账期")) {
            return "payment";
        }
        if (containsAny(s, "发票", "税", "专票", "价税")) {
            return "tax";
        }
        if (containsAny(s, "验收", "交付", "质保", "里程碑", "对账")) {
            return "delivery_acceptance";
        }
        if (containsAny(s, "责任", "赔偿", "损失", "违约", "知识产权")) {
            return "liability";
        }
        if (containsAny(s, "解除", "终止", "退出")) {
            return "termination";
        }
        if (containsAny(s, "数据", "安全", "保密", "日志", "权限", "DPA")) {
            return "security";
        }
        if (containsAny(s, "分包", "驻场", "外包", "人员")) {
            return "subcontract";
        }
        if (containsAny(s, "SLA", "可用性", "故障")) {
            return "sla";
        }
        return doc.title().isBlank() ? "misc" : doc.title();
    }

    private static String policyGroup(PolicyRagDocument doc) {
        return doc.policyDomain().isBlank() ? "policy_misc" : doc.policyDomain();
    }

    private static double sameGroupPenalty(String left, String right) {
        return left.equals(right) ? 0.35 : 0.0;
    }

    private static String clauseModelText(RagDocument doc) {
        if (doc.title().isBlank()) {
            return doc.text();
        }
        return doc.title() + "\n" + doc.text();
    }

    private static String policyModelText(PolicyRagDocument doc) {
        return String.join("\n",
                doc.policyId(),
                doc.policyDomain(),
                doc.controlObjective(),
                String.join(" ", doc.triggerKeywords()),
                String.join(" ", doc.requiredEvidence()),
                doc.escalationRole(),
                doc.text()
        );
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Scored<T>(T item, double score, String group) {
    }
}
