package com.yy.agent.contract.api.dto;

import com.yy.agent.contract.ai.agent.AgentTrace;
import com.yy.agent.contract.domain.RiskItem;

import java.util.List;

/**
 * 风险审查响应：总结、结构化风险条目，以及本轮多 Agent 协作轨迹。
 * <p>
 * 模型 JSON 解析失败时，summary 可能为模型原文，riskItems 为空；agentTrace 用于说明
 * 合同事实、制度依据、风险审查等角色各自完成了什么。
 *
 * @param summary    总体结论或模型原文
 * @param riskItems  风险点列表，可能为空
 * @param agentTrace 多 Agent 执行轨迹，按编排顺序排列
 */
public record ContractRiskCheckResponse(String summary, List<RiskItem> riskItems, List<AgentTrace> agentTrace) {

    /**
     * 兼容旧调用方的构造器：未显式传入 trace 时返回空轨迹列表。
     *
     * @param summary   总体结论或模型原文
     * @param riskItems 风险点列表
     */
    public ContractRiskCheckResponse(String summary, List<RiskItem> riskItems) {
        this(summary, riskItems, List.of());
    }

    /**
     * 紧凑构造：列表字段 null 转为空列表，确保接口契约稳定。
     */
    public ContractRiskCheckResponse {
        if (summary == null) {
            summary = "";
        }
        riskItems = riskItems == null ? List.of() : List.copyOf(riskItems);
        agentTrace = agentTrace == null ? List.of() : List.copyOf(agentTrace);
    }
}
