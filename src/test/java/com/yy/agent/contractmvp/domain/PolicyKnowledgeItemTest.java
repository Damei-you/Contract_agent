package com.yy.agent.contractmvp.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyKnowledgeItemTest {

    @Test
    void splitMultiShouldHandleSemicolonsAndTrimWhitespace() {
        List<String> result = PolicyKnowledgeItem.splitMulti("付款条款截图; 控制点清单 ；验收/对账要求");

        assertThat(result).containsExactly("付款条款截图", "控制点清单", "验收/对账要求");
    }

    @Test
    void splitMultiShouldReturnEmptyListForBlank() {
        assertThat(PolicyKnowledgeItem.splitMulti(null)).isEmpty();
        assertThat(PolicyKnowledgeItem.splitMulti("")).isEmpty();
        assertThat(PolicyKnowledgeItem.splitMulti("  ")).isEmpty();
    }

    @Test
    void appliesToShouldMatchByContractTypeDisplayName() {
        PolicyKnowledgeItem item = sample("采购合同;服务合同");

        assertThat(item.appliesTo(ContractType.PROCUREMENT)).isTrue();
        assertThat(item.appliesTo(ContractType.SERVICE)).isTrue();
        assertThat(item.appliesTo(null)).isFalse();
    }

    @Test
    void appliesToShouldRejectUnsupportedContractType() {
        PolicyKnowledgeItem item = sample("服务合同");

        assertThat(item.appliesTo(ContractType.PROCUREMENT)).isFalse();
    }

    @Test
    void compactConstructorShouldDefaultVectorDocIdAndOptionalFields() {
        PolicyKnowledgeItem item = new PolicyKnowledgeItem(
                "POL-FIN-001",
                "财务合规",
                "采购合同;服务合同",
                RiskSeverity.HIGH,
                null,
                null,
                "完整制度条文",
                null,
                null,
                null,
                null
        );

        assertThat(item.vectorDocId()).isEqualTo("doc_pol_POL-FIN-001");
        assertThat(item.triggerKeywords()).isEmpty();
        assertThat(item.controlObjective()).isEmpty();
        assertThat(item.requiredEvidence()).isEmpty();
        assertThat(item.escalationRole()).isEmpty();
    }

    @Test
    void compactConstructorShouldRejectBlankRequiredFields() {
        assertThatThrownBy(() -> new PolicyKnowledgeItem(
                "  ", "财务合规", "采购合同", RiskSeverity.HIGH,
                "", "", "正文", "", "", null, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PolicyKnowledgeItem(
                "POL-FIN-001", "财务合规", "采购合同", RiskSeverity.HIGH,
                "", "", "  ", "", "", null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static PolicyKnowledgeItem sample(String appliesTo) {
        return new PolicyKnowledgeItem(
                "POL-FIN-001",
                "财务合规",
                appliesTo,
                RiskSeverity.HIGH,
                "预付;全额预付",
                "付款风险",
                "原则上应避免无担保的高比例预付。",
                "付款计划表;担保文件",
                "财务负责人",
                null,
                null
        );
    }
}
