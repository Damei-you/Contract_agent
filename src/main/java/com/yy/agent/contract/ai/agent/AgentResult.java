package com.yy.agent.contract.ai.agent;

import java.util.List;

/**
 * Agent 的标准返回包装：同时携带业务输出和一条可解释执行轨迹。
 * <p>
 * 业务输出继续使用强类型对象，轨迹则用于 API 返回中的 agentTrace，方便前端或人工审查理解
 * “哪个角色做了什么”。
 *
 * @param output Agent 业务输出
 * @param trace  Agent 执行轨迹，可为空
 * @param <O>    输出类型
 */
public record AgentResult<O>(O output, AgentTrace trace) {

    /**
     * 构造包含一条标准轨迹的 Agent 返回值。
     *
     * @param output    业务输出
     * @param agentName Agent 名称
     * @param summary   轨迹摘要
     * @param <O>       输出类型
     * @return 标准 Agent 返回值
     */
    public static <O> AgentResult<O> of(O output, String agentName, String summary) {
        return new AgentResult<>(output, new AgentTrace(agentName, summary));
    }

    /**
     * 构造包含证据 id 的轨迹，供前端按 Agent 展示可点击溯源。
     *
     * @param output            业务输出
     * @param agentName         Agent 名称
     * @param summary           轨迹摘要
     * @param retrievedChunkIds 合同条款通道命中 id
     * @param retrievedPolicyIds 制度通道命中 id
     * @param <O>               输出类型
     * @return 标准 Agent 返回值
     */
    public static <O> AgentResult<O> of(
            O output,
            String agentName,
            String summary,
            List<String> retrievedChunkIds,
            List<String> retrievedPolicyIds
    ) {
        return new AgentResult<>(output, new AgentTrace(agentName, summary, retrievedChunkIds, retrievedPolicyIds));
    }
}
