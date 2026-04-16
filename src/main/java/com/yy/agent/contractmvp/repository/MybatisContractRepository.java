package com.yy.agent.contractmvp.repository;

import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.domain.Contract;
import com.yy.agent.contractmvp.mapper.ClauseChunkMapper;
import com.yy.agent.contractmvp.mapper.ContractMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis 的合同与条款持久化；审批轨迹暂未落库。
 */
@Repository
@Primary
@Profile("!test")
public class MybatisContractRepository implements ContractRepository {

    private final ContractMapper contractMapper;
    private final ClauseChunkMapper clauseChunkMapper;

    public MybatisContractRepository(ContractMapper contractMapper, ClauseChunkMapper clauseChunkMapper) {
        this.contractMapper = contractMapper;
        this.clauseChunkMapper = clauseChunkMapper;
    }

    @Override
    public Optional<Contract> findById(String id) {
        return Optional.ofNullable(contractMapper.selectById(id));
    }

    @Override
    public Optional<ClauseChunk> findChunk(String contractId, String chunkId) {
        return Optional.ofNullable(clauseChunkMapper.selectOne(contractId, chunkId));
    }

    @Override
    public List<ClauseChunk> findChunksByContractId(String contractId) {
        return clauseChunkMapper.selectByContractId(contractId);
    }

    @Override
    public List<ApprovalRecord> findApprovalRecordsByContractId(String contractId) {
        return List.of();
    }

    @Override
    public Contract save(Contract contract) {
        contractMapper.upsert(contract);
        return contract;
    }

    @Override
    public void replaceChunks(String contractId, List<ClauseChunk> chunks) {
        clauseChunkMapper.deleteByContractId(contractId);
        if (chunks != null && !chunks.isEmpty()) {
            clauseChunkMapper.insertBatch(chunks);
        }
    }

    @Override
    public void replaceApprovalRecords(String contractId, List<ApprovalRecord> records) {
        // 审批表未建
    }

    @Override
    @Transactional
    public void saveContractWithChunks(Contract contract, List<ClauseChunk> chunks) {
        save(contract);
        replaceChunks(contract.id(), chunks);
        replaceApprovalRecords(contract.id(), List.of());
    }
}
