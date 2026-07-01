package com.yy.agent.contractmvp.controller;

import com.yy.agent.contractmvp.ai.AiContractAssistant;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeRequest;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeResponse;
import com.yy.agent.contractmvp.api.dto.ParsePolicyKnowledgeFileResponse;
import com.yy.agent.contractmvp.api.dto.PolicyKnowledgeDetailResponse;
import com.yy.agent.contractmvp.api.dto.PolicyQaRequest;
import com.yy.agent.contractmvp.api.dto.PolicyQaResponse;
import com.yy.agent.contractmvp.service.DocumentParseApplicationService;
import com.yy.agent.contractmvp.service.PolicyKnowledgeApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 政策/制度知识库 API：将 HTTP 请求委托给 {@link PolicyKnowledgeApplicationService}，自身不做业务规则。
 * <p>
 * 路径前缀：{@code /api/policies}。
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyKnowledgeApplicationService policyKnowledgeApplicationService;
    private final DocumentParseApplicationService documentParseApplicationService;
    private final AiContractAssistant aiContractAssistant;

    /**
     * @param policyKnowledgeApplicationService 政策/制度应用服务（编排仓储与向量入库）
     */
    public PolicyController(
            PolicyKnowledgeApplicationService policyKnowledgeApplicationService,
            DocumentParseApplicationService documentParseApplicationService,
            AiContractAssistant aiContractAssistant
    ) {
        this.policyKnowledgeApplicationService = policyKnowledgeApplicationService;
        this.documentParseApplicationService = documentParseApplicationService;
        this.aiContractAssistant = aiContractAssistant;
    }

    /**
     * 批量导入政策/制度条目：按 {@code policyId} 覆盖更新权威表并同步写入向量库。
     *
     * @param request 制度条目列表
     * @return 实际写入条数与 policyId 列表（按导入顺序，去重后）
     */
    @PostMapping("/import")
    public ImportPolicyKnowledgeResponse importPolicies(@Valid @RequestBody ImportPolicyKnowledgeRequest request) {
        return policyKnowledgeApplicationService.importPolicies(request);
    }

    /**
     * 政策/制度问答：基于制度知识库检索结果回答，不依赖具体合同。
     *
     * @param request 用户问题与可选合同类型范围
     * @return 回答、命中的制度依据 id 与 AgentTrace
     */
    @PostMapping("/qa")
    public PolicyQaResponse qa(@Valid @RequestBody PolicyQaRequest request) {
        try {
            return aiContractAssistant.answerPolicyQuestion(request.question(), request.contractType());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 查询指定制度依据详情，供 AgentTrace 溯源面板使用。
     *
     * @param policyId 制度条目主键
     * @return 制度依据详情
     */
    @GetMapping("/{policyId}")
    public PolicyKnowledgeDetailResponse getPolicy(@PathVariable("policyId") String policyId) {
        return policyKnowledgeApplicationService.getPolicy(policyId);
    }

    /**
     * 上传制度文件并解析为可编辑导入草稿；不直接落库，草稿确认后复用 {@code POST /api/policies/import}。
     *
     * @param file PDF、Word 或文本文件
     * @return 制度知识库导入草稿与解析元数据
     */
    @PostMapping(value = "/parse-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsePolicyKnowledgeFileResponse parsePolicyKnowledgeFile(@RequestPart("file") MultipartFile file) {
        return documentParseApplicationService.parsePolicyKnowledgeFile(file);
    }
}
