package com.yy.agent.contractmvp.domain;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 政策/制度知识库条目：跨合同共享的业务规则单元，供制度通道 RAG、风险项依据引用与审批核对清单使用。
 * <ul>
 *   <li>{@code policyId}：稳定主键（如 {@code POL-FIN-001}），被审批记录与风险项引用，应避免改名</li>
 *   <li>{@code policyDomain}：制度领域（财务合规/税务合规/资金支付 等）</li>
 *   <li>{@code appliesToContractType}：适用合同类型，多个值用 {@code ;} 分隔（如 {@code 采购合同;服务合同}）</li>
 *   <li>{@code severity}：严重度，复用 {@link RiskSeverity}</li>
 *   <li>{@code triggerKeywords}：触发关键词，多个值用 {@code ;} 分隔，便于召回与高亮</li>
 *   <li>{@code controlObjective}：控制目标短语</li>
 *   <li>{@code policyTextForEmbedding}：用于向量化的制度条文摘要（触发条件 + 要求 + 例外/补救 + 证据）</li>
 *   <li>{@code requiredEvidence}：要求提供的材料/证据，多个值用 {@code ;} 分隔，可直接转 checklist</li>
 *   <li>{@code escalationRole}：需要升级或会签的角色（如 财务负责人 / 法务）</li>
 *   <li>{@code vectorDocId}：制度条目对应的向量文档 id，可空时由紧凑构造默认生成</li>
 *   <li>{@code updatedAt}：最近导入或更新时间，可空</li>
 * </ul>
 */
public record PolicyKnowledgeItem(
        String policyId,
        String policyDomain,
        String appliesToContractType,
        RiskSeverity severity,
        String triggerKeywords,
        String controlObjective,
        String policyTextForEmbedding,
        String requiredEvidence,
        String escalationRole,
        String vectorDocId,
        OffsetDateTime updatedAt
) {

    /**
     * 紧凑构造：校验关键字段非空，可空字符串字段规范化为空串，{@code vectorDocId} 默认按 policyId 生成。
     */
    public PolicyKnowledgeItem {
        Objects.requireNonNull(policyId, "policyId");
        if (policyId.isBlank()) {
            throw new IllegalArgumentException("policyId is blank");
        }
        Objects.requireNonNull(policyDomain, "policyDomain");
        if (policyDomain.isBlank()) {
            throw new IllegalArgumentException("policyDomain is blank");
        }
        Objects.requireNonNull(appliesToContractType, "appliesToContractType");
        if (appliesToContractType.isBlank()) {
            throw new IllegalArgumentException("appliesToContractType is blank");
        }
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(policyTextForEmbedding, "policyTextForEmbedding");
        if (policyTextForEmbedding.isBlank()) {
            throw new IllegalArgumentException("policyTextForEmbedding is blank");
        }
        if (triggerKeywords == null) {
            triggerKeywords = "";
        }
        if (controlObjective == null) {
            controlObjective = "";
        }
        if (requiredEvidence == null) {
            requiredEvidence = "";
        }
        if (escalationRole == null) {
            escalationRole = "";
        }
        if (vectorDocId == null || vectorDocId.isBlank()) {
            vectorDocId = "doc_pol_" + policyId;
        }
    }

    /**
     * 将 {@code ;} 分隔的多值字段拆分为列表，去除空元素与首尾空白。
     *
     * @param value 半角或全角分号分隔的字符串
     * @return 拆分后的不可变列表，输入为空时返回空列表
     */
    public static List<String> splitMulti(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[;；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return {@link #appliesToContractType} 拆分后的列表
     */
    public List<String> appliesToContractTypeList() {
        return splitMulti(appliesToContractType);
    }

    /**
     * @return {@link #triggerKeywords} 拆分后的列表
     */
    public List<String> triggerKeywordsList() {
        return splitMulti(triggerKeywords);
    }

    /**
     * @return {@link #requiredEvidence} 拆分后的列表
     */
    public List<String> requiredEvidenceList() {
        return splitMulti(requiredEvidence);
    }

    /**
     * 判断该制度是否适用某种合同类型（按中文展示名比较）。
     *
     * @param contractType 合同类型枚举，可空时返回 {@code false}
     * @return 是否覆盖该合同类型
     */
    public boolean appliesTo(ContractType contractType) {
        if (contractType == null) {
            return false;
        }
        String display = contractType.displayName();
        for (String t : appliesToContractTypeList()) {
            if (t.equals(display)) {
                return true;
            }
        }
        return false;
    }
}
