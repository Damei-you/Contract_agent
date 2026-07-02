package com.yy.agent.contract;

import com.yy.agent.contract.domain.ApprovalRecord;
import com.yy.agent.contract.domain.ClauseChunk;
import com.yy.agent.contract.domain.Contract;
import com.yy.agent.contract.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

/**
 * 应用上下文冒烟测试：验证主要 Bean 可正常装配。
 */
@SpringBootTest
@ActiveProfiles("test")
class ContractAgentApplicationTests {

	/**
	 * 空测试方法：仅触发 Spring 容器启动与依赖注入。
	 */
	@Test
	void contextLoads() {
	}

    @TestConfiguration
    static class ContractRepositoryTestConfig {

        @Bean
        ContractRepository contractRepository() {
            return new ContractRepository() {
                @Override
                public Optional<Contract> findById(String id) {
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
                    return List.of();
                }

                @Override
                public Contract save(Contract contract) {
                    return contract;
                }

                @Override
                public void replaceChunks(String contractId, List<ClauseChunk> chunks) {
                }

                @Override
                public void replaceApprovalRecords(String contractId, List<ApprovalRecord> records) {
                }

                @Override
                public void saveContractWithChunks(Contract contract, List<ClauseChunk> chunks) {
                }
            };
        }
    }

}
