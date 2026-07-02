package com.yy.agent.contract.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/**
 * 政策/制度知识库导入的单条条目 DTO，将映射为
 * {@link com.yy.agent.contract.domain.PolicyKnowledgeItem}。
 *
 * @param policyId               制度条目主键，必填且应稳定不变
 * @param policyDomain           制度领域，如 {@code 财务合规}
 * @param appliesToContractType  适用合同类型，多个值用 {@code ;} 分隔
 * @param severity               严重度（{@code HIGH/MEDIUM/LOW} 或中文）
 * @param triggerKeywords        触发关键词，多个值用 {@code ;} 分隔，可空
 * @param controlObjective       控制目标短语，可空
 * @param policyTextForEmbedding 用于向量化的制度条文摘要，必填
 * @param requiredEvidence       要求的材料/证据，多个值用 {@code ;} 分隔，可空
 * @param escalationRole         升级或会签角色，可空
 * @param vectorDocId            向量文档 id，可空（缺省由领域对象生成）
 * @param updatedAt              更新时间，可空（缺省由服务端补充）
 */
public record PolicyKnowledgeItemDto(
        @NotBlank String policyId,
        @NotBlank String policyDomain,
        @NotBlank String appliesToContractType,
        @NotBlank String severity,
        String triggerKeywords,
        String controlObjective,
        @NotBlank String policyTextForEmbedding,
        String requiredEvidence,
        String escalationRole,
        String vectorDocId,
        OffsetDateTime updatedAt
) {
}
