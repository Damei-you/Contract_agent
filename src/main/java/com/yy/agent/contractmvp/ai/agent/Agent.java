package com.yy.agent.contractmvp.ai.agent;

/**
 * AI 编排层中的最小角色单元：接收共享上下文与业务输入，返回结构化结果和可解释执行轨迹。
 * <p>
 * 当前用于把合同事实、制度依据、风险审查、审批建议等职责从单一 Assistant 中拆开；
 * 后续若需要独立 Bean、并行执行或失败降级，仍可复用该接口契约。
 *
 * @param <I> Agent 输入类型
 * @param <O> Agent 输出类型
 */
@FunctionalInterface
public interface Agent<I, O> {

    /**
     * 执行单个 Agent 角色。
     *
     * @param context 当前请求级编排上下文
     * @param input   当前 Agent 的输入
     * @return Agent 输出和本次执行轨迹
     */
    AgentResult<O> run(AgentContext context, I input);
}
