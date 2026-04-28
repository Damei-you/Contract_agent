package com.yy.agent.contractmvp.service;

import com.yy.agent.contractmvp.ai.AiContractAssistant;
import com.yy.agent.contractmvp.ai.rag.ContractVectorIngestionService;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordDto;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordsRequest;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordsResponse;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRiskItemDto;
import com.yy.agent.contractmvp.domain.ApprovalDecision;
import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.domain.Contract;
import com.yy.agent.contractmvp.domain.ContractType;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import com.yy.agent.contractmvp.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContractApplicationServiceImportApprovalRecordsTest {

    @Test
    void shouldReplaceApprovalRecordsForExistingContract() {
        InMemoryContractRepository repository = new InMemoryContractRepository();
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
                                List.of("POL-LEGAL-009"),
                                List.of("解除/续期条款截图", "对等性改写建议"),
                                "法务"
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
        assertThat(records.getFirst().riskItems().getFirst().requiredEvidence())
                .containsExactly("解除/续期条款截图", "对等性改写建议");
        assertThat(records.getFirst().riskItems().getFirst().escalationRole()).isEqualTo("法务");
    }

    /**
     * 测试用最小合同仓储：生产代码只保留 PostgreSQL/MyBatis 实现。
     */
    private static final class InMemoryContractRepository implements ContractRepository {

        private Contract contract = demoContract();
        private List<ApprovalRecord> approvals = new ArrayList<>();

        @Override
        public Optional<Contract> findById(String id) {
            if (contract != null && contract.id().equals(id)) {
                return Optional.of(contract);
            }
            return Optional.empty();
        }

        @Override
        public Optional<ClauseChunk> findChunk(String contractId, String chunkId) {
            return Optional.empty();
        }

        @Override
        public List<ClauseChunk> findChunksByContractId(String contractId) {
            return List.of();
        }

        @Override
        public List<ApprovalRecord> findApprovalRecordsByContractId(String contractId) {
            if (contract != null && contract.id().equals(contractId)) {
                return List.copyOf(approvals);
            }
            return List.of();
        }

        @Override
        public Contract save(Contract contract) {
            this.contract = contract;
            return contract;
        }

        @Override
        public void replaceChunks(String contractId, List<ClauseChunk> chunks) {
        }

        @Override
        public void replaceApprovalRecords(String contractId, List<ApprovalRecord> records) {
            approvals = new ArrayList<>(records);
        }

        @Override
        public void saveContractWithChunks(Contract contract, List<ClauseChunk> chunks) {
            save(contract);
            approvals = new ArrayList<>();
        }

        private static Contract demoContract() {
            return new Contract(
                    "CTR-2026-DEMO-001",
                    ContractType.PROCUREMENT,
                    "某某科技有限公司",
                    "某某设备供应商有限公司",
                    "CNY",
                    new BigDecimal("500000.00"),
                    new BigDecimal("13"),
                    new BigDecimal("565000.00"),
                    LocalDate.of(2026, 1, 10),
                    LocalDate.of(2026, 1, 15),
                    LocalDate.of(2026, 12, 31),
                    "甲方指定交付地点",
                    "验收合格后30日内电汇支付至乙方账户",
                    "信息技术部",
                    RiskSeverity.MEDIUM,
                    "doc_ctr_001",
                    "框架下单分批交付"
            );
        }
    }
}
