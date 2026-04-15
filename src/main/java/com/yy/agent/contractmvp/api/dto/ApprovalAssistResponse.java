package com.yy.agent.contractmvp.api.dto;

import java.util.List;

/**
 * 审批辅助响应：模型给出的结论建议与可执行核对清单。
 *
 * @param suggestion 对当前审批节点的建议结论
 * @param checklist  待核对事项列表（模型 JSON 中的字符串数组）
 */
public record ApprovalAssistResponse(String suggestion, List<String> checklist) {
}
