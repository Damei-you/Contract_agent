package com.yy.agent.contractmvp.ai.agent;

/**
 * 面向接口返回的轻量 Agent 执行轨迹。
 * <p>
 * 它不是完整日志，也不保存 Prompt 或模型原文，只表达该角色在本轮协作中的关键贡献。
 *
 * @param agentName Agent 角色名称
 * @param summary   该角色输出的简要说明
 */
public record AgentTrace(String agentName, String summary) {

    /**
     * 紧凑构造：对外输出字段不返回 null，保持 API 契约稳定。
     */
    public AgentTrace {
        agentName = agentName == null ? "" : agentName;
        summary = summary == null ? "" : summary;
    }
}
