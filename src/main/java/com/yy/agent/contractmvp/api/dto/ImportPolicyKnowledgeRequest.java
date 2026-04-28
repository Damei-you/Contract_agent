package com.yy.agent.contractmvp.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 政策/制度知识库导入请求：按 {@code policyId} 覆盖更新所提交条目。
 *
 * @param policies 制度条目列表，至少一条，元素均做字段校验
 */
public record ImportPolicyKnowledgeRequest(
        @NotEmpty @Valid List<PolicyKnowledgeItemDto> policies
) {
}
