package com.yy.agent.contractmvp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yy.agent.contractmvp.ai.prompt.ContractPrompts;
import com.yy.agent.contractmvp.ai.rag.RagDocument;
import com.yy.agent.contractmvp.ai.rag.RagRetriever;
import com.yy.agent.contractmvp.ai.tool.ContractToolExecutor;
import com.yy.agent.contractmvp.api.dto.ApprovalAssistResponse;
import com.yy.agent.contractmvp.api.dto.ContractQaResponse;
import com.yy.agent.contractmvp.api.dto.ContractRiskCheckResponse;
import com.yy.agent.contractmvp.domain.RiskItem;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 合同场景的大模型编排入口：组合 {@link RagRetriever}、{@link ContractToolExecutor} 与 {@link ContractPrompts}，
 * 调用 {@link ChatClient} 完成问答、风险审查、审批辅助，并解析模型返回的 JSON。
 */
@Component
public class AiContractAssistant {

    /** 每次检索返回的条款块数量上限。 */
    private static final int RAG_TOP_K = 4;
    /** 拼入 Prompt 的 RAG 正文最大字符数，防止超长上下文。 */
    private static final int RAG_CONTEXT_MAX_CHARS = 12_000;

    private final ChatClient chatClient;
    private final RagRetriever ragRetriever;
    private final ContractToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    /**
     * @param chatClientBuilder 构建独立 {@link ChatClient} 实例
     * @param ragRetriever      条款检索实现（MVP 为关键词）
     * @param toolExecutor      从仓储拉取摘要与全文的工具封装
     * @param objectMapper      解析模型 JSON 输出
     */
    public AiContractAssistant(
            ChatClient.Builder chatClientBuilder,
            RagRetriever ragRetriever,
            ContractToolExecutor toolExecutor,
            ObjectMapper objectMapper
    ) {
        this.chatClient = chatClientBuilder.build();
        this.ragRetriever = ragRetriever;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 合同问答：按用户问题做 RAG，附合同摘要，返回自然语言回答及命中的条款块 id。
     *
     * @param contractId 合同 id（须已存在）
     * @param question   用户问题
     * @return 回答与检索 id列表
     */
    public ContractQaResponse answerQuestion(String contractId, String question) {
        toolExecutor.requireContract(contractId);
        List<RagDocument> docs = ragRetriever.retrieve(contractId, question, RAG_TOP_K);
        String rag = buildRagContext(docs);
        String summary = toolExecutor.contractSummary(contractId);
        String user = ContractPrompts.qaUser(summary, rag, question);
        String answer = chatClient.prompt()
                .system(ContractPrompts.qaSystem())
                .user(user)
                .call()
                .content();
        List<String> ids = docs.stream().map(RagDocument::id).toList();
        return new ContractQaResponse(answer == null ? "" : answer, ids);
    }

    /**
     * 风险检查：使用较宽的检索词覆盖财务/法务常见主题，并附带历史审批摘要，要求模型仅输出 JSON后解析为领域对象。
     *
     * @param contractId 合同 id
     * @return 总结与 {@link RiskItem} 列表；解析失败时原文落入 summary
     */
    public ContractRiskCheckResponse riskCheck(String contractId) {
        toolExecutor.requireContract(contractId);
        String broadQuery = "价格 付款 发票 税务 验收 质保 违约 责任 保密 分包 数据 驻场";
        List<RagDocument> docs = ragRetriever.retrieve(contractId, broadQuery, RAG_TOP_K);
        String rag = buildRagContext(docs);
        String summary = toolExecutor.contractSummary(contractId);
        String approvals = toolExecutor.approvalHistoryDigest(contractId);
        String user = ContractPrompts.riskCheckUser(summary, rag, approvals);
        String raw = chatClient.prompt()
                .system(ContractPrompts.riskCheckSystem())
                .user(user)
                .call()
                .content();
        return parseRiskResponse(raw);
    }

    /**
     * 审批辅助：根据审批角色与关注重点构造检索 query，输出建议与 checklist（JSON 解析）。
     *
     * @param contractId   合同 id
     * @param approverRole 当前节点审批角色描述
     * @param focus        额外关注点，可为空
     * @return 建议与核对项
     */
    public ApprovalAssistResponse approvalAssist(String contractId, String approverRole, String focus) {
        toolExecutor.requireContract(contractId);
        String q = (approverRole + " " + focus + " 审批 财务 法务 发票 验收 保密 分包").trim();
        List<RagDocument> docs = ragRetriever.retrieve(contractId, q, RAG_TOP_K);
        String rag = buildRagContext(docs);
        String summary = toolExecutor.contractSummary(contractId);
        String approvals = toolExecutor.approvalHistoryDigest(contractId);
        String user = ContractPrompts.approvalAssistUser(approverRole, focus, summary, rag, approvals);
        String raw = chatClient.prompt()
                .system(ContractPrompts.approvalAssistSystem())
                .user(user)
                .call()
                .content();
        return parseAssistResponse(raw);
    }

    /**
     * 将多条 {@link RagDocument} 格式化为带标题的正文，供插入用户 Prompt。
     */
    private String buildRagContext(List<RagDocument> docs) {
        StringBuilder sb = new StringBuilder();
        for (RagDocument d : docs) {
            sb.append("【").append(d.id()).append(" ").append(d.title()).append("】\n");
            sb.append(d.text()).append("\n\n");
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
     * 解析风险审查模型输出：期望顶层含 {@code summary} 与 {@code riskItems} 数组。
     * 失败时返回原文作为 summary、风险列表为空。
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
                items.add(new RiskItem(code, sev, detail, cids, pids));
            }
            return new ContractRiskCheckResponse(summary, items);
        } catch (Exception ignored) {
            return new ContractRiskCheckResponse(raw.trim(), List.of());
        }
    }

    /**
     * 解析审批辅助模型输出：期望 {@code suggestion} 与 {@code checklist} 字符串数组。
     */
    private ApprovalAssistResponse parseAssistResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ApprovalAssistResponse("", List.of());
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
            return new ApprovalAssistResponse(suggestion, checklist);
        } catch (Exception ignored) {
            return new ApprovalAssistResponse(raw.trim(), List.of());
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
