package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 导入合同请求：主数据字段与 {@link com.yy.agent.contractmvp.domain.Contract} 对齐，并携带可选条款列表。
 * <p>
 * {@code type} 支持枚举名、英文别名或中文展示名（见 {@link com.yy.agent.contractmvp.domain.ContractType#fromFlexible(String)}）；
 * {@code riskTier} 支持枚举名或中文（见 {@link com.yy.agent.contractmvp.domain.RiskSeverity#fromDisplayName(String)}）。
 *
 * @param id 合同 id，可空则服务端生成 UUID
 * @param type                合同类型字符串
 * @param partyAName          甲方名称
 * @param partyBName          乙方名称
 * @param currency            币种，可空默认 CNY
 * @param amountExTax         不含税金额
 * @param taxRatePct          税率（百分比数值，如 13 表示 13%）
 * @param amountIncTax        含税金额
 * @param signDate            签订日
 * @param effectiveDate       生效日
 * @param endDate             结束日
 * @param performanceSite     履约地点
 * @param paymentTermsSummary 付款摘要
 * @param businessOwnerDept   主办部门
 * @param riskTier            风险档位字符串
 * @param vectorDocId         向量文档 id，可空则领域对象内默认
 * @param notes               备注
 * @param chunks              条款块列表，可空
 */
public record ImportContractRequest(
        String id,
        @NotBlank String type,
        @NotBlank String partyAName,
        @NotBlank String partyBName,
        String currency,
        @NotNull BigDecimal amountExTax,
        @NotNull BigDecimal taxRatePct,
        @NotNull BigDecimal amountIncTax,
        @NotNull LocalDate signDate,
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate endDate,
        String performanceSite,
        String paymentTermsSummary,
        String businessOwnerDept,
        @NotBlank String riskTier,
        String vectorDocId,
        String notes,
        @Valid List<ImportChunkDto> chunks
) {

    /**
     * 紧凑构造：货币、可空字符串与 {@code chunks} 规范化。
     */
    public ImportContractRequest {
        if (currency == null || currency.isBlank()) {
            currency = "CNY";
        }
        if (performanceSite == null) {
            performanceSite = "";
        }
        if (paymentTermsSummary == null) {
            paymentTermsSummary = "";
        }
        if (businessOwnerDept == null) {
            businessOwnerDept = "";
        }
        if (notes == null) {
            notes = "";
        }
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
