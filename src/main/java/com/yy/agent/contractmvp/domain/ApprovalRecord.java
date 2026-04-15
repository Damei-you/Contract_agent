package com.yy.agent.contractmvp.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 单条审批轨迹：流程中某一节点上某位审批人的结论与意见。
 * <ul>
 *   <li>{@code id}：记录主键（如 AR-001）</li>
 *   <li>{@code contractId}：所属合同</li>
 *   <li>{@code stepNo}：流程步骤序号，升序排列</li>
 *   <li>{@code approverRole}：审批角色描述（如「财务经理」）</li>
 *   <li>{@code decision}：结论枚举 {@link ApprovalDecision}</li>
 *   <li>{@code decisionTime}：决策时间（含时区）</li>
 *   <li>{@code commentSummary}：意见摘要</li>
 *   <li>{@code linkedPolicyIds}：引用到的政策条目 id</li>
 *   <li>{@code linkedClauseChunkIds}：引用到的条款块 id</li>
 *   <li>{@code riskItems}：结构化风险列表，可与 {@code commentSummary} 并存</li>
 *   <li>{@code vectorDocId}：若该条记录单独入库向量时的文档 id</li>
 * </ul>
 */
public record ApprovalRecord(
        String id,
        String contractId,
        int stepNo,
        String approverRole,
        ApprovalDecision decision,
        OffsetDateTime decisionTime,
        String commentSummary,
        List<String> linkedPolicyIds,
        List<String> linkedClauseChunkIds,
        List<RiskItem> riskItems,
        String vectorDocId
) {

    /**
     * 紧凑构造：校验 id、contractId、审批角色与结论；列表复制为不可变；{@code vectorDocId} 可默认。
     */
    public ApprovalRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contractId, "contractId");
        if (approverRole == null || approverRole.isBlank()) {
            throw new IllegalArgumentException("approverRole is required");
        }
        Objects.requireNonNull(decision, "decision");
        linkedPolicyIds = linkedPolicyIds == null ? List.of() : List.copyOf(linkedPolicyIds);
        linkedClauseChunkIds = linkedClauseChunkIds == null ? List.of() : List.copyOf(linkedClauseChunkIds);
        riskItems = riskItems == null ? List.of() : List.copyOf(riskItems);
        if (commentSummary == null) {
            commentSummary = "";
        }
        if (vectorDocId == null || vectorDocId.isBlank()) {
            vectorDocId = "doc_ar_" + id;
        }
    }
}
