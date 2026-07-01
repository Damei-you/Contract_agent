package com.yy.agent.contractmvp.ai.prompt;

/**
 * 合同场景 Prompt 文本工厂：集中管理系统提示词与用户消息拼装格式，避免散落在调用处。
 * <p>
 * RAG 约定：用户消息按「合同摘要」「合同条款上下文」「制度依据上下文」拼装；
 * 制度依据上下文可为空，模型必须区分合同事实与制度要求，避免把通用制度要求误写为合同已约定内容。
 */
public final class ContractPrompts {

    /** 检索通道无命中时的统一占位文案。 */
    private static final String EMPTY_CONTEXT_PLACEHOLDER = "（暂无相关材料）";

    private ContractPrompts() {
    }

    /**
     * 问答场景系统提示：约束模型仅依据给定材料作答并区分事实/制度。
     *
     * @return system 消息正文
     */
    public static String qaSystem() {
        return """
                你是企业财务与法务方向的合同问答助手。请仅依据提供的「合同摘要」「合同条款上下文」「制度依据上下文」作答。
                注意区分两类来源：合同条款是当前合同的事实，制度依据是公司内部规范要求。
                若问题与合同事实有关而条款上下文不足，请明确说明缺少的信息。
                只有在制度依据上下文确实提供内容时，才可用于解释、校验或提示；若制度依据上下文为暂无相关材料，不要做制度合规判断。
                不要把制度要求误写成合同已约定内容。
                回答简洁、分点列出关键结论。
                """;
    }

    /**
     * 问答场景用户消息：嵌入合同摘要、合同条款上下文、制度依据上下文与具体问题。
     *
     * @param contractSummary 来自工具层的合同主数据摘要
     * @param clauseRagContext 合同条款通道命中文本
     * @param policyRagContext 制度通道命中文本
     * @param question 用户问题
     * @return user 消息正文
     */
    public static String qaUser(
            String contractSummary,
            String clauseRagContext,
            String policyRagContext,
            String question
    ) {
        return """
                【合同摘要】
                %s

                【合同条款上下文】
                %s

                【制度依据上下文】
                %s

                【用户问题】
                %s
                """.formatted(
                contractSummary,
                blankAsPlaceholder(clauseRagContext),
                blankAsPlaceholder(policyRagContext),
                question
        );
    }

    /**
     * 政策/制度问答系统提示：仅依据制度依据上下文回答。
     *
     * @return system 消息正文
     */
    public static String policyQaSystem() {
        return """
                你是企业内部政策制度问答助手。请仅依据提供的「制度依据上下文」回答用户问题。
                回答时应明确说明结论、适用条件、需要留存的证据或审批动作；如材料不足，请直接说明缺少依据。
                不要编造未在制度依据中出现的政策编号、金额比例、审批角色或证据要求。
                """;
    }

    /**
     * 政策/制度问答用户消息：嵌入可选合同类型、制度依据上下文与具体问题。
     *
     * @param contractTypeScope 合同类型范围描述
     * @param policyRagContext  制度通道命中文本
     * @param question          用户问题
     * @return user 消息正文
     */
    public static String policyQaUser(
            String contractTypeScope,
            String policyRagContext,
            String question
    ) {
        return """
                【合同类型范围】
                %s

                【制度依据上下文】
                %s

                【用户问题】
                %s
                """.formatted(
                contractTypeScope == null || contractTypeScope.isBlank() ? "不限" : contractTypeScope,
                blankAsPlaceholder(policyRagContext),
                question
        );
    }

    /**
     * 风险审查系统提示：要求结构化 JSON 输出，并显式包含制度依据派生字段。
     *
     * @return system 消息正文
     */
    public static String riskCheckSystem() {
        return """
                你是合同风险审查助手。请结合「合同摘要」「合同条款上下文」「制度依据上下文」「历史审批摘要」输出 JSON，且只输出 JSON，不要 Markdown 围栏。
                JSON Schema:
                {
                  "summary": "string",
                  "riskItems": [
                    {
                      "code": "string",
                      "severity": "LOW|MEDIUM|HIGH",
                      "detail": "string",
                      "relatedClauseChunkIds": ["string"],
                      "relatedPolicyIds": ["string"],
                      "requiredEvidence": ["string"],
                      "escalationRole": "string"
                    }
                  ]
                }
                约束：
                - severity 必须为大写枚举。
                - 风险项必须由合同条款触发；relatedClauseChunkIds 应为当前合同的条款 id；若仅依赖制度无合同条款命中，请将该风险标注为「需人工复核」并尽量给出 relatedPolicyIds。
                - 若命中制度依据，relatedPolicyIds、requiredEvidence、escalationRole 应尽量回填来自制度通道的稳定值；无依据时返回空数组或空字符串。
                - 若无风险点，riskItems 可为空数组。
                """;
    }

    /**
     * 风险审查用户消息：合同摘要、合同条款上下文、制度依据上下文与历史审批摘要。
     *
     * @param contractSummary 合同主数据摘要
     * @param clauseRagContext 合同条款通道命中文本
     * @param policyRagContext 制度通道命中文本
     * @param approvalDigest 审批历史摘要
     * @return user 消息正文
     */
    public static String riskCheckUser(
            String contractSummary,
            String clauseRagContext,
            String policyRagContext,
            String approvalDigest
    ) {
        return """
                【合同摘要】
                %s

                【合同条款上下文】
                %s

                【制度依据上下文】
                %s

                【历史审批摘要】
                %s
                """.formatted(
                contractSummary,
                blankAsPlaceholder(clauseRagContext),
                blankAsPlaceholder(policyRagContext),
                approvalDigest
        );
    }

    /**
     * 审批辅助系统提示：输出 suggestion + checklist 的 JSON，强制结合制度依据生成核对项。
     *
     * @return system 消息正文
     */
    public static String approvalAssistSystem() {
        return """
                你是审批辅助助手。根据合同摘要、合同条款上下文、制度依据上下文与历史审批意见，为当前审批人提供「结论建议」与「核对清单」。
                输出 JSON，且只输出 JSON：
                {
                  "suggestion": "string",
                  "checklist": ["string"]
                }
                约束：
                - checklist 3-8 条，可操作、可核验。
                - 优先由命中的制度依据 requiredEvidence 派生 checklist 项；若涉及升级/会签，可在 suggestion 中点出 escalationRole。
                - 不引入与当前合同无关的制度通用要求；制度依据应解释或校验合同事实，而不是替代合同事实。
                """;
    }

    /**
     * 审批辅助用户消息：当前角色、关注重点、合同摘要、合同条款上下文、制度依据上下文与历史审批。
     *
     * @param approverRole 审批角色
     * @param focus 关注重点，可为空（展示为未指定）
     * @param contractSummary 合同摘要
     * @param clauseRagContext 合同条款通道命中文本
     * @param policyRagContext 制度通道命中文本
     * @param approvalDigest 历史审批摘要
     * @return user 消息正文
     */
    public static String approvalAssistUser(
            String approverRole,
            String focus,
            String contractSummary,
            String clauseRagContext,
            String policyRagContext,
            String approvalDigest
    ) {
        return """
                【当前审批角色】
                %s

                【关注重点】
                %s

                【合同摘要】
                %s

                【合同条款上下文】
                %s

                【制度依据上下文】
                %s

                【历史审批摘要】
                %s
                """.formatted(
                approverRole,
                focus == null || focus.isBlank() ? "（未指定）" : focus,
                contractSummary,
                blankAsPlaceholder(clauseRagContext),
                blankAsPlaceholder(policyRagContext),
                approvalDigest
        );
    }

    private static String blankAsPlaceholder(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY_CONTEXT_PLACEHOLDER;
        }
        return text;
    }
}
