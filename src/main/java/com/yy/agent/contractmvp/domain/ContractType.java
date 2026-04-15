package com.yy.agent.contractmvp.domain;

/**
 * 合同业务类型：用于区分采购类与服务类合同，驱动策略文案与示例数据分支。
 * <ul>
 *   <li>{@link #PROCUREMENT}：采购/供货等以交付物为主的合同</li>
 *   <li>{@link #SERVICE}：运维、咨询等以持续服务为主的合同</li>
 * </ul>
 */
public enum ContractType {

    /** 采购合同（展示名：采购合同）。 */
    PROCUREMENT("采购合同"),
    /** 服务合同（展示名：服务合同）。 */
    SERVICE("服务合同");

    private final String displayName;

    /**
     * @param displayName 对业务展示的中文名称
     */
    ContractType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return 对业务人员展示的中文名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 按中文展示名解析，如「采购合同」。
     *
     * @param value 展示名字符串，不可为空
     * @return 对应枚举
     * @throws IllegalArgumentException 无法识别时
     */
    public static ContractType fromDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contract type is blank");
        }
        for (ContractType t : values()) {
            if (t.displayName.equals(value.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown contract type: " + value);
    }

    /**
     * 灵活解析：支持枚举名（不区分大小写）、英文别名（PURCHASE/GOODS→采购）、或中文展示名。
     *
     * @param value API / 导入数据中的类型字符串
     * @return 对应枚举
     * @throws IllegalArgumentException 无法识别时
     */
    public static ContractType fromFlexible(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contract type is blank");
        }
        String s = value.trim();
        for (ContractType t : values()) {
            if (t.name().equalsIgnoreCase(s)) {
                return t;
            }
        }
        if ("PURCHASE".equalsIgnoreCase(s) || "GOODS".equalsIgnoreCase(s)) {
            return PROCUREMENT;
        }
        return fromDisplayName(s);
    }
}
