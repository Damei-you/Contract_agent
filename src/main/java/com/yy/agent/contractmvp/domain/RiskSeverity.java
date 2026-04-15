package com.yy.agent.contractmvp.domain;

/**
 * 风险等级：既可用于 {@link Contract#riskTier()} 合同整体分层，也可用于 {@link RiskItem#severity()} 单点问题。
 */
public enum RiskSeverity {

    /** 低。 */
    LOW("低"),
    /** 中。 */
    MEDIUM("中"),
    /** 高。 */
    HIGH("高");

    private final String displayName;

    /**
     * @param displayName 中文展示名（低/中/高）
     */
    RiskSeverity(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return 中文展示名（低/中/高）
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 按中文展示名或枚举名（不区分大小写）解析。
     *
     * @param value 如「高」或 HIGH
     * @return 对应枚举
     * @throws IllegalArgumentException 无法识别时
     */
    public static RiskSeverity fromDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("risk severity is blank");
        }
        for (RiskSeverity s : values()) {
            if (s.displayName.equalsIgnoreCase(value.trim())
                    || s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown risk severity: " + value);
    }
}
