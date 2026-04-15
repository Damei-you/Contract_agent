package com.yy.agent.contractmvp.domain;

import java.util.List;

/**
 * 结构化风险点：表示审查或审批中发现的一条可追踪问题。
 * <ul>
 *   <li>{@code code}：稳定编码，便于规则与报表（如 TAX_CLARITY）</li>
 *   <li>{@code severity}：严重度 {@link RiskSeverity}</li>
 *   <li>{@code detail}：人类可读说明</li>
 *   <li>{@code relatedClauseChunkIds}：关联的条款块 id，便于 RAG 回溯</li>
 *   <li>{@code relatedPolicyIds}：关联内控政策 id（若有）</li>
 * </ul>
 * 来源可以是规则引擎命中、大模型 JSON 输出，或审批人在 {@link ApprovalRecord} 中填报。
 */
public record RiskItem(
        String code,
        RiskSeverity severity,
        String detail,
        List<String> relatedClauseChunkIds,
        List<String> relatedPolicyIds
) {

    /**
     * 紧凑构造：校验 code 与 severity，复制列表为不可变视图。
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
    }
}
