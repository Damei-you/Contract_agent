package com.yy.agent.contractmvp.ai.agent;

import java.util.List;

/**
 * 面向接口返回的轻量 Agent 执行轨迹。
 * <p>
 * 它不是完整日志，也不保存 Prompt 或模型原文，只表达该角色在本轮协作中的关键贡献。
 *
 * @param agentName         Agent 角色名称
 * @param summary           该角色输出的简要说明
 * @param retrievedChunkIds 合同条款通道命中的条款块主键
 * @param retrievedPolicyIds 制度通道命中的制度依据主键
 */
public record AgentTrace(
        String agentName,
        String summary,
        List<String> retrievedChunkIds,
        List<String> retrievedPolicyIds
) {

    /**
     * 兼容旧调用方：未传证据 id 时返回空列表。
     */
    public AgentTrace(String agentName, String summary) {
        this(agentName, summary, List.of(), List.of());
    }

    /**
     * 紧凑构造：对外输出字段不返回 null，保持 API 契约稳定。
     */
    public AgentTrace {
        agentName = agentName == null ? "" : agentName;
        summary = summary == null ? "" : summary;
        retrievedChunkIds = normalizeIds(retrievedChunkIds);
        retrievedPolicyIds = normalizeIds(retrievedPolicyIds);
    }

    private static List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
