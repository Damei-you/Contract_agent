package com.yy.agent.contractmvp.ai.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 协作中的请求级上下文。
 * <p>
 * 该对象只在一次编排流程内共享，用于保存工作流名称、合同 id 和各 Agent 产生的执行轨迹；
 * 不承载大块 RAG 正文或模型原始响应，避免上下文对象演变成隐式全局状态。
 */
public final class AgentContext {

    private final String workflowName;
    private final String contractId;
    private final List<AgentTrace> traces = new ArrayList<>();

    /**
     * @param workflowName 当前编排流程名称，例如 risk-check 或 approval-assist
     * @param contractId   当前处理的合同 id
     */
    public AgentContext(String workflowName, String contractId) {
        this.workflowName = workflowName == null ? "" : workflowName;
        this.contractId = contractId == null ? "" : contractId;
    }

    /**
     * @return 当前编排流程名称
     */
    public String workflowName() {
        return workflowName;
    }

    /**
     * @return 当前合同 id
     */
    public String contractId() {
        return contractId;
    }

    /**
     * 仅允许同包内的编排器追加轨迹，避免业务代码绕过编排顺序直接改写 trace。
     *
     * @param trace Agent 执行轨迹
     */
    void addTrace(AgentTrace trace) {
        traces.add(trace);
    }

    /**
     * @return 按执行顺序排列的 Agent 轨迹快照
     */
    public List<AgentTrace> traces() {
        return List.copyOf(traces);
    }
}
