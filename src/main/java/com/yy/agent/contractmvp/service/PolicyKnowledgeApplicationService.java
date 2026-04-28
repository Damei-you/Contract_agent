package com.yy.agent.contractmvp.service;

import com.yy.agent.contractmvp.ai.rag.PolicyVectorIngestionService;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeRequest;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeResponse;
import com.yy.agent.contractmvp.api.dto.PolicyKnowledgeItemDto;
import com.yy.agent.contractmvp.domain.PolicyKnowledgeItem;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import com.yy.agent.contractmvp.repository.PolicyKnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 政策/制度知识库的应用服务层：负责 DTO 校验/规范化、按 {@code policyId} 覆盖落库，
 * 并在向量服务可用时同步写入向量库，使制度通道 RAG 立即生效。
 * <p>
 * 仓储与向量服务都通过 {@link ObjectProvider} 注入，便于在缺少 PostgreSQL 或 VectorStore 的环境下安全启动上下文。
 */
@Service
public class PolicyKnowledgeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyKnowledgeApplicationService.class);

    private final ObjectProvider<PolicyKnowledgeRepository> policyKnowledgeRepository;
    private final ObjectProvider<PolicyVectorIngestionService> policyVectorIngestionService;

    /**
     * @param policyKnowledgeRepository    制度知识库权威数据访问；test profile 缺失时为空 provider
     * @param policyVectorIngestionService 制度向量入库服务；test profile 缺失时为空 provider
     */
    public PolicyKnowledgeApplicationService(
            ObjectProvider<PolicyKnowledgeRepository> policyKnowledgeRepository,
            ObjectProvider<PolicyVectorIngestionService> policyVectorIngestionService
    ) {
        this.policyKnowledgeRepository = policyKnowledgeRepository;
        this.policyVectorIngestionService = policyVectorIngestionService;
    }

    /**
     * 批量导入政策/制度条目：按 {@code policyId} upsert 到权威表，并同步写入向量库。
     * 同一请求内 {@code policyId} 重复时只保留最后一条，避免向量库重复写入。
     *
     * @param request 导入请求
     * @return 实际写入条数与 policyId 列表（按导入顺序）
     */
    public ImportPolicyKnowledgeResponse importPolicies(ImportPolicyKnowledgeRequest request) {
        PolicyKnowledgeRepository repository = policyKnowledgeRepository.getIfAvailable();
        if (repository == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Policy knowledge repository is not available; ensure PostgreSQL and MyBatis mappers are configured."
            );
        }
        List<PolicyKnowledgeItem> items = mapPolicies(request.policies());
        List<PolicyKnowledgeItem> persisted = repository.upsertAll(items);
        List<String> policyIds = persisted.stream().map(PolicyKnowledgeItem::policyId).toList();
        String warning = ingestVectorsSafely(persisted);
        return new ImportPolicyKnowledgeResponse(persisted.size(), policyIds, warning);
    }

    /**
     * 调用向量入库服务并对异常做软降级：业务表已经成功提交，再因向量库失败让接口 500 会让客户端误判数据未落库；
     * 这里捕获异常仅记录日志并将简要描述返回给客户端，由其按需重试 {@code POST /api/policies/import}（幂等）。
     *
     * @return 失败时的 warning 文本；成功或向量服务不可用时返回 {@code null}
     */
    private String ingestVectorsSafely(List<PolicyKnowledgeItem> persisted) {
        PolicyVectorIngestionService vectorService = policyVectorIngestionService.getIfAvailable();
        if (vectorService == null) {
            log.info("Policy vector ingestion service is not available; skipping vector store sync for {} item(s).",
                    persisted.size());
            return null;
        }
        try {
            vectorService.ingest(persisted);
            return null;
        } catch (Exception ex) {
            List<String> failedIds = persisted.stream().map(PolicyKnowledgeItem::policyId).toList();
            log.error("Policy vector ingestion failed for policyIds={}; the policy_knowledge table is already updated, "
                    + "client may safely retry POST /api/policies/import to re-sync vector_store.", failedIds, ex);
            return "Vector store sync failed: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : (": " + ex.getMessage()))
                    + ". Business table updated; retry import to re-sync.";
        }
    }

    /**
     * 将导入 DTO 列表映射为领域条目，过滤 {@code policyId} 内重复并保留最后一次出现的内容。
     */
    private static List<PolicyKnowledgeItem> mapPolicies(List<PolicyKnowledgeItemDto> dtos) {
        OffsetDateTime importedAt = OffsetDateTime.now();
        // LinkedHashMap 顺序无法直接用 stream 反向覆盖，这里用列表 + Set 跟踪保证「最后一次出现获胜 + 顺序稳定」。
        List<PolicyKnowledgeItem> reverseAccumulated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = dtos.size() - 1; i >= 0; i--) {
            PolicyKnowledgeItemDto d = dtos.get(i);
            String policyId = d.policyId().trim();
            if (!seen.add(policyId)) {
                continue;
            }
            reverseAccumulated.add(toDomain(d, importedAt));
        }
        // 翻转为原始顺序输出，保留首次出现的位置。
        List<PolicyKnowledgeItem> ordered = new ArrayList<>(reverseAccumulated.size());
        for (int i = reverseAccumulated.size() - 1; i >= 0; i--) {
            ordered.add(reverseAccumulated.get(i));
        }
        return ordered;
    }

    private static PolicyKnowledgeItem toDomain(PolicyKnowledgeItemDto d, OffsetDateTime importedAt) {
        return new PolicyKnowledgeItem(
                d.policyId().trim(),
                d.policyDomain().trim(),
                d.appliesToContractType().trim(),
                parseSeverity(d.severity()),
                nullToEmpty(d.triggerKeywords()),
                nullToEmpty(d.controlObjective()),
                d.policyTextForEmbedding(),
                nullToEmpty(d.requiredEvidence()),
                nullToEmpty(d.escalationRole()),
                d.vectorDocId() == null || d.vectorDocId().isBlank() ? null : d.vectorDocId().trim(),
                d.updatedAt() == null ? importedAt : d.updatedAt()
        );
    }

    private static RiskSeverity parseSeverity(String value) {
        try {
            return RiskSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return RiskSeverity.fromDisplayName(value.trim());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
