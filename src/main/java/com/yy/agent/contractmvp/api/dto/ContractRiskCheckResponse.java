package com.yy.agent.contractmvp.api.dto;

import com.yy.agent.contractmvp.domain.RiskItem;

import java.util.List;

/**
 * 风险审查响应：总结与结构化风险条目（模型 JSON 解析失败时 summary 可能为原始文本）。
 *
 * @param summary   总体结论或模型原文
 * @param riskItems 风险点列表，可能为空
 */
public record ContractRiskCheckResponse(String summary, List<RiskItem> riskItems) {
}
