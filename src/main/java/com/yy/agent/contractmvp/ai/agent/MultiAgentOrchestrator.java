package com.yy.agent.contractmvp.ai.agent;

import org.springframework.stereotype.Component;

/**
 * 轻量级多 Agent 顺序编排器。
 * <p>
 * 当前版本只负责“执行 Agent -> 收集 trace -> 返回输出”的最小闭环，避免过早引入复杂工作流框架。
 * 后续如需并行扇出、失败降级、重试或人工复核节点，可在该类中扩展，而不改变单个 Agent 的接口。
 */
@Component
public class MultiAgentOrchestrator {

    /**
     * 执行一个 Agent，并把它产生的轨迹追加到请求上下文。
     *
     * @param context 当前请求级编排上下文
     * @param agent   待执行的 Agent
     * @param input   Agent 输入
     * @param <I>     输入类型
     * @param <O>     输出类型
     * @return Agent 业务输出
     */
    public <I, O> O run(AgentContext context, Agent<I, O> agent, I input) {
        AgentResult<O> result = agent.run(context, input);
        if (result.trace() != null) {
            context.addTrace(result.trace());
        }
        return result.output();
    }
}
