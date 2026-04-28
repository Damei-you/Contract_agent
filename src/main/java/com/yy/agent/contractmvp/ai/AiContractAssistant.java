package com.yy.agent.contractmvp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yy.agent.contractmvp.ai.prompt.ContractPrompts;
import com.yy.agent.contractmvp.ai.rag.PolicyRagDocument;
import com.yy.agent.contractmvp.ai.rag.PolicyRagRetriever;
import com.yy.agent.contractmvp.ai.rag.RagDocument;
import com.yy.agent.contractmvp.ai.rag.RagRetriever;
import com.yy.agent.contractmvp.ai.tool.ContractToolExecutor;
import com.yy.agent.contractmvp.api.dto.ApprovalAssistResponse;
import com.yy.agent.contractmvp.api.dto.ContractQaResponse;
import com.yy.agent.contractmvp.api.dto.ContractRiskCheckResponse;
import com.yy.agent.contractmvp.domain.Contract;
import com.yy.agent.contractmvp.domain.ContractType;
import com.yy.agent.contractmvp.domain.RiskItem;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 合同场景的大模型编排入口：组合 {@link RagRetriever}（合同条款通道）、
 * {@link PolicyRagRetriever}（政策/制度通道）、{@link ContractToolExecutor} 与 {@link ContractPrompts}，
 * 调用 {@link ChatClient} 完成问答、风险审查、审批辅助，并解析模型返回的 JSON。
 * <p>
 * RAG 通道通过 {@link ObjectProvider} 软依赖注入：测试 profile 下可不装配 PgVector 检索实现，
 * 运行时检索不到命中则仅使用合同摘要与审批历史组织提示词。
 */
@Component
public class AiContractAssistant {

    /** 每次检索返回的合同条款数量上限。 */
    private static final int RAG_TOP_K = 4;
    /** 每次检索返回的政策/制度条目数量上限。 */
    private static final int POLICY_TOP_K = 4;
    /** 拼入 Prompt 的单通道正文最大字符数，防止超长上下文。 */
    private static final int RAG_CONTEXT_MAX_CHARS = 12_000;

    private final ChatClient chatClient;
    private final ObjectProvider<RagRetriever> ragRetriever;
    private final ObjectProvider<PolicyRagRetriever> policyRagRetriever;
    private final ContractToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    /**
     * @param chatClientBuilder 构建独立 {@link ChatClient} 实例
     * @param ragRetriever      合同条款检索实现，软依赖（test profile 下可缺省）
     * @param policyRagRetriever 政策/制度条目检索实现，软依赖（test profile 下可缺省）
     * @param toolExecutor      从仓储拉取摘要与全文的工具封装
     * @param objectMapper      解析模型 JSON 输出
     */
    public AiContractAssistant(
            ChatClient.Builder chatClientBuilder,
            ObjectProvider<RagRetriever> ragRetriever,
            ObjectProvider<PolicyRagRetriever> policyRagRetriever,
            ContractToolExecutor toolExecutor,
            ObjectMapper objectMapper
    ) {
        this.chatClient = chatClientBuilder.build();
        this.ragRetriever = ragRetriever;
        this.policyRagRetriever = policyRagRetriever;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 合同问答：双通道 RAG（合同条款 + 制度依据），返回模型回答与两通道命中 id 列表。
     *
     * @param contractId 合同 id（须已存在）
     * @param question   用户问题
     * @return 回答与两通道命中 id 列表
     */
    public ContractQaResponse answerQuestion(String contractId, String question) {
        Contract contract = toolExecutor.requireContract(contractId);
        List<RagDocument> clauseDocs = retrieveClauses(contractId, question, RAG_TOP_K);
        List<PolicyRagDocument> policyDocs = retrievePolicies(contract.type(), question, POLICY_TOP_K);
        String clauseContext = buildClauseContext(clauseDocs);
        String policyContext = buildPolicyContext(policyDocs);
        String summary = toolExecutor.contractSummary(contractId);
        String user = ContractPrompts.qaUser(summary, clauseContext, policyContext, question);
        String answer = chatClient.prompt()
                .system(ContractPrompts.qaSystem())
                .user(user)
                .call()
                .content();
        return new ContractQaResponse(
                answer == null ? "" : answer,
                clauseDocs.stream().map(RagDocument::id).toList(),
                policyDocs.stream().map(PolicyRagDocument::policyId).toList()
        );
    }

    /**
     * 风险检查：双通道 RAG + 历史审批摘要，要求模型只输出 JSON 后解析为领域对象，并保留制度依据派生字段。
     *
     * @param contractId 合同 id
     * @return 总结与 {@link RiskItem} 列表；解析失败时原文落入 summary
     */
    public ContractRiskCheckResponse riskCheck(String contractId) {
        Contract contract = toolExecutor.requireContract(contractId);
        String broadQuery = "价格 付款 发票 税务 验收 质保 违约 责任 保密 分包 数据 驻场";
        List<RagDocument> clauseDocs = retrieveClauses(contractId, broadQuery, RAG_TOP_K);
        List<PolicyRagDocument> policyDocs = retrievePolicies(contract.type(), broadQuery, POLICY_TOP_K);
        String clauseContext = buildClauseContext(clauseDocs);
        String policyContext = buildPolicyContext(policyDocs);
        String summary = toolExecutor.contractSummary(contractId);
        String approvals = toolExecutor.approvalHistoryDigest(contractId);
        String user = ContractPrompts.riskCheckUser(summary, clauseContext, policyContext, approvals);
        String raw = chatClient.prompt()
                .system(ContractPrompts.riskCheckSystem())
                .user(user)
                .call()
                .content();
        return parseRiskResponse(raw);
    }

    /**
     * 审批辅助：双通道 RAG，输出建议与 checklist；命中通道 id 列表回填给前端便于溯源。
     *
     * @param contractId   合同 id
     * @param approverRole 当前节点审批角色描述
     * @param focus        额外关注点，可为空
     * @return 建议与核对项及命中来源
     */
    public ApprovalAssistResponse approvalAssist(String contractId, String approverRole, String focus) {
        Contract contract = toolExecutor.requireContract(contractId);
        String q = (approverRole + " " + focus + " 审批 财务 法务 发票 验收 保密 分包").trim();
        List<RagDocument> clauseDocs = retrieveClauses(contractId, q, RAG_TOP_K);
        List<PolicyRagDocument> policyDocs = retrievePolicies(contract.type(), q, POLICY_TOP_K);
        String clauseContext = buildClauseContext(clauseDocs);
        String policyContext = buildPolicyContext(policyDocs);
        String summary = toolExecutor.contractSummary(contractId);
        String approvals = toolExecutor.approvalHistoryDigest(contractId);
        String user = ContractPrompts.approvalAssistUser(
                approverRole, focus, summary, clauseContext, policyContext, approvals
        );
        String raw = chatClient.prompt()
                .system(ContractPrompts.approvalAssistSystem())
                .user(user)
                .call()
                .content();
        return parseAssistResponse(
                raw,
                clauseDocs.stream().map(RagDocument::id).toList(),
                policyDocs.stream().map(PolicyRagDocument::policyId).toList()
        );
    }

    /**
     * 合同条款通道软调用：未配置 {@link RagRetriever} 时返回空列表，保持测试上下文轻量。
     */
    private List<RagDocument> retrieveClauses(String contractId, String query, int topK) {
        RagRetriever retriever = ragRetriever.getIfAvailable();
        if (retriever == null) {
            return List.of();
        }
        List<RagDocument> docs = retriever.retrieve(contractId, query, topK);
        return docs == null ? List.of() : docs;
    }

    /**
     * 制度通道软调用：未配置 {@link PolicyRagRetriever} 时返回空列表。
     */
    private List<PolicyRagDocument> retrievePolicies(ContractType type, String query, int topK) {
        PolicyRagRetriever retriever = policyRagRetriever.getIfAvailable();
        if (retriever == null) {
            return List.of();
        }
        List<PolicyRagDocument> docs = retriever.retrieve(type, query, topK);
        return docs == null ? List.of() : docs;
    }

    /**
     * 将合同条款命中格式化为带标题的正文，供插入用户 Prompt。
     */
    private String buildClauseContext(List<RagDocument> docs) {
        StringBuilder sb = new StringBuilder();
        for (RagDocument d : docs) {
            sb.append("【").append(d.id()).append(" ").append(d.title()).append("】\n");
            sb.append(d.text()).append("\n\n");
        }
        return truncate(sb.toString(), RAG_CONTEXT_MAX_CHARS);
    }

    /**
     * 将制度命中格式化为带依据元数据的正文，便于模型直接复用 policyId、severity 与 escalationRole。
     */
    private String buildPolicyContext(List<PolicyRagDocument> docs) {
        StringBuilder sb = new StringBuilder();
        for (PolicyRagDocument d : docs) {
            sb.append("【").append(d.policyId());
            if (!d.policyDomain().isBlank()) {
                sb.append(" ").append(d.policyDomain());
            }
            if (!d.controlObjective().isBlank()) {
                sb.append(" / ").append(d.controlObjective());
            }
            if (!d.severity().isBlank()) {
                sb.append("（severity=").append(d.severity()).append("）");
            }
            sb.append("】\n");
            sb.append(d.text()).append("\n");
            if (!d.requiredEvidence().isEmpty()) {
                sb.append("requiredEvidence=").append(d.requiredEvidence()).append("\n");
            }
            if (!d.escalationRole().isBlank()) {
                sb.append("escalationRole=").append(d.escalationRole()).append("\n");
            }
            sb.append("\n");
        }
        return truncate(sb.toString(), RAG_CONTEXT_MAX_CHARS);
    }

    /** 按最大长度截断并追加省略标记。 */
    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n...[truncated]";
    }

    /**
     * 解析风险审查模型输出：期望顶层含 {@code summary} 与 {@code riskItems} 数组，每项包含
     * {@code requiredEvidence}/{@code escalationRole}。失败时返回原文作为 summary、风险列表为空。
     */
    private ContractRiskCheckResponse parseRiskResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ContractRiskCheckResponse("", List.of());
        }
        try {
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            String summary = root.path("summary").asText("");
            List<RiskItem> items = new ArrayList<>();
            for (JsonNode n : root.withArray("riskItems")) {
                String code = n.path("code").asText("UNKNOWN");
                RiskSeverity sev = parseSeverity(n.path("severity").asText("MEDIUM"));
                String detail = n.path("detail").asText("");
                List<String> cids = readStringArray(n, "relatedClauseChunkIds");
                List<String> pids = readStringArray(n, "relatedPolicyIds");
                List<String> evidence = readStringArray(n, "requiredEvidence");
                String escalationRole = n.path("escalationRole").asText("");
                items.add(new RiskItem(code, sev, detail, cids, pids, evidence, escalationRole));
            }
            return new ContractRiskCheckResponse(summary, items);
        } catch (Exception ignored) {
            return new ContractRiskCheckResponse(raw.trim(), List.of());
        }
    }

    /**
     * 解析审批辅助模型输出：期望 {@code suggestion} 与 {@code checklist} 字符串数组。
     * 命中通道 id 列表由调用方保留，独立于模型输出，便于失败回退。
     */
    private ApprovalAssistResponse parseAssistResponse(
            String raw,
            List<String> retrievedChunkIds,
            List<String> retrievedPolicyIds
    ) {
        if (raw == null || raw.isBlank()) {
            return new ApprovalAssistResponse("", List.of(), retrievedChunkIds, retrievedPolicyIds);
        }
        try {
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            String suggestion = root.path("suggestion").asText("");
            List<String> checklist = new ArrayList<>();
            for (JsonNode n : root.withArray("checklist")) {
                if (n.isTextual()) {
                    checklist.add(n.asText());
                }
            }
            return new ApprovalAssistResponse(suggestion, checklist, retrievedChunkIds, retrievedPolicyIds);
        } catch (Exception ignored) {
            return new ApprovalAssistResponse(raw.trim(), List.of(), retrievedChunkIds, retrievedPolicyIds);
        }
    }

    /**
     * 从模型回复中提取第一个完整 JSON 对象子串（处理前后夹杂说明文字的情况）。
     */
    private static String extractJsonObject(String raw) {
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    /** 将模型输出的严重度字符串转为枚举，支持枚举名与中文。 */
    private static RiskSeverity parseSeverity(String s) {
        if (s == null || s.isBlank()) {
            return RiskSeverity.MEDIUM;
        }
        try {
            return RiskSeverity.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return RiskSeverity.fromDisplayName(s.trim());
        }
    }

    /** 读取 JSON 对象中字符串数组字段，忽略非文本节点。 */
    private static List<String> readStringArray(JsonNode n, String field) {
        JsonNode arr = n.path(field);
        if (!arr.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode x : arr) {
            if (x.isTextual()) {
                out.add(x.asText());
            }
        }
        return out;
    }
}
