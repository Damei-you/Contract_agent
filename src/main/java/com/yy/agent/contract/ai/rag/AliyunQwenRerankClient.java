package com.yy.agent.contract.ai.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 阿里云百炼 qwen3-rerank 文本排序客户端。
 */
@Component
@Profile("!test")
class AliyunQwenRerankClient implements RerankModelClient {

    private static final Logger log = LoggerFactory.getLogger(AliyunQwenRerankClient.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final String instruct;
    private final int maxDocuments;
    private final int maxDocumentChars;

    AliyunQwenRerankClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.rag.rerank.enabled:false}") boolean enabled,
            @Value("${app.rag.rerank.endpoint:}") String configuredEndpoint,
            @Value("${app.rag.rerank.workspace-id:}") String workspaceId,
            @Value("${app.rag.rerank.region:cn-beijing}") String region,
            @Value("${app.rag.rerank.api-key:}") String apiKey,
            @Value("${app.rag.rerank.model:qwen3-rerank}") String model,
            @Value("${app.rag.rerank.instruct:Given a web search query, retrieve relevant passages that answer the query.}") String instruct,
            @Value("${app.rag.rerank.max-documents:40}") int maxDocuments,
            @Value("${app.rag.rerank.max-document-chars:3000}") int maxDocumentChars,
            @Value("${app.rag.rerank.timeout-ms:5000}") int timeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.enabled = enabled;
        this.endpoint = resolveEndpoint(configuredEndpoint, workspaceId, region);
        this.apiKey = blankToEmpty(apiKey);
        this.model = StringUtils.hasText(model) ? model.trim() : "qwen3-rerank";
        this.instruct = blankToEmpty(instruct);
        this.maxDocuments = Math.max(1, maxDocuments);
        this.maxDocumentChars = Math.max(200, maxDocumentChars);
    }

    @Override
    public boolean isAvailable() {
        return enabled && StringUtils.hasText(endpoint) && StringUtils.hasText(apiKey);
    }

    @Override
    public List<RerankScore> rerank(String query, List<String> documents) {
        if (!isAvailable() || !StringUtils.hasText(query) || documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<String> normalizedDocuments = documents.stream()
                .limit(maxDocuments)
                .map(this::clip)
                .toList();
        Qwen3RerankResponse response = restClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Qwen3RerankRequest(
                        model,
                        normalizedDocuments,
                        query,
                        normalizedDocuments.size(),
                        instruct
                ))
                .retrieve()
                .body(Qwen3RerankResponse.class);
        if (response == null || response.results() == null) {
            return List.of();
        }
        if (StringUtils.hasText(response.code())) {
            log.warn("Aliyun qwen3-rerank returned {}: {}", response.code(), response.message());
            return List.of();
        }
        return response.results().stream()
                .filter(r -> r.relevanceScore() != null)
                .map(r -> new RerankScore(r.index(), r.relevanceScore()))
                .toList();
    }

    private String clip(String document) {
        String text = blankToEmpty(document);
        if (text.length() <= maxDocumentChars) {
            return text;
        }
        return text.substring(0, maxDocumentChars);
    }

    private static String resolveEndpoint(String configuredEndpoint, String workspaceId, String region) {
        if (StringUtils.hasText(configuredEndpoint)) {
            return configuredEndpoint.trim();
        }
        if (!StringUtils.hasText(workspaceId)) {
            return "";
        }
        String safeRegion = StringUtils.hasText(region) ? region.trim() : "cn-beijing";
        return "https://%s.%s.maas.aliyuncs.com/compatible-api/v1/reranks"
                .formatted(workspaceId.trim(), safeRegion);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record Qwen3RerankRequest(
            String model,
            List<String> documents,
            String query,
            @JsonProperty("top_n") int topN,
            String instruct
    ) {
    }

    private record Qwen3RerankResponse(
            List<Result> results,
            String model,
            String id,
            Usage usage,
            String code,
            String message
    ) {
    }

    private record Result(
            int index,
            @JsonProperty("relevance_score") Double relevanceScore
    ) {
    }

    private record Usage(@JsonProperty("total_tokens") Integer totalTokens) {
    }
}
