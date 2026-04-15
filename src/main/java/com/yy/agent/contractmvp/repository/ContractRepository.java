package com.yy.agent.contractmvp.repository;

import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.domain.Contract;

import java.util.List;
import java.util.Optional;

/**
 * 合同聚合持久化抽象：主数据、条款块、审批记录分层存储。
 * <p>
 * MVP 由 {@link MockContractRepository} 内存实现；后续可换为 JPA/MyBatis 等而不改上层服务签名。
 */
public interface ContractRepository {

    /**
     * @param id 合同主键
     * @return 合同 Optional
     */
    Optional<Contract> findById(String id);

    /**
     * @param contractId 合同 id
     * @param chunkId    条款块 id
     * @return 条款块 Optional
     */
    Optional<ClauseChunk> findChunk(String contractId, String chunkId);

    /**
     * @param contractId 合同 id
     * @return 该合同下全部条款块（不可变列表视图由实现决定，当前实现返回 copy）
     */
    List<ClauseChunk> findChunksByContractId(String contractId);

    /**
     * @param contractId 合同 id
     * @return 该合同关联的审批记录列表
     */
    List<ApprovalRecord> findApprovalRecordsByContractId(String contractId);

    /**
     * 保存或覆盖合同主数据。
     *
     * @param contract 领域对象
     * @return 传入对象（便于链式）
     */
    Contract save(Contract contract);

    /**
     * 整体替换某合同的条款块列表（导入或全量更新场景）。
     *
     * @param contractId 合同 id
     * @param chunks     新条款列表
     */
    void replaceChunks(String contractId, List<ClauseChunk> chunks);

    /**
     * 整体替换某合同的审批记录（导入时可置空列表）。
     *
     * @param contractId 合同 id
     * @param records    审批记录全量替换
     */
    void replaceApprovalRecords(String contractId, List<ApprovalRecord> records);
}
