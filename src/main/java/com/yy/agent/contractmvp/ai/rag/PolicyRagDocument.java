package com.yy.agent.contractmvp.ai.rag;

import java.util.List;

/**
 * 制度通道 RAG 命中片段：除文本与相关性分数外，保留 {@code policyId} 与依据元数据，
 * 便于风险检查与审批辅助回填 {@code relatedPolicyIds}、{@code requiredEvidence}、{@code escalationRole}。
 *
 * @param policyId         制度条目主键，对应 {@link com.yy.agent.contractmvp.domain.PolicyKnowledgeItem#policyId()}
 * @param policyDomain     制度领域，便于上层按角色/领域重排
 * @param controlObjective 控制目标短语，可空
 * @param severity         严重度（{@code HIGH/MEDIUM/LOW}）
 * @param requiredEvidence 命中条目要求的材料/证据列表，已拆分为多元素
 * @param escalationRole   命中条目的升级或会签角色，可空字符串
 * @param text             用于向量化的制度条文（参与 Prompt 拼装）
 * @param score            相关性分数（关键词或向量相似度，由实现定义）
 */
public record PolicyRagDocument(
        String policyId,
        String policyDomain,
        String controlObjective,
        String severity,
        List<String> requiredEvidence,
        String escalationRole,
        String text,
        double score
) {

    /**
     * 紧凑构造：null 字符串规范为空串，{@code requiredEvidence} 复制为不可变列表。
     */
    public PolicyRagDocument {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId is blank");
        }
        if (policyDomain == null) {
            policyDomain = "";
        }
        if (controlObjective == null) {
            controlObjective = "";
        }
        if (severity == null) {
            severity = "";
        }
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        if (escalationRole == null) {
            escalationRole = "";
        }
        if (text == null) {
            text = "";
        }
    }
}
