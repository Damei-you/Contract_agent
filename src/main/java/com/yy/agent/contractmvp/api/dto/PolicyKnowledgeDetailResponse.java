package com.yy.agent.contractmvp.api.dto;

import com.yy.agent.contractmvp.domain.PolicyKnowledgeItem;

import java.time.OffsetDateTime;

/**
 * 制度依据详情响应，供前端从 AgentTrace 溯源到具体制度条文。
 *
 * @param policyId               制度条目主键
 * @param policyDomain           制度领域
 * @param appliesToContractType  适用合同类型
 * @param severity               严重度
 * @param triggerKeywords        触发关键词
 * @param controlObjective       控制目标
 * @param policyTextForEmbedding 制度条文正文
 * @param requiredEvidence       要求证据
 * @param escalationRole         升级或会签角色
 * @param vectorDocId            向量文档 id
 * @param updatedAt              更新时间
 */
public record PolicyKnowledgeDetailResponse(
        String policyId,
        String policyDomain,
        String appliesToContractType,
        String severity,
        String triggerKeywords,
        String controlObjective,
        String policyTextForEmbedding,
        String requiredEvidence,
        String escalationRole,
        String vectorDocId,
        OffsetDateTime updatedAt
) {

    public static PolicyKnowledgeDetailResponse from(PolicyKnowledgeItem item) {
        return new PolicyKnowledgeDetailResponse(
                item.policyId(),
                item.policyDomain(),
                item.appliesToContractType(),
                item.severity().name(),
                item.triggerKeywords(),
                item.controlObjective(),
                item.policyTextForEmbedding(),
                item.requiredEvidence(),
                item.escalationRole(),
                item.vectorDocId(),
                item.updatedAt()
        );
    }
}
