package com.yy.agent.contract.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 政策/制度知识库导入结果。
 * <p>
 * 业务表写入是事务性的；向量库写入由独立服务执行，失败时通过 {@link #vectorIngestionWarning} 提示，
 * 不会阻塞接口（避免业务表已成功但客户端误判失败）。
 *
 * @param importedCount           实际写入业务表条数
 * @param policyIds               成功落库的 {@code policyId} 列表，按导入顺序返回
 * @param vectorIngestionWarning  向量入库失败时的简要错误描述；成功时为 {@code null}（序列化时省略）
 */
public record ImportPolicyKnowledgeResponse(
        int importedCount,
        List<String> policyIds,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String vectorIngestionWarning
) {

    /**
     * 紧凑构造：列表 null 转空列表，warning 为空白时统一规范化为 {@code null}，便于序列化省略。
     */
    public ImportPolicyKnowledgeResponse {
        policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
        if (vectorIngestionWarning != null && vectorIngestionWarning.isBlank()) {
            vectorIngestionWarning = null;
        }
    }

    /**
     * 业务表 + 向量库均成功的便捷构造。
     */
    public ImportPolicyKnowledgeResponse(int importedCount, List<String> policyIds) {
        this(importedCount, policyIds, null);
    }
}
