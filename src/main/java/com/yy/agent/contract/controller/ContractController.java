package com.yy.agent.contract.controller;

import com.yy.agent.contract.api.dto.ApprovalAssistRequest;
import com.yy.agent.contract.api.dto.ApprovalAssistResponse;
import com.yy.agent.contract.api.dto.ContractClauseChunkResponse;
import com.yy.agent.contract.api.dto.ContractQaRequest;
import com.yy.agent.contract.api.dto.ContractQaResponse;
import com.yy.agent.contract.api.dto.ContractRiskCheckResponse;
import com.yy.agent.contract.api.dto.ContractListItemResponse;
import com.yy.agent.contract.api.dto.ImportApprovalRecordsRequest;
import com.yy.agent.contract.api.dto.ImportApprovalRecordsResponse;
import com.yy.agent.contract.api.dto.ImportContractRequest;
import com.yy.agent.contract.api.dto.ImportContractResponse;
import com.yy.agent.contract.api.dto.ParseContractFileResponse;
import com.yy.agent.contract.service.ContractApplicationService;
import com.yy.agent.contract.service.DocumentParseApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 合同 Agent 演示 API：将 HTTP 请求委托给 {@link ContractApplicationService}，自身不做业务规则。
 * <p>
 * 路径前缀：{@code /api/contracts}。
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractApplicationService contractApplicationService;
    private final DocumentParseApplicationService documentParseApplicationService;

    /**
     * @param contractApplicationService 应用服务（编排仓储与 AI）
     */
    public ContractController(
            ContractApplicationService contractApplicationService,
            DocumentParseApplicationService documentParseApplicationService
    ) {
        this.contractApplicationService = contractApplicationService;
        this.documentParseApplicationService = documentParseApplicationService;
    }

    /**
     * 查询可选择的全部合同。
     *
     * @return 按合同 id 排序的精简合同列表
     */
    @GetMapping
    public List<ContractListItemResponse> listContracts() {
        return contractApplicationService.listContracts();
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
     * @return 风险总结与 {@link com.yy.agent.contract.domain.RiskItem} 列表
     */
    @PostMapping("/{id}/risk-check")
    public ContractRiskCheckResponse riskCheck(@PathVariable("id") String id) {
        return contractApplicationService.riskCheck(id);
    }

    /**
     * 查询指定合同条款块详情，供风险检查/审批辅助的 AgentTrace 溯源面板使用。
     *
     * @param id      合同 id
     * @param chunkId 条款块 id
     * @return 条款块详情
     */
    @GetMapping("/{id}/chunks/{chunkId}")
    public ContractClauseChunkResponse getChunk(
            @PathVariable("id") String id,
            @PathVariable("chunkId") String chunkId
    ) {
        return contractApplicationService.getChunk(id, chunkId);
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
     * 导入合同及条款块；若请求未带 id 则服务端生成。id 已存在时先返回覆盖确认提示。
     *
     * @param request 合同主数据、覆盖确认标记与可选条款列表
     * @return 导入结果或覆盖确认提示
     */
    @PostMapping("/import")
    public ImportContractResponse importContract(@Valid @RequestBody ImportContractRequest request) {
        return contractApplicationService.importContract(request);
    }

    /**
     * 上传合同文件并解析为可编辑导入草稿；不直接落库，草稿确认后复用 {@code POST /api/contracts/import}。
     *
     * @param file PDF、Word 或文本文件
     * @return 合同导入草稿与解析元数据
     */
    @PostMapping(value = "/parse-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParseContractFileResponse parseContractFile(@RequestPart("file") MultipartFile file) {
        return documentParseApplicationService.parseContractFile(file);
    }

    /**
     * 全量替换某合同的审批记录，供审批历史补录或批量导入使用。
     *
     * @param id      合同 id
     * @param request 审批记录列表
     * @return 导入结果
     */
    @PostMapping("/{id}/approval-records/import")
    public ImportApprovalRecordsResponse importApprovalRecords(
            @PathVariable("id") String id,
            @Valid @RequestBody ImportApprovalRecordsRequest request
    ) {
        return contractApplicationService.importApprovalRecords(id, request);
    }
}
