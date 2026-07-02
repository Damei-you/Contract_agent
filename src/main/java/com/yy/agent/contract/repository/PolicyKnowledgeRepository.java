package com.yy.agent.contract.repository;

import com.yy.agent.contract.domain.ContractType;
import com.yy.agent.contract.domain.PolicyKnowledgeItem;

import java.util.List;
import java.util.Optional;

/**
 * 政策/制度知识库持久化抽象：保存跨合同共享的业务规则权威数据。
 * <p>
 * 默认由 {@link MybatisPolicyKnowledgeRepository} 落 PostgreSQL；{@code test} profile 下不装配该仓储（避免测试环境依赖 PostgreSQL）。
 */
public interface PolicyKnowledgeRepository {

    /**
     * 按主键查询单条制度条目。
     *
     * @param policyId 制度条目主键
     * @return Optional 包装；不存在为空
     */
    Optional<PolicyKnowledgeItem> findById(String policyId);

    /**
     * 全量查询所有制度条目，主要供离线导入摘要或管理视图使用。
     *
     * @return 全部制度条目（不可变视图）
     */
    List<PolicyKnowledgeItem> findAll();

    /**
     * 按主键集合批量查询，便于风险项依据回填。
     *
     * @param policyIds 主键列表，可空或为空集合（返回空列表）
     * @return 命中条目；缺失的 id 不返回
     */
    List<PolicyKnowledgeItem> findByIds(List<String> policyIds);

    /**
     * 按合同类型筛选可适用的制度条目；用于管理查询或非向量批处理场景。
     *
     * @param contractType 合同类型，{@code null} 时返回空列表
     * @return 命中条目（不可变视图）
     */
    List<PolicyKnowledgeItem> findByApplicableContractType(ContractType contractType);

    /**
     * 单条 upsert：以 {@code policyId} 为冲突键覆盖更新，便于批量刷新制度知识库。
     *
     * @param item 制度条目
     * @return 写入的对象，便于链式调用
     */
    PolicyKnowledgeItem upsert(PolicyKnowledgeItem item);

    /**
     * 批量 upsert：按顺序逐条覆盖更新，调用方应在事务内执行。
     *
     * @param items 制度条目列表，空或 null 时不做处理
     * @return 实际写入的条目列表（保留输入顺序）
     */
    List<PolicyKnowledgeItem> upsertAll(List<PolicyKnowledgeItem> items);

    /**
     * 按主键删除一条制度条目。
     *
     * @param policyId 主键
     */
    void deleteById(String policyId);
}
