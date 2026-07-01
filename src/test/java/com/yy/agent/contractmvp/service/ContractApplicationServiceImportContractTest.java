package com.yy.agent.contractmvp.service;

import com.yy.agent.contractmvp.ai.AiContractAssistant;
import com.yy.agent.contractmvp.ai.rag.ContractVectorIngestionService;
import com.yy.agent.contractmvp.api.dto.ContractClauseChunkResponse;
import com.yy.agent.contractmvp.api.dto.ImportChunkDto;
import com.yy.agent.contractmvp.api.dto.ImportContractRequest;
import com.yy.agent.contractmvp.api.dto.ImportContractResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContractApplicationServiceImportContractTest {

    @Test
    void shouldImportNewContractWithoutConfirmation() {
        InMemoryContractRepository repository = new InMemoryContractRepository(null);
        ContractApplicationService service = service(repository);

        ImportContractResponse response = service.importContract(importRequest("CTR-NEW-001", false));

        assertThat(response.contractId()).isEqualTo("CTR-NEW-001");
        assertThat(response.imported()).isTrue();
        assertThat(response.overwritten()).isFalse();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(repository.saveContractWithChunksCalls).isEqualTo(1);
        assertThat(repository.contract.partyAName()).isEqualTo("新甲方公司");
        assertThat(repository.chunks).hasSize(1);
    }

    @Test
    void shouldAskForOverwriteConfirmationWithoutWritingExistingContract() {
        Contract existing = demoContract("CTR-EXISTING-001", "旧甲方公司");
        InMemoryContractRepository repository = new InMemoryContractRepository(existing);
        ContractApplicationService service = service(repository);

        ImportContractResponse response = service.importContract(importRequest("CTR-EXISTING-001", false));

        assertThat(response.contractId()).isEqualTo("CTR-EXISTING-001");
        assertThat(response.imported()).isFalse();
        assertThat(response.overwritten()).isFalse();
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.message()).contains("确认后将覆盖");
        assertThat(repository.saveContractWithChunksCalls).isZero();
        assertThat(repository.contract.partyAName()).isEqualTo("旧甲方公司");
        assertThat(repository.chunks).isEmpty();
    }

    @Test
    void shouldOverwriteExistingContractWhenConfirmed() {
        Contract existing = demoContract("CTR-EXISTING-002", "旧甲方公司");
        InMemoryContractRepository repository = new InMemoryContractRepository(existing);
        ContractApplicationService service = service(repository);

        ImportContractResponse response = service.importContract(importRequest("CTR-EXISTING-002", true));

        assertThat(response.contractId()).isEqualTo("CTR-EXISTING-002");
        assertThat(response.imported()).isTrue();
        assertThat(response.overwritten()).isTrue();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(repository.saveContractWithChunksCalls).isEqualTo(1);
        assertThat(repository.contract.partyAName()).isEqualTo("新甲方公司");
        assertThat(repository.chunks).hasSize(1);
        assertThat(repository.chunks.get(0).contractId()).isEqualTo("CTR-EXISTING-002");
    }

    @Test
    void shouldReturnContractChunkDetail() {
        InMemoryContractRepository repository = new InMemoryContractRepository(null);
        ContractApplicationService service = service(repository);
        service.importContract(importRequest("CTR-DETAIL-001", false));

        ContractClauseChunkResponse response = service.getChunk("CTR-DETAIL-001", "CTR-DETAIL-001-ch-1");

        assertThat(response.id()).isEqualTo("CTR-DETAIL-001-ch-1");
        assertThat(response.contractId()).isEqualTo("CTR-DETAIL-001");
        assertThat(response.clauseTitle()).isEqualTo("付款条件");
        assertThat(response.textForEmbedding()).contains("验收合格后30个自然日");
    }

    private static ContractApplicationService service(InMemoryContractRepository repository) {
        return new ContractApplicationService(
                repository,
                mock(AiContractAssistant.class),
                new StaticListableBeanFactory().getBeanProvider(ContractVectorIngestionService.class)
        );
    }

    private static ImportContractRequest importRequest(String id, boolean overwriteConfirmed) {
        return new ImportContractRequest(
                id,
                "procurement",
                "新甲方公司",
                "新乙方公司",
                "CNY",
                new BigDecimal("100000.00"),
                new BigDecimal("13"),
                new BigDecimal("113000.00"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "上海",
                "验收后30日付款",
                "采购部",
                "MEDIUM",
                null,
                "测试导入",
                List.of(new ImportChunkDto(
                        "",
                        "PAY",
                        "付款条件",
                        "财务",
                        "甲方应在验收合格后30个自然日内支付合同价款。"
                )),
                overwriteConfirmed
        );
    }

    private static Contract demoContract(String id, String partyAName) {
        return new Contract(
                id,
                ContractType.PROCUREMENT,
                partyAName,
                "旧乙方公司",
                "CNY",
                new BigDecimal("500000.00"),
                new BigDecimal("13"),
                new BigDecimal("565000.00"),
                LocalDate.of(2025, 1, 10),
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 12, 31),
                "北京",
                "旧付款条件",
                "信息技术部",
                RiskSeverity.MEDIUM,
                "doc_old",
                "旧合同"
        );
    }

    /**
     * 测试用最小合同仓储：记录是否真正执行了导入写入。
     */
    private static final class InMemoryContractRepository implements ContractRepository {

        private Contract contract;
        private List<ClauseChunk> chunks = new ArrayList<>();
        private List<ApprovalRecord> approvals = new ArrayList<>();
        private int saveContractWithChunksCalls;

        private InMemoryContractRepository(Contract contract) {
            this.contract = contract;
        }

        @Override
        public Optional<Contract> findById(String id) {
            if (contract != null && contract.id().equals(id)) {
                return Optional.of(contract);
            }
            return Optional.empty();
        }

        @Override
        public Optional<ClauseChunk> findChunk(String contractId, String chunkId) {
            return chunks.stream()
                    .filter(c -> c.contractId().equals(contractId) && c.id().equals(chunkId))
                    .findFirst();
        }

        @Override
        public List<ClauseChunk> findChunksByContractId(String contractId) {
            return chunks.stream()
                    .filter(c -> c.contractId().equals(contractId))
                    .toList();
        }

        @Override
        public List<ApprovalRecord> findApprovalRecordsByContractId(String contractId) {
            return List.copyOf(approvals);
        }

        @Override
        public Contract save(Contract contract) {
            this.contract = contract;
            return contract;
        }

        @Override
        public void replaceChunks(String contractId, List<ClauseChunk> chunks) {
            this.chunks = new ArrayList<>(chunks);
        }

        @Override
        public void replaceApprovalRecords(String contractId, List<ApprovalRecord> records) {
            this.approvals = new ArrayList<>(records);
        }

        @Override
        public void saveContractWithChunks(Contract contract, List<ClauseChunk> chunks) {
            saveContractWithChunksCalls++;
            save(contract);
            replaceChunks(contract.id(), chunks);
            replaceApprovalRecords(contract.id(), List.of());
        }
    }
}
