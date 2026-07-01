package com.yy.agent.contractmvp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yy.agent.contractmvp.ai.agent.AgentContext;
import com.yy.agent.contractmvp.ai.agent.AgentResult;
import com.yy.agent.contractmvp.ai.agent.AgentTrace;
import com.yy.agent.contractmvp.ai.agent.MultiAgentOrchestrator;
import com.yy.agent.contractmvp.ai.prompt.ContractPrompts;
import com.yy.agent.contractmvp.ai.rag.PolicyRagDocument;
import com.yy.agent.contractmvp.ai.rag.PolicyRagRetriever;
import com.yy.agent.contractmvp.ai.rag.RagDocument;
import com.yy.agent.contractmvp.ai.rag.RagRetriever;
import com.yy.agent.contractmvp.ai.tool.ContractToolExecutor;
import com.yy.agent.contractmvp.api.dto.ApprovalAssistResponse;
import com.yy.agent.contractmvp.api.dto.ContractQaResponse;
import com.yy.agent.contractmvp.api.dto.ContractRiskCheckResponse;
import com.yy.agent.contractmvp.api.dto.PolicyQaResponse;
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
    private final MultiAgentOrchestrator orchestrator;

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
            ObjectMapper objectMapper,
            MultiAgentOrchestrator orchestrator
    ) {
        this.chatClient = chatClientBuilder.build();
        this.ragRetriever = ragRetriever;
        this.policyRagRetriever = policyRagRetriever;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
    }

    /**
     * 合同问答：默认只检索合同条款；用户显式开启时额外检索制度依据。
     *
     * @param contractId              合同 id（须已存在）
     * @param question                用户问题
     * @param includePolicyEvidence   是否同时引用制度依据
     * @return 回答与命中 id 列表
     */
    public ContractQaResponse answerQuestion(String contractId, String question, boolean includePolicyEvidence) {
        Contract contract = toolExecutor.requireContract(contractId);
        List<RagDocument> clauseDocs = retrieveClauses(contractId, question, RAG_TOP_K);
        List<PolicyRagDocument> policyDocs = includePolicyEvidence
                ? retrievePolicies(contract.type(), question, POLICY_TOP_K)
                : List.of();
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
                policyDocs.stream().map(PolicyRagDocument::policyId).toList(),
                buildQaTrace(clauseDocs, policyDocs, includePolicyEvidence)
        );
    }

    private static List<AgentTrace> buildQaTrace(
            List<RagDocument> clauseDocs,
            List<PolicyRagDocument> policyDocs,
            boolean includePolicyEvidence
    ) {
        List<AgentTrace> traces = new ArrayList<>();
        traces.add(new AgentTrace(
                "ContractFactAgent",
                "已按问题检索合同条款，命中 %d 个合同条款片段。".formatted(clauseDocs.size()),
                clauseDocs.stream().map(RagDocument::id).toList(),
                List.of()
        ));
        if (includePolicyEvidence) {
            traces.add(new AgentTrace(
                    "PolicyEvidenceAgent",
                    "已按问题和合同类型检索制度依据，命中 %d 条制度依据。".formatted(policyDocs.size()),
                    List.of(),
                    policyDocs.stream().map(PolicyRagDocument::policyId).toList()
            ));
        }
        return traces;
    }

    /**
     * 政策/制度问答：只基于制度知识库 RAG 作答，可选按合同类型收敛适用范围。
     *
     * @param question         用户问题
     * @param contractTypeText 可选合同类型文本
     * @return 回答与命中的制度依据 id 列表
     */
    public PolicyQaResponse answerPolicyQuestion(String question, String contractTypeText) {
        ContractType contractType = parseOptionalContractType(contractTypeText);
        List<PolicyRagDocument> policyDocs = contractType == null
                ? retrievePolicies(question, POLICY_TOP_K)
                : retrievePolicies(contractType, question, POLICY_TOP_K);
        List<String> policyIds = policyDocs.stream().map(PolicyRagDocument::policyId).toList();
        String scope = contractType == null ? "不限" : contractType.displayName();
        String user = ContractPrompts.policyQaUser(scope, buildPolicyContext(policyDocs), question);
        String answer = chatClient.prompt()
                .system(ContractPrompts.policyQaSystem())
                .user(user)
                .call()
                .content();
        return new PolicyQaResponse(
                answer == null ? "" : answer,
                policyIds,
                List.of(new AgentTrace(
                        "PolicyEvidenceAgent",
                        "已按合同类型范围「%s」检索制度依据，命中 %d 条。".formatted(scope, policyDocs.size()),
                        List.of(),
                        policyIds
                ))
        );
    }

    /**
     * 风险检查：双通道 RAG + 历史审批摘要，要求模型只输出 JSON 后解析为领域对象，并保留制度依据派生字段。
     *
     * @param contractId 合同 id
     * @return 总结与 {@link RiskItem} 列表；解析失败时原文落入 summary
     */
    public ContractRiskCheckResponse riskCheck(String contractId) {
        AgentContext context = new AgentContext("risk-check", contractId);
        String broadQuery = "价格 付款 发票 税务 验收 质保 违约 责任 保密 分包 数据 驻场";
        ContractEvidence contractEvidence = orchestrator.run(
                context,
                this::contractFactAgent,
                new ContractFactRequest(contractId, broadQuery, true)
        );
        PolicyEvidence policyEvidence = orchestrator.run(
                context,
                this::policyEvidenceAgent,
                new PolicyEvidenceRequest(contractEvidence.contract().type(), broadQuery)
        );
        ContractRiskCheckResponse response = orchestrator.run(
                context,
                this::riskReviewAgent,
                new RiskReviewRequest(contractEvidence, policyEvidence)
        );
        return new ContractRiskCheckResponse(response.summary(), response.riskItems(), context.traces());
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
        AgentContext context = new AgentContext("approval-assist", contractId);
        String q = (approverRole + " " + focus + " 审批 财务 法务 发票 验收 保密 分包").trim();
        ContractEvidence contractEvidence = orchestrator.run(
                context,
                this::contractFactAgent,
                new ContractFactRequest(contractId, q, true)
        );
        PolicyEvidence policyEvidence = orchestrator.run(
                context,
                this::policyEvidenceAgent,
                new PolicyEvidenceRequest(contractEvidence.contract().type(), q)
        );
        ApprovalAssistResponse response = orchestrator.run(
                context,
                this::approvalAdviceAgent,
                new ApprovalAdviceRequest(approverRole, focus, contractEvidence, policyEvidence)
        );
        return new ApprovalAssistResponse(
                response.suggestion(),
                response.checklist(),
                response.retrievedChunkIds(),
                response.retrievedPolicyIds(),
                context.traces()
        );
    }

    /**
     * 合同事实 Agent：负责读取合同主数据、合同条款 RAG 命中和可选审批历史。
     * <p>
     * 该 Agent 只整理“当前合同事实”，不引入制度判断，避免把制度要求误写成合同已约定内容。
     */
    private AgentResult<ContractEvidence> contractFactAgent(AgentContext context, ContractFactRequest request) {
        Contract contract = toolExecutor.requireContract(request.contractId());
        List<RagDocument> clauseDocs = retrieveClauses(request.contractId(), request.query(), RAG_TOP_K);
        String summary = toolExecutor.contractSummary(request.contractId());
        String approvals = request.includeApprovalHistory()
                ? toolExecutor.approvalHistoryDigest(request.contractId())
                : "";
        ContractEvidence output = new ContractEvidence(
                contract,
                clauseDocs,
                summary,
                buildClauseContext(clauseDocs),
                approvals
        );
        String trace = "已加载合同事实，并命中 %d 个合同条款片段。".formatted(clauseDocs.size());
        return AgentResult.of(
                output,
                "ContractFactAgent",
                trace,
                clauseDocs.stream().map(RagDocument::id).toList(),
                List.of()
        );
    }

    /**
     * 制度依据 Agent：按合同类型和查询语义召回适用制度。
     * <p>
     * 该 Agent 只提供制度证据，不直接生成风险结论；风险判断由下游审查 Agent 完成。
     */
    private AgentResult<PolicyEvidence> policyEvidenceAgent(AgentContext context, PolicyEvidenceRequest request) {
        List<PolicyRagDocument> policyDocs = retrievePolicies(request.contractType(), request.query(), POLICY_TOP_K);
        PolicyEvidence output = new PolicyEvidence(policyDocs, buildPolicyContext(policyDocs));
        String trace = "已按合同类型「%s」命中 %d 条制度依据。".formatted(
                request.contractType().displayName(),
                policyDocs.size()
        );
        return AgentResult.of(
                output,
                "PolicyEvidenceAgent",
                trace,
                List.of(),
                policyDocs.stream().map(PolicyRagDocument::policyId).toList()
        );
    }

    /**
     * 风险审查 Agent：基于合同事实、制度依据和审批历史生成结构化风险项。
     * <p>
     * 上游 Agent 已经区分合同事实与制度依据，本 Agent 只负责把二者进行对照审查并解析模型 JSON。
     */
    private AgentResult<ContractRiskCheckResponse> riskReviewAgent(
            AgentContext context,
            RiskReviewRequest request
    ) {
        String user = ContractPrompts.riskCheckUser(
                request.contractEvidence().summary(),
                request.contractEvidence().clauseContext(),
                request.policyEvidence().policyContext(),
                request.contractEvidence().approvalHistory()
        );
        String raw = chatClient.prompt()
                .system(ContractPrompts.riskCheckSystem())
                .user(user)
                .call()
                .content();
        ContractRiskCheckResponse response = parseRiskResponse(raw);
        String trace = "已生成 %d 个结构化风险项。".formatted(response.riskItems().size());
        return AgentResult.of(response, "RiskReviewAgent", trace);
    }

    /**
     * 审批建议 Agent：面向当前审批角色生成建议结论和核对清单。
     * <p>
     * 输出中保留合同条款 id 与制度 id，便于前端后续展示溯源证据。
     */
    private AgentResult<ApprovalAssistResponse> approvalAdviceAgent(
            AgentContext context,
            ApprovalAdviceRequest request
    ) {
        String user = ContractPrompts.approvalAssistUser(
                request.approverRole(),
                request.focus(),
                request.contractEvidence().summary(),
                request.contractEvidence().clauseContext(),
                request.policyEvidence().policyContext(),
                request.contractEvidence().approvalHistory()
        );
        String raw = chatClient.prompt()
                .system(ContractPrompts.approvalAssistSystem())
                .user(user)
                .call()
                .content();
        ApprovalAssistResponse response = parseAssistResponse(
                raw,
                request.contractEvidence().clauseDocs().stream().map(RagDocument::id).toList(),
                request.policyEvidence().policyDocs().stream().map(PolicyRagDocument::policyId).toList()
        );
        String trace = "已生成审批建议，并产出 %d 个核对项。".formatted(response.checklist().size());
        return AgentResult.of(response, "ApprovalAdviceAgent", trace);
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
     * 制度问答通道软调用：不限定合同类型，面向全局制度知识库检索。
     */
    private List<PolicyRagDocument> retrievePolicies(String query, int topK) {
        PolicyRagRetriever retriever = policyRagRetriever.getIfAvailable();
        if (retriever == null) {
            return List.of();
        }
        List<PolicyRagDocument> docs = retriever.retrieve(query, topK);
        return docs == null ? List.of() : docs;
    }

    private static ContractType parseOptionalContractType(String contractTypeText) {
        if (contractTypeText == null || contractTypeText.isBlank()) {
            return null;
        }
        try {
            return ContractType.fromFlexible(contractTypeText);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported contractType: " + contractTypeText, e);
        }
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
     * 尝试将 LLM 原始输出直接解析为 {@link ContractRiskCheckResponse}，失败时返回原文作为 summary、风险列表为空。
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

    /**
     * 合同事实 Agent 输入。
     *
     * @param contractId              合同 id
     * @param query                   用于条款检索的查询文本
     * @param includeApprovalHistory  是否附带审批历史摘要
     */
    private record ContractFactRequest(String contractId, String query, boolean includeApprovalHistory) {
    }

    /**
     * 制度依据 Agent 输入。
     *
     * @param contractType 合同类型，用于收敛制度适用范围
     * @param query        用于制度检索的查询文本
     */
    private record PolicyEvidenceRequest(ContractType contractType, String query) {
    }

    /**
     * 风险审查 Agent 输入：由上游事实 Agent 和制度 Agent 的输出组成。
     */
    private record RiskReviewRequest(ContractEvidence contractEvidence, PolicyEvidence policyEvidence) {
    }

    /**
     * 审批建议 Agent 输入：审批角色信息加上合同事实和制度依据。
     */
    private record ApprovalAdviceRequest(
            String approverRole,
            String focus,
            ContractEvidence contractEvidence,
            PolicyEvidence policyEvidence
    ) {
    }

    /**
     * 合同事实 Agent 输出。
     *
     * @param contract        合同领域对象
     * @param clauseDocs      条款检索命中
     * @param summary         合同主数据摘要
     * @param clauseContext   可拼入 Prompt 的条款上下文
     * @param approvalHistory 审批历史摘要，可为空
     */
    private record ContractEvidence(
            Contract contract,
            List<RagDocument> clauseDocs,
            String summary,
            String clauseContext,
            String approvalHistory
    ) {
    }

    /**
     * 制度依据 Agent 输出。
     *
     * @param policyDocs     制度检索命中
     * @param policyContext  可拼入 Prompt 的制度依据上下文
     */
    private record PolicyEvidence(List<PolicyRagDocument> policyDocs, String policyContext) {
    }
}
