package com.yy.agent.contractmvp.ai.tool;

import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.domain.Contract;
import com.yy.agent.contractmvp.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * 面向模型/编排层的「工具」实现：从 {@link ContractRepository} 读取事实并格式化为 Prompt 可用文本。
 * <p>
 * 当前由 {@link com.yy.agent.contractmvp.ai.AiContractAssistant} 直接调用；后续可改为
 * Spring AI Function Calling，使模型主动发起相同能力的函数调用。
 */
@Component
public class ContractToolExecutor {

    private final ContractRepository contractRepository;

    /**
     * @param contractRepository 合同数据访问
     */
    public ContractToolExecutor(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /**
     * 加载合同，不存在则抛出 404（与 Web 层一致，便于排错）。
     *
     * @param contractId 合同 id
     * @return 领域合同对象
     */
    public Contract requireContract(String contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found: " + contractId));
    }

    /**
     * 生成供 Prompt 使用的合同主数据一行摘要（非条款全文、非向量块）。
     *
     * @param contractId 合同 id
     * @return 中文摘要字符串
     */
    public String contractSummary(String contractId) {
        Contract c = requireContract(contractId);
        return """
                合同编号：%s；类型：%s；甲方：%s；乙方：%s；币种：%s；不含税金额：%s；税率(%%)：%s；含税金额：%s；
                签订日：%s；生效日：%s；结束日：%s；履约地点：%s；付款摘要：%s；主办部门：%s；风险分层：%s；备注：%s
                """.formatted(
                c.id(),
                c.type().displayName(),
                c.partyAName(),
                c.partyBName(),
                c.currency(),
                c.amountExTax().toPlainString(),
                c.taxRatePct().toPlainString(),
                c.amountIncTax().toPlainString(),
                c.signDate(),
                c.effectiveDate(),
                c.endDate(),
                c.performanceSite(),
                c.paymentTermsSummary(),
                c.businessOwnerDept(),
                c.riskTier().displayName(),
                c.notes()
        ).trim();
    }

    /**
     * 按条款块 id 取正文，用于未来函数调用或人工钻取单条。
     *
     * @param contractId 合同 id
     * @param chunkId    条款块 id
     * @return 正文 Optional
     */
    public Optional<String> clauseText(String contractId, String chunkId) {
        return contractRepository.findChunk(contractId, chunkId).map(ClauseChunk::textForEmbedding);
    }

    /**
     * 将历史审批记录压缩为多行文本（步骤、角色、结论、意见），供风险与审批 Prompt 引用。
     *
     * @param contractId 合同 id
     * @return 无记录时返回固定提示语
     */
    public String approvalHistoryDigest(String contractId) {
        List<ApprovalRecord> records = contractRepository.findApprovalRecordsByContractId(contractId);
        if (records.isEmpty()) {
            return "（暂无审批记录）";
        }
        StringBuilder sb = new StringBuilder();
        for (ApprovalRecord r : records) {
            sb.append("- 步骤")
                    .append(r.stepNo())
                    .append(" ")
                    .append(r.approverRole())
                    .append(" ")
                    .append(r.decision().displayName())
                    .append("：")
                    .append(r.commentSummary())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
