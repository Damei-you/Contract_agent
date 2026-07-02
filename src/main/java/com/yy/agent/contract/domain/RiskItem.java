package com.yy.agent.contract.domain;

import java.util.List;

/**
 * 结构化风险点：表示审查或审批中发现的一条可追踪问题。
 * <ul>
 *   <li>{@code code}：稳定编码，便于规则与报表（如 TAX_CLARITY）</li>
 *   <li>{@code severity}：严重度 {@link RiskSeverity}</li>
 *   <li>{@code detail}：人类可读说明</li>
 *   <li>{@code relatedClauseChunkIds}：触发该风险的合同条款块 id，便于 RAG 回溯</li>
 *   <li>{@code relatedPolicyIds}：支撑该风险判断的政策/制度条目 id，对应
 *       {@link PolicyKnowledgeItem#policyId()}；若无依据可为空列表</li>
 *   <li>{@code requiredEvidence}：建议核对/补充的材料清单，常由命中制度条目派生</li>
 *   <li>{@code escalationRole}：建议升级或会签的角色，常由命中制度条目派生</li>
 * </ul>
 * 来源可以是规则引擎命中、大模型 JSON 输出，或审批人在 {@link ApprovalRecord} 中填报。
 */
public record RiskItem(
        String code,
        RiskSeverity severity,
        String detail,
        List<String> relatedClauseChunkIds,
        List<String> relatedPolicyIds,
        List<String> requiredEvidence,
        String escalationRole
) {

    /**
     * 紧凑构造：校验 code 与 severity，复制列表为不可变视图，可空字符串与列表规范化为空值。
     */
    public RiskItem {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("risk code is required");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity is required");
        }
        if (detail == null) {
            detail = "";
        }
        relatedClauseChunkIds = relatedClauseChunkIds == null ? List.of() : List.copyOf(relatedClauseChunkIds);
        relatedPolicyIds = relatedPolicyIds == null ? List.of() : List.copyOf(relatedPolicyIds);
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        if (escalationRole == null) {
            escalationRole = "";
        }
    }

    /**
     * 兼容旧调用的便捷构造：未提供制度依据派生字段时，{@code requiredEvidence} 为空列表，{@code escalationRole} 为空串。
     *
     * @param code                  风险编码
     * @param severity              严重度
     * @param detail                详情
     * @param relatedClauseChunkIds 触发条款 id 列表
     * @param relatedPolicyIds      支撑制度 id 列表
     */
    public RiskItem(
            String code,
            RiskSeverity severity,
            String detail,
            List<String> relatedClauseChunkIds,
            List<String> relatedPolicyIds
    ) {
        this(code, severity, detail, relatedClauseChunkIds, relatedPolicyIds, List.of(), "");
    }
}
