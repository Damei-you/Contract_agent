package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 审批辅助请求：由当前审批人角色与可选关注点驱动检索与生成。
 *
 * @param approverRole 审批角色，必填（如「财务经理」）
 * @param focus        本次特别关注的风险或条款方向，可为空
 */
public record ApprovalAssistRequest(
        @NotBlank String approverRole,
        String focus
) {

    /**
     * 紧凑构造：将 {@code focus} 的 null 规范为空串，便于下游拼接 Prompt。
     */
    public ApprovalAssistRequest {
        if (focus == null) {
            focus = "";
        }
    }
}
