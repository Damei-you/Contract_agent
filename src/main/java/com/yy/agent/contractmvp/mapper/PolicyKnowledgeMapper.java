package com.yy.agent.contractmvp.mapper;

import com.yy.agent.contractmvp.domain.PolicyKnowledgeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 政策/制度知识库 MyBatis Mapper：仅暴露增删查与按 id 集合查询的核心能力。
 */
@Mapper
public interface PolicyKnowledgeMapper {

    /**
     * 按主键查询单条制度条目。
     *
     * @param policyId 制度条目主键
     * @return 命中条目；不存在返回 {@code null}
     */
    PolicyKnowledgeItem selectById(@Param("policyId") String policyId);

    /**
     * 全表查询，按 {@code policy_id} 升序，主要供离线导入摘要或管理视图使用。
     *
     * @return 全部制度条目
     */
    List<PolicyKnowledgeItem> selectAll();

    /**
     * 按主键集合批量查询，便于风险项依据回填。
     *
     * @param policyIds 制度条目 id 列表，空集合时调用方应避免触发该方法
     * @return 命中条目，缺失项不返回
     */
    List<PolicyKnowledgeItem> selectByIds(@Param("policyIds") List<String> policyIds);

    /**
     * 单条 upsert：以 {@code policy_id} 为冲突键覆盖更新所有字段。
     *
     * @param item 制度条目
     * @return 受影响行数
     */
    int upsert(PolicyKnowledgeItem item);

    /**
     * 按主键删除单条制度条目。
     *
     * @param policyId 主键
     * @return 受影响行数
     */
    int deleteById(@Param("policyId") String policyId);
}
