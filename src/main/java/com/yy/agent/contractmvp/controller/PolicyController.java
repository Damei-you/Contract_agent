package com.yy.agent.contractmvp.controller;

import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeRequest;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeResponse;
import com.yy.agent.contractmvp.service.PolicyKnowledgeApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 政策/制度知识库 API：将 HTTP 请求委托给 {@link PolicyKnowledgeApplicationService}，自身不做业务规则。
 * <p>
 * 路径前缀：{@code /api/policies}。
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyKnowledgeApplicationService policyKnowledgeApplicationService;

    /**
     * @param policyKnowledgeApplicationService 政策/制度应用服务（编排仓储与向量入库）
     */
    public PolicyController(PolicyKnowledgeApplicationService policyKnowledgeApplicationService) {
        this.policyKnowledgeApplicationService = policyKnowledgeApplicationService;
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
}
