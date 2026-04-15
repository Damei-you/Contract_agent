package com.yy.agent.contractmvp.domain;

import java.util.Objects;

/**
 * 合同条款分块：一条面向向量检索/关键词检索的文本单元，必须归属某合同 {@link Contract#id()}。
 * <ul>
 *   <li>{@code id}：块主键（如 c004）</li>
 *   <li>{@code clauseCode}：内部条款编码（PRICE、PAY 等）</li>
 *   <li>{@code clauseTitle} / {@code clauseCategory}：标题与分类，辅助展示与过滤</li>
 *   <li>{@code partyFocus}：条款更侧重哪一方（甲方/乙方/双方）</li>
 *   <li>{@code riskFlag}：该块在知识库中标注的风险等级</li>
 *   <li>{@code sourceSection}：对应纸质合同章节（如「第4条」）</li>
 *   <li>{@code textForEmbedding}：实际参与向量与关键词匹配的正文</li>
 *   <li>{@code relatedAmountField}：若与金额字段相关可填元数据键名</li>
 *   <li>{@code reviewPriority}：人工配置的审阅优先级提示</li>
 * </ul>
 */
public record ClauseChunk(
        String id,
        String contractId,
        String clauseCode,
        String clauseTitle,
        String clauseCategory,
        String partyFocus,
        RiskSeverity riskFlag,
        String sourceSection,
        String textForEmbedding,
        String relatedAmountField,
        String reviewPriority
) {

    /**
     * 紧凑构造：保证 id、contractId、正文非空，其余字符串类字段默认空串，{@code riskFlag} 默认 LOW。
     */
    public ClauseChunk {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(textForEmbedding, "textForEmbedding");
        if (clauseCode == null) {
            clauseCode = "";
        }
        if (clauseTitle == null) {
            clauseTitle = "";
        }
        if (clauseCategory == null) {
            clauseCategory = "";
        }
        if (partyFocus == null) {
            partyFocus = "";
        }
        if (riskFlag == null) {
            riskFlag = RiskSeverity.LOW;
        }
        if (sourceSection == null) {
            sourceSection = "";
        }
        if (relatedAmountField == null) {
            relatedAmountField = "";
        }
        if (reviewPriority == null) {
            reviewPriority = "";
        }
    }
}
