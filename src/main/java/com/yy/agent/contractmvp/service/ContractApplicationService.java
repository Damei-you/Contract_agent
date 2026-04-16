package com.yy.agent.contractmvp.service;

import com.yy.agent.contractmvp.ai.AiContractAssistant;
import com.yy.agent.contractmvp.ai.rag.ContractVectorIngestionService;
import com.yy.agent.contractmvp.api.dto.ApprovalAssistRequest;
import com.yy.agent.contractmvp.api.dto.ApprovalAssistResponse;
import com.yy.agent.contractmvp.api.dto.ContractQaRequest;
import com.yy.agent.contractmvp.api.dto.ContractQaResponse;
import com.yy.agent.contractmvp.api.dto.ContractRiskCheckResponse;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordDto;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordsRequest;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRecordsResponse;
import com.yy.agent.contractmvp.api.dto.ImportApprovalRiskItemDto;
import com.yy.agent.contractmvp.api.dto.ImportChunkDto;
import com.yy.agent.contractmvp.api.dto.ImportContractRequest;
import com.yy.agent.contractmvp.api.dto.ImportContractResponse;
import com.yy.agent.contractmvp.domain.ApprovalDecision;
import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.domain.Contract;
import com.yy.agent.contractmvp.domain.ContractType;
import com.yy.agent.contractmvp.domain.RiskItem;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import com.yy.agent.contractmvp.repository.ContractRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 合同相关用例的应用服务层：负责「存在性校验 → 调用 AI 编排 → 导入落库」的流程串联。
 * <p>
 * 不直接操作 HTTP，由 {@link com.yy.agent.contractmvp.controller.ContractController} 调用。
 */
@Service
public class ContractApplicationService {

    private final ContractRepository contractRepository;
    private final AiContractAssistant aiContractAssistant;
    private final ObjectProvider<ContractVectorIngestionService> contractVectorIngestionService;

    /**
     * @param contractRepository   合同与条款、审批数据访问（默认 MyBatis + PostgreSQL；test 为内存 Mock）
     * @param aiContractAssistant 封装 RAG、Prompt与大模型调用的助手
     * @param contractVectorIngestionService test profile 下可能无向量 Bean，故用 ObjectProvider
     */
    public ContractApplicationService(
            ContractRepository contractRepository,
            AiContractAssistant aiContractAssistant,
            ObjectProvider<ContractVectorIngestionService> contractVectorIngestionService
    ) {
        this.contractRepository = contractRepository;
        this.aiContractAssistant = aiContractAssistant;
        this.contractVectorIngestionService = contractVectorIngestionService;
    }

    /**
     * 合同问答：校验合同存在后，交给助手完成检索与回答。
     *
     * @param contractId 合同 id
     * @param request    用户问题
     * @return 模型回答与检索条款 id
     */
    public ContractQaResponse qa(String contractId, ContractQaRequest request) {
        ensureContractExists(contractId);
        return aiContractAssistant.answerQuestion(contractId, request.question());
    }

    /**
     * 风险检查：校验合同存在后，使用固定宽检索词与历史审批摘要驱动模型输出结构化风险。
     *
     * @param contractId 合同 id
     * @return 总结与风险条目列表
     */
    public ContractRiskCheckResponse riskCheck(String contractId) {
        ensureContractExists(contractId);
        return aiContractAssistant.riskCheck(contractId);
    }

    /**
     * 审批辅助：结合角色、关注重点、条款与历史意见生成建议与清单。
     *
     * @param contractId 合同 id
     * @param request    审批角色与可选 focus
     * @return 建议与 checklist
     */
    public ApprovalAssistResponse approvalAssist(String contractId, ApprovalAssistRequest request) {
        ensureContractExists(contractId);
        return aiContractAssistant.approvalAssist(contractId, request.approverRole(), request.focus());
    }

    /**
     * 导入合同：生成或使用请求中的 id，写入主表、替换条款列表、清空审批列表（新合同无历史）。
     * id 冲突时抛出409。
     *
     * @param request 主数据与条款 DTO
     * @return 新建合同 id
     */
    public ImportContractResponse importContract(ImportContractRequest request) {
        String id = (request.id() == null || request.id().isBlank())
                ? "CTR-" + UUID.randomUUID()
                : request.id().trim();
        if (contractRepository.findById(id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contract already exists: " + id);
        }
        ContractType type = ContractType.fromFlexible(request.type());
        RiskSeverity tier = parseRiskTier(request.riskTier());
        Contract contract = new Contract(
                id,
                type,
                request.partyAName(),
                request.partyBName(),
                request.currency(),
                request.amountExTax(),
                request.taxRatePct(),
                request.amountIncTax(),
                request.signDate(),
                request.effectiveDate(),
                request.endDate(),
                request.performanceSite(),
                request.paymentTermsSummary(),
                request.businessOwnerDept(),
                tier,
                request.vectorDocId() == null || request.vectorDocId().isBlank() ? null : request.vectorDocId(),
                request.notes()
        );
        List<ClauseChunk> chunks = mapChunks(id, request.chunks());
        contractRepository.saveContractWithChunks(contract, chunks);
        contractVectorIngestionService.ifAvailable(s -> s.ingest(chunks));
        return new ImportContractResponse(id);
    }

    /**
     * 全量替换某合同审批记录，导入后风险检查/审批辅助即可读取最新审批历史。
     *
     * @param contractId 合同 id
     * @param request    审批记录列表
     * @return 导入数量
     */
    public ImportApprovalRecordsResponse importApprovalRecords(
            String contractId,
            ImportApprovalRecordsRequest request
    ) {
        ensureContractExists(contractId);
        List<ApprovalRecord> records = mapApprovalRecords(contractId, request.records());
        contractRepository.replaceApprovalRecords(contractId, records);
        return new ImportApprovalRecordsResponse(contractId, records.size());
    }

    /**
     * 若合同不存在则抛出 404，供问答/风险/审批接口复用。
     */
    private void ensureContractExists(String contractId) {
        if (contractRepository.findById(contractId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found: " + contractId);
        }
    }

    /**
     * 解析风险档位：优先按枚举名（HIGH），失败则按中文（高）。
     */
    private static RiskSeverity parseRiskTier(String value) {
        try {
            return RiskSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return RiskSeverity.fromDisplayName(value.trim());
        }
    }

    /**
     * 将导入 DTO 转为领域 {@link ClauseChunk}；未提供块 id 时按序号生成 {@code contractId-ch-n}。
     */
    private static List<ClauseChunk> mapChunks(String contractId, List<ImportChunkDto> chunks) {
        List<ClauseChunk> out = new ArrayList<>();
        int seq = 0;
        for (ImportChunkDto d : chunks) {
            seq++;
            String cid = (d.id() == null || d.id().isBlank()) ? contractId + "-ch-" + seq : d.id().trim();
            out.add(new ClauseChunk(
                    cid,
                    contractId,
                    nullToEmpty(d.clauseCode()),
                    nullToEmpty(d.clauseTitle()),
                    nullToEmpty(d.clauseCategory()),
                    "",
                    RiskSeverity.LOW,
                    "",
                    d.textForEmbedding(),
                    "",
                    ""
            ));
        }
        return out;
    }

    /**
     * 将审批导入 DTO 映射为领域记录；未传记录 id 时按步骤序号补足稳定默认值。
     */
    private static List<ApprovalRecord> mapApprovalRecords(String contractId, List<ImportApprovalRecordDto> records) {
        List<ApprovalRecord> out = new ArrayList<>();
        int seq = 0;
        for (ImportApprovalRecordDto d : records) {
            seq++;
            String id = (d.id() == null || d.id().isBlank()) ? contractId + "-ar-" + seq : d.id().trim();
            out.add(new ApprovalRecord(
                    id,
                    contractId,
                    d.stepNo(),
                    d.approverRole().trim(),
                    parseApprovalDecision(d.decision()),
                    d.decisionTime(),
                    nullToEmpty(d.commentSummary()),
                    trimList(d.linkedPolicyIds()),
                    trimList(d.linkedClauseChunkIds()),
                    mapRiskItems(d.riskItems()),
                    d.vectorDocId() == null || d.vectorDocId().isBlank() ? null : d.vectorDocId().trim()
            ));
        }
        return out;
    }

    private static List<RiskItem> mapRiskItems(List<ImportApprovalRiskItemDto> riskItems) {
        if (riskItems == null || riskItems.isEmpty()) {
            return List.of();
        }
        List<RiskItem> out = new ArrayList<>();
        for (ImportApprovalRiskItemDto d : riskItems) {
            out.add(new RiskItem(
                    d.code().trim(),
                    parseRiskTier(d.severity()),
                    nullToEmpty(d.detail()),
                    trimList(d.relatedClauseChunkIds()),
                    trimList(d.relatedPolicyIds())
            ));
        }
        return out;
    }

    /**
     * 审批结论支持英文枚举名和中文显示名。
     */
    private static ApprovalDecision parseApprovalDecision(String value) {
        try {
            return ApprovalDecision.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ApprovalDecision.fromDisplayName(value.trim());
        }
    }

    private static List<String> trimList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList();
    }

    /** null 转为空串，避免领域对象中出现 null 描述字段。 */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
