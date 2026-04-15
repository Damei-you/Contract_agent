package com.yy.agent.contractmvp.domain;

/**
 * 审批结论：表示该节点上审批人对合同材料的业务决策结果（非「提交」「转办」等流程动作）。
 */
public enum ApprovalDecision {

    /** 通过。 */
    APPROVED("通过"),
    /** 附条件通过。 */
    CONDITIONAL_APPROVED("附条件通过"),
    /** 退回补充或否决性退回。 */
    REJECTED("退回"),
    /** 待处理；解析空字符串时也落在此枚举。 */
    PENDING("待处理");

    private final String displayName;

    /**
     * @param displayName 流程展示用中文结论
     */
    ApprovalDecision(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return 与流程展示一致的中文结论
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 按中文结论解析；空或空白返回 {@link #PENDING}。
     *
     * @param value 如「附条件通过」
     * @return 对应枚举
     * @throws IllegalArgumentException 无法识别时
     */
    public static ApprovalDecision fromDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        for (ApprovalDecision d : values()) {
            if (d.displayName.equals(value.trim())) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown approval decision: " + value);
    }
}
