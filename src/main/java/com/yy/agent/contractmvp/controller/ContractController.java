package com.yy.agent.contractmvp.controller;

import com.yy.agent.contractmvp.api.dto.ApprovalAssistRequest;
import com.yy.agent.contractmvp.api.dto.ApprovalAssistResponse;
import com.yy.agent.contractmvp.api.dto.ContractQaRequest;
import com.yy.agent.contractmvp.api.dto.ContractQaResponse;
import com.yy.agent.contractmvp.api.dto.ContractRiskCheckResponse;
import com.yy.agent.contractmvp.api.dto.ImportContractRequest;
import com.yy.agent.contractmvp.api.dto.ImportContractResponse;
import com.yy.agent.contractmvp.service.ContractApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同 Agent 演示 API：将 HTTP 请求委托给 {@link ContractApplicationService}，自身不做业务规则。
 * <p>
 * 路径前缀：{@code /api/contracts}。
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractApplicationService contractApplicationService;

    /**
     * @param contractApplicationService 应用服务（编排仓储与 AI）
     */
    public ContractController(ContractApplicationService contractApplicationService) {
        this.contractApplicationService = contractApplicationService;
    }

    /**
     * 基于合同条款检索与用户问题，返回模型回答及命中的条款块 id列表。
     *
     * @param id      合同 id
     * @param request 含 {@code question}
     * @return 回答与检索到的条款块 id
     */
    @PostMapping("/{id}/qa")
    public ContractQaResponse qa(@PathVariable("id") String id, @Valid @RequestBody ContractQaRequest request) {
        return contractApplicationService.qa(id, request);
    }

    /**
     * 对指定合同执行风险扫描（RAG + 历史审批摘要 + 模型结构化输出）。
     *
     * @param id 合同 id
     * @return 风险总结与 {@link com.yy.agent.contractmvp.domain.RiskItem} 列表
     */
    @PostMapping("/{id}/risk-check")
    public ContractRiskCheckResponse riskCheck(@PathVariable("id") String id) {
        return contractApplicationService.riskCheck(id);
    }

    /**
     * 按当前审批人角色与关注重点，生成审批建议与核对清单（JSON 解析为 DTO）。
     *
     * @param id      合同 id
     * @param request 审批角色、可选关注重点
     * @return 建议与 checklist 字符串列表
     */
    @PostMapping("/{id}/approval-assist")
    public ApprovalAssistResponse approvalAssist(
            @PathVariable("id") String id,
            @Valid @RequestBody ApprovalAssistRequest request
    ) {
        return contractApplicationService.approvalAssist(id, request);
    }

    /**
     * 导入新合同及条款块；若请求未带 id 则服务端生成。id 已存在时返回 409。
     *
     * @param request 合同主数据与可选条款列表
     * @return 落库后的合同 id
     */
    @PostMapping("/import")
    public ImportContractResponse importContract(@Valid @RequestBody ImportContractRequest request) {
        return contractApplicationService.importContract(request);
    }
}
