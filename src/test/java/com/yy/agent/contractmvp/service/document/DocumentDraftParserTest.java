package com.yy.agent.contractmvp.service.document;

import com.yy.agent.contractmvp.api.dto.ImportContractRequest;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDraftParserTest {

    private final DocumentDraftParser parser = new DocumentDraftParser();

    @Test
    void parsesContractDraftFromText() {
        String text = """
                合同编号：CTR-PARSE-2026-001
                甲方：上海甲方公司
                乙方：北京乙方公司
                不含税金额：人民币500,000.00元
                合同金额：人民币565,000.00元
                税率：13%
                签订日期：2026年04月16日
                生效日期：2026-04-16
                有效期至：2027-04-15

                第一条 付款条件
                甲方在收到发票及验收证明后30个自然日内付款。

                第二条 违约责任
                逾期履约按日万分之五承担违约金。
                """;

        DocumentDraftParser.ParsedContractDraft parsed = parser.parseContractDraft(
                new ExtractedDocument("contract.txt", "text/plain", "text/plain", text.length(), text)
        );

        ImportContractRequest draft = parsed.draft();
        assertThat(draft.id()).isEqualTo("CTR-PARSE-2026-001");
        assertThat(draft.partyAName()).isEqualTo("上海甲方公司");
        assertThat(draft.partyBName()).isEqualTo("北京乙方公司");
        assertThat(draft.amountIncTax()).isEqualByComparingTo(new BigDecimal("565000.00"));
        assertThat(draft.amountExTax()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(draft.taxRatePct()).isEqualByComparingTo(new BigDecimal("13.0000"));
        assertThat(draft.signDate()).isEqualTo(LocalDate.of(2026, 4, 16));
        assertThat(draft.effectiveDate()).isEqualTo(LocalDate.of(2026, 4, 16));
        assertThat(draft.endDate()).isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(draft.chunks()).hasSize(2);
        assertThat(draft.chunks().getFirst().clauseCategory()).isEqualTo("财务");
        assertThat(parsed.warnings()).isEmpty();
    }

    @Test
    void generatesStableFallbackContractIdWhenContractNumberIsMissing() {
        String text = """
                甲方：上海甲方公司
                乙方：北京乙方公司
                合同金额：人民币113,000.00元
                税率：13%
                签订日期：2026年04月16日
                生效日期：2026-04-16
                有效期至：2027-04-15

                第一条 付款条件
                甲方在收到发票后付款。
                """;

        DocumentDraftParser.ParsedContractDraft first = parser.parseContractDraft(
                new ExtractedDocument("contract-a.txt", "text/plain", "text/plain", text.length(), text)
        );
        DocumentDraftParser.ParsedContractDraft second = parser.parseContractDraft(
                new ExtractedDocument("contract-b.txt", "text/plain", "text/plain", text.length(), text)
        );

        assertThat(first.draft().id()).startsWith("CTR-FILE-");
        assertThat(first.draft().id()).isEqualTo(second.draft().id());
    }

    @Test
    void parsesPolicyDraftFromText() {
        String text = """
                第一条 采购合同预付款比例不得超过合同总金额的30%，且需提供等额保函。

                第二条 与关联方签订采购合同需经合规部门审批，并在合同中明确披露关联关系。
                """;

        DocumentDraftParser.ParsedPolicyDraft parsed = parser.parsePolicyDraft(
                new ExtractedDocument("policy.txt", "text/plain", "text/plain", text.length(), text)
        );

        ImportPolicyKnowledgeRequest draft = parsed.draft();
        assertThat(draft.policies()).hasSize(2);
        assertThat(draft.policies().getFirst().policyDomain()).isEqualTo("财务合规");
        assertThat(draft.policies().getFirst().severity()).isEqualTo("HIGH");
        assertThat(draft.policies().getFirst().triggerKeywords()).contains("预付款", "保函");
        assertThat(draft.policies().get(1).policyDomain()).isEqualTo("合规审查");
        assertThat(draft.policies().get(1).appliesToContractType()).isEqualTo("procurement");
    }
}
