package com.yy.agent.contractmvp.repository;

import com.yy.agent.contractmvp.domain.ContractType;
import com.yy.agent.contractmvp.domain.PolicyKnowledgeItem;
import com.yy.agent.contractmvp.mapper.PolicyKnowledgeMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis 的政策/制度知识库持久化实现：跨合同共享的业务规则权威数据。
 * <p>
 * {@code test} profile 下不装配（MyBatis mapper 未扫描），避免测试环境依赖 PostgreSQL。
 * 适用合同类型按中文展示名以 {@code ;} 分隔字符串维护，按类型筛选时在内存中拆分匹配，
 * 兼顾导入便利性与未来切换为 JSONB 数组的灵活性。
 */
@Repository
@Profile("!test")
public class MybatisPolicyKnowledgeRepository implements PolicyKnowledgeRepository {

    private final PolicyKnowledgeMapper policyKnowledgeMapper;

    public MybatisPolicyKnowledgeRepository(PolicyKnowledgeMapper policyKnowledgeMapper) {
        this.policyKnowledgeMapper = policyKnowledgeMapper;
    }

    @Override
    public Optional<PolicyKnowledgeItem> findById(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(policyKnowledgeMapper.selectById(policyId.trim()));
    }

    @Override
    public List<PolicyKnowledgeItem> findAll() {
        return policyKnowledgeMapper.selectAll();
    }

    @Override
    public List<PolicyKnowledgeItem> findByIds(List<String> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = policyIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return policyKnowledgeMapper.selectByIds(normalized);
    }

    @Override
    public List<PolicyKnowledgeItem> findByApplicableContractType(ContractType contractType) {
        if (contractType == null) {
            return List.of();
        }
        return policyKnowledgeMapper.selectAll().stream()
                .filter(p -> p.appliesTo(contractType))
                .toList();
    }

    @Override
    public PolicyKnowledgeItem upsert(PolicyKnowledgeItem item) {
        policyKnowledgeMapper.upsert(item);
        return item;
    }

    @Override
    @Transactional
    public List<PolicyKnowledgeItem> upsertAll(List<PolicyKnowledgeItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<PolicyKnowledgeItem> out = new ArrayList<>(items.size());
        for (PolicyKnowledgeItem item : items) {
            policyKnowledgeMapper.upsert(item);
            out.add(item);
        }
        return out;
    }

    @Override
    public void deleteById(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return;
        }
        policyKnowledgeMapper.deleteById(policyId.trim());
    }
}
