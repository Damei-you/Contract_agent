package com.yy.agent.contractmvp.service;

import com.yy.agent.contractmvp.ai.rag.PolicyVectorIngestionService;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeRequest;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeResponse;
import com.yy.agent.contractmvp.api.dto.PolicyKnowledgeItemDto;
import com.yy.agent.contractmvp.domain.ContractType;
import com.yy.agent.contractmvp.domain.PolicyKnowledgeItem;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import com.yy.agent.contractmvp.repository.PolicyKnowledgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyKnowledgeApplicationServiceTest {

    @Test
    void shouldUpsertAllPoliciesAndReturnPolicyIdsInImportOrder() {
        InMemoryPolicyKnowledgeRepository repository = new InMemoryPolicyKnowledgeRepository();
        PolicyKnowledgeApplicationService service = service(repository);

        ImportPolicyKnowledgeRequest request = new ImportPolicyKnowledgeRequest(List.of(
                dto("POL-FIN-001", "财务合规", "采购合同;服务合同", "HIGH", "付款条件应包含验收"),
                dto("POL-TAX-001", "税务合规", "采购合同;服务合同", "高", "明确不含税金额与税率")
        ));

        ImportPolicyKnowledgeResponse response = service.importPolicies(request);

        assertThat(response.importedCount()).isEqualTo(2);
        assertThat(response.policyIds()).containsExactly("POL-FIN-001", "POL-TAX-001");
        assertThat(repository.findById("POL-FIN-001")).isPresent();
        assertThat(repository.findById("POL-TAX-001"))
                .map(PolicyKnowledgeItem::severity)
                .hasValue(RiskSeverity.HIGH);
    }

    @Test
    void shouldDeduplicateByPolicyIdKeepingLastWins() {
        InMemoryPolicyKnowledgeRepository repository = new InMemoryPolicyKnowledgeRepository();
        PolicyKnowledgeApplicationService service = service(repository);

        ImportPolicyKnowledgeRequest request = new ImportPolicyKnowledgeRequest(List.of(
                dto("POL-FIN-001", "财务合规", "采购合同", "HIGH", "v1"),
                dto("POL-FIN-001", "财务合规", "采购合同;服务合同", "HIGH", "v2-后者覆盖")
        ));

        ImportPolicyKnowledgeResponse response = service.importPolicies(request);

        assertThat(response.policyIds()).containsExactly("POL-FIN-001");
        Optional<PolicyKnowledgeItem> stored = repository.findById("POL-FIN-001");
        assertThat(stored).isPresent();
        assertThat(stored.get().policyTextForEmbedding()).isEqualTo("v2-后者覆盖");
        assertThat(stored.get().appliesTo(ContractType.SERVICE)).isTrue();
    }

    @Test
    void shouldDegradeGracefullyWhenVectorIngestionFails() {
        InMemoryPolicyKnowledgeRepository repository = new InMemoryPolicyKnowledgeRepository();
        // 用子类覆盖 ingest 模拟向量库写入异常（如嵌入服务批次上限、重复主键等真实场景）。
        // 父类构造需要 VectorBatchWriter，这里传 null —— 测试只覆盖 ingest，不会触发底层调用。
        PolicyVectorIngestionService failingVectorService = new PolicyVectorIngestionService(null) {
            @Override
            public void ingest(List<PolicyKnowledgeItem> items) {
                throw new IllegalStateException("simulated upstream embedding batch-size 400");
            }
        };
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("policyKnowledgeRepository", repository);
        beanFactory.addBean("policyVectorIngestionService", failingVectorService);
        PolicyKnowledgeApplicationService service = new PolicyKnowledgeApplicationService(
                beanFactory.getBeanProvider(PolicyKnowledgeRepository.class),
                beanFactory.getBeanProvider(PolicyVectorIngestionService.class)
        );

        ImportPolicyKnowledgeRequest request = new ImportPolicyKnowledgeRequest(List.of(
                dto("POL-FIN-001", "财务合规", "采购合同", "HIGH", "测试")
        ));

        ImportPolicyKnowledgeResponse response = service.importPolicies(request);

        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(response.policyIds()).containsExactly("POL-FIN-001");
        assertThat(response.vectorIngestionWarning())
                .isNotBlank()
                .contains("Vector store sync failed");
        // 业务表必须仍然成功落库，让客户端可以安全地按相同请求体重试触发再次向量同步。
        assertThat(repository.findById("POL-FIN-001")).isPresent();
    }

    @Test
    void shouldFailWith503WhenPolicyRepositoryUnavailable() {
        StaticListableBeanFactory empty = new StaticListableBeanFactory();
        PolicyKnowledgeApplicationService service = new PolicyKnowledgeApplicationService(
                empty.getBeanProvider(PolicyKnowledgeRepository.class),
                empty.getBeanProvider(PolicyVectorIngestionService.class)
        );

        ImportPolicyKnowledgeRequest request = new ImportPolicyKnowledgeRequest(List.of(
                dto("POL-FIN-001", "财务合规", "采购合同", "HIGH", "测试")
        ));

        assertThatThrownBy(() -> service.importPolicies(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static PolicyKnowledgeApplicationService service(PolicyKnowledgeRepository repository) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("policyKnowledgeRepository", repository);
        return new PolicyKnowledgeApplicationService(
                beanFactory.getBeanProvider(PolicyKnowledgeRepository.class),
                beanFactory.getBeanProvider(PolicyVectorIngestionService.class)
        );
    }

    private static PolicyKnowledgeItemDto dto(
            String policyId,
            String domain,
            String appliesTo,
            String severity,
            String text
    ) {
        return new PolicyKnowledgeItemDto(
                policyId,
                domain,
                appliesTo,
                severity,
                "kw1;kw2",
                "control objective",
                text,
                "evidence-a;evidence-b",
                "财务负责人",
                null,
                null
        );
    }

    /**
     * 测试用最小内存仓储：避免在生产代码中保留 Mock 实现，仅供单元测试断言导入语义。
     */
    private static final class InMemoryPolicyKnowledgeRepository implements PolicyKnowledgeRepository {

        private final Map<String, PolicyKnowledgeItem> store = new LinkedHashMap<>();

        @Override
        public Optional<PolicyKnowledgeItem> findById(String policyId) {
            return Optional.ofNullable(store.get(policyId));
        }

        @Override
        public List<PolicyKnowledgeItem> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<PolicyKnowledgeItem> findByIds(List<String> policyIds) {
            if (policyIds == null) {
                return List.of();
            }
            List<PolicyKnowledgeItem> out = new ArrayList<>();
            for (String pid : policyIds) {
                PolicyKnowledgeItem item = store.get(pid);
                if (item != null) {
                    out.add(item);
                }
            }
            return List.copyOf(out);
        }

        @Override
        public List<PolicyKnowledgeItem> findByApplicableContractType(ContractType contractType) {
            if (contractType == null) {
                return List.of();
            }
            return store.values().stream()
                    .filter(p -> p.appliesTo(contractType))
                    .toList();
        }

        @Override
        public PolicyKnowledgeItem upsert(PolicyKnowledgeItem item) {
            store.put(item.policyId(), item);
            return item;
        }

        @Override
        public List<PolicyKnowledgeItem> upsertAll(List<PolicyKnowledgeItem> items) {
            List<PolicyKnowledgeItem> out = new ArrayList<>();
            for (PolicyKnowledgeItem item : items) {
                store.put(item.policyId(), item);
                out.add(item);
            }
            return out;
        }

        @Override
        public void deleteById(String policyId) {
            store.remove(policyId);
        }
    }
}
