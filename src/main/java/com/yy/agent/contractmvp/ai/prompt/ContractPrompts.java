package com.yy.agent.contractmvp.ai.prompt;

/**
 * 合同场景 Prompt 文本工厂：集中管理系统提示词与用户消息拼装格式，避免散落在调用处。
 * <p>
 * 后续可替换为从配置中心或数据库加载的模板引擎实现。
 */
public final class ContractPrompts {

    private ContractPrompts() {
    }

    /**
     * 问答场景系统提示：约束模型仅依据给定材料作答并说明信息不足。
     *
     * @return system 消息正文
     */
    public static String qaSystem() {
        return """
                你是企业财务与法务方向的合同问答助手。请仅依据提供的「合同摘要」与「检索到的条款片段」作答；
                若材料不足以判断，请明确说明缺少哪些信息，并给出需要补充核对的要点。回答简洁、分点列出关键结论。
                """;
    }

    /**
     * 问答场景用户消息：嵌入合同摘要、检索条款与具体问题。
     *
     * @param contractSummary 来自工具层的合同主数据摘要
     * @param ragContext      格式化后的条款检索结果
     * @param question        用户问题
     * @return user 消息正文
     */
    public static String qaUser(String contractSummary, String ragContext, String question) {
        return """
                【合同摘要】
                %s

                【检索条款】
                %s

                【用户问题】
                %s
                """.formatted(contractSummary, ragContext, question);
    }

    /**
     * 风险审查系统提示：要求只输出符合约定 Schema 的 JSON。
     *
     * @return system 消息正文
     */
    public static String riskCheckSystem() {
        return """
                你是合同风险审查助手。请结合「合同摘要」「条款检索」「历史审批摘要」输出 JSON，且只输出 JSON，不要 Markdown 围栏。
                JSON Schema:
                {
                  "summary": "string",
                  "riskItems": [
                    {
                      "code": "string",
                      "severity": "LOW|MEDIUM|HIGH",
                      "detail": "string",
                      "relatedClauseChunkIds": ["string"],
                      "relatedPolicyIds": ["string"]
                    }
                  ]
                }
                severity 必须为大写枚举。若无风险点，riskItems 可为空数组。
                """;
    }

    /**
     * 风险审查用户消息：合同摘要、条款检索、历史审批压缩文本。
     *
     * @param contractSummary 合同主数据摘要
     * @param ragContext      检索条款
     * @param approvalDigest  审批历史摘要
     * @return user 消息正文
     */
    public static String riskCheckUser(String contractSummary, String ragContext, String approvalDigest) {
        return """
                【合同摘要】
                %s

                【条款检索】
                %s

                【历史审批摘要】
                %s
                """.formatted(contractSummary, ragContext, approvalDigest);
    }

    /**
     * 审批辅助系统提示：输出 suggestion + checklist 的 JSON。
     *
     * @return system 消息正文
     */
    public static String approvalAssistSystem() {
        return """
                你是审批辅助助手。根据合同摘要、条款检索与历史审批意见，为当前审批人提供「结论建议」与「核对清单」。
                输出 JSON，且只输出 JSON：
                {
                  "suggestion": "string",
                  "checklist": ["string"]
                }
                checklist 3-8 条，可操作、可核验。
                """;
    }

    /**
     * 审批辅助用户消息：包含当前角色、关注重点、摘要、检索与历史审批。
     *
     * @param approverRole    审批角色
     * @param focus           关注重点，可为空（展示为未指定）
     * @param contractSummary 合同摘要
     * @param ragContext      检索条款
     * @param approvalDigest  历史审批摘要
     * @return user 消息正文
     */
    public static String approvalAssistUser(
            String approverRole,
            String focus,
            String contractSummary,
            String ragContext,
            String approvalDigest
    ) {
        return """
                【当前审批角色】
                %s

                【关注重点】
                %s

                【合同摘要】
                %s

                【条款检索】
                %s

                【历史审批摘要】
                %s
                """.formatted(
                approverRole,
                focus == null || focus.isBlank() ? "（未指定）" : focus,
                contractSummary,
                ragContext,
                approvalDigest
        );
    }
}
