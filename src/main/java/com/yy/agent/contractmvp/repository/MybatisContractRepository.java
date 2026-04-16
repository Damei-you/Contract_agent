package com.yy.agent.contractmvp.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.domain.Contract;
import com.yy.agent.contractmvp.domain.RiskItem;
import com.yy.agent.contractmvp.mapper.ApprovalRecordMapper;
import com.yy.agent.contractmvp.mapper.ClauseChunkMapper;
import com.yy.agent.contractmvp.mapper.ContractMapper;
import com.yy.agent.contractmvp.mapper.model.ApprovalRecordRow;
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
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ObjectMapper objectMapper;

    public MybatisContractRepository(
            ContractMapper contractMapper,
            ClauseChunkMapper clauseChunkMapper,
            ApprovalRecordMapper approvalRecordMapper,
            ObjectMapper objectMapper
    ) {
        this.contractMapper = contractMapper;
        this.clauseChunkMapper = clauseChunkMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.objectMapper = objectMapper;
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
        return approvalRecordMapper.selectByContractId(contractId).stream()
                .map(this::toDomain)
                .toList();
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
        approvalRecordMapper.deleteByContractId(contractId);
        if (records != null && !records.isEmpty()) {
            approvalRecordMapper.insertBatch(records.stream().map(this::toRow).toList());
        }
    }

    @Override
    @Transactional
    public void saveContractWithChunks(Contract contract, List<ClauseChunk> chunks) {
        save(contract);
        replaceChunks(contract.id(), chunks);
        replaceApprovalRecords(contract.id(), List.of());
    }

    private ApprovalRecord toDomain(ApprovalRecordRow row) {
        return new ApprovalRecord(
                row.approvalRecordId(),
                row.contractId(),
                row.stepNo(),
                row.approverRole(),
                row.decision(),
                row.decisionTime(),
                row.commentSummary(),
                readStringList(row.linkedPolicyIdsJson()),
                readStringList(row.linkedClauseChunkIdsJson()),
                readRiskItems(row.riskItemsJson()),
                row.vectorDocId()
        );
    }

    private ApprovalRecordRow toRow(ApprovalRecord record) {
        return new ApprovalRecordRow(
                record.contractId(),
                record.id(),
                record.stepNo(),
                record.approverRole(),
                record.decision(),
                record.decisionTime(),
                record.commentSummary(),
                writeJson(record.linkedPolicyIds()),
                writeJson(record.linkedClauseChunkIds()),
                writeJson(record.riskItems()),
                record.vectorDocId()
        );
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize approval string list", e);
        }
    }

    private List<RiskItem> readRiskItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<RiskItem>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize approval risk items", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize approval payload", e);
        }
    }
}
