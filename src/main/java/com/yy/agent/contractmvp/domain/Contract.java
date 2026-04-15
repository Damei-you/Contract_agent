package com.yy.agent.contractmvp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 合同主数据聚合：描述一份采购/服务合同在审批与向量检索中需要的核心元信息。
 * <p>
 * 字段含义概览：
 * <ul>
 *   <li>{@code id}：业务主键，如 CTR-2026-DEMO-001</li>
 *   <li>{@code type}：合同类型（采购/服务），影响策略与话术</li>
 *   <li>{@code partyAName} / {@code partyBName}：甲乙方名称</li>
 *   <li>{@code currency}：币种，默认 CNY</li>
 *   <li>{@code amountExTax} / {@code taxRatePct} / {@code amountIncTax}：不含税金额、税率(%)、含税金额</li>
 *   <li>{@code signDate} / {@code effectiveDate} / {@code endDate}：签署日、生效日、结束日</li>
 *   <li>{@code performanceSite}：履约/交付地点摘要</li>
 *   <li>{@code paymentTermsSummary}：付款条件文字摘要，供 Prompt 与人工快速理解</li>
 *   <li>{@code businessOwnerDept}：主办部门</li>
 *   <li>{@code riskTier}：业务上预设的合同整体风险档位（低/中/高）</li>
 *   <li>{@code vectorDocId}：与向量库或文档版本的关联 id；为空时由紧凑构造默认生成</li>
 *   <li>{@code notes}：备注</li>
 * </ul>
 * 与 {@link ClauseChunk} 为一对多：同一 {@code id} 下可挂多条可检索条款块。
 */
public record Contract(
        String id,
        ContractType type,
        String partyAName,
        String partyBName,
        String currency,
        BigDecimal amountExTax,
        BigDecimal taxRatePct,
        BigDecimal amountIncTax,
        LocalDate signDate,
        LocalDate effectiveDate,
        LocalDate endDate,
        String performanceSite,
        String paymentTermsSummary,
        String businessOwnerDept,
        RiskSeverity riskTier,
        String vectorDocId,
        String notes
) {

    /**
     * 紧凑构造：校验必填字段，并为货币、向量文档 id、可空字符串字段提供合理默认值。
     */
    public Contract {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(partyAName, "partyAName");
        Objects.requireNonNull(partyBName, "partyBName");
        if (currency == null || currency.isBlank()) {
            currency = "CNY";
        }
        Objects.requireNonNull(amountExTax, "amountExTax");
        Objects.requireNonNull(taxRatePct, "taxRatePct");
        Objects.requireNonNull(amountIncTax, "amountIncTax");
        Objects.requireNonNull(signDate, "signDate");
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(riskTier, "riskTier");
        if (vectorDocId == null || vectorDocId.isBlank()) {
            vectorDocId = "doc_ctr_" + id;
        }
        if (notes == null) {
            notes = "";
        }
        if (paymentTermsSummary == null) {
            paymentTermsSummary = "";
        }
        if (performanceSite == null) {
            performanceSite = "";
        }
        if (businessOwnerDept == null) {
            businessOwnerDept = "";
        }
    }
}
