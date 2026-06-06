package com.yy.agent.contractmvp.ai.agent;

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
}
