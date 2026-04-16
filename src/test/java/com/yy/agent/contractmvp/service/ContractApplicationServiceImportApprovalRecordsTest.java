package com.yy.agent.contractmvp.service;

import com.yy.agent.contractmvp.ai.AiContractAssistant;
import com.yy.agent.contractmvp.ai.rag.ContractVectorIngestionService;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordDto;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordsRequest;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordsResponse;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRiskItemDto;
import com.yy.agent.contractmvp.domain.ApprovalDecision;
import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import com.yy.agent.contractmvp.repository.MockContractRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContractApplicationServiceImportApprovalRecordsTest {

    @Test
    void shouldReplaceApprovalRecordsForExistingContract() {
        MockContractRepository repository = new MockContractRepository();
        ContractApplicationService service = new ContractApplicationService(
                repository,
                mock(AiContractAssistant.class),
                new StaticListableBeanFactory().getBeanProvider(ContractVectorIngestionService.class)
        );

        ImportApprovalRecordsRequest request = new ImportApprovalRecordsRequest(List.of(
                new ImportApprovalRecordDto(
                        "",
                        5,
                        "法务",
                        "附条件通过",
                        OffsetDateTime.parse("2026-04-16T11:00:00+08:00"),
                        "需补充解除条款对等性。",
                        List.of("POL-LEGAL-009"),
                        List.of("c007", "c009"),
                        List.of(new ImportApprovalRiskItemDto(
                                "TERMINATION_BALANCE",
                                "MEDIUM",
                                "解除条件需双方对等。",
                                List.of("c009"),
                                List.of("POL-LEGAL-009")
                        )),
                        ""
                )
        ));

        ImportApprovalRecordsResponse response = service.importApprovalRecords("CTR-2026-DEMO-001", request);
        List<ApprovalRecord> records = repository.findApprovalRecordsByContractId("CTR-2026-DEMO-001");

        assertThat(response.contractId()).isEqualTo("CTR-2026-DEMO-001");
        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().id()).isEqualTo("CTR-2026-DEMO-001-ar-1");
        assertThat(records.getFirst().decision()).isEqualTo(ApprovalDecision.CONDITIONAL_APPROVED);
        assertThat(records.getFirst().riskItems()).hasSize(1);
        assertThat(records.getFirst().riskItems().getFirst().severity()).isEqualTo(RiskSeverity.MEDIUM);
    }
}
