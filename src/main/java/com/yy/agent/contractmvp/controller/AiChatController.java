package com.yy.agent.contractmvp.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 通用 AI 连通性演示：与合同业务 API 分离，便于单独验证模型配置与网络。
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final ChatClient chatClient;

    /**
     * @param chatClientBuilder Spring AI 注入的构建器，每次构建一个无状态 {@link ChatClient}
     */
    public AiChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 健康检查：返回服务名与时间戳，不调用大模型。
     *
     * @return status、service、time
     */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "contract-agent-mvp",
                "time", OffsetDateTime.now().toString()
        );
    }

    /**
     * 单次对话：固定系统提示为合同审批助手，用户消息透传至模型。
     *
     * @param request 用户消息
     * @return 仅含 {@code answer} 的 Map
     */
    @PostMapping("/chat")
    public Map<String, String> chat(@Valid @RequestBody ChatRequest request) {
        String content = chatClient.prompt()
                .system("You are an assistant for financial contract approval scenarios. Keep responses concise and practical.")
                .user(request.message())
                .call()
                .content();
        return Map.of("answer", content == null ? "" : content);
    }

    /**
     * 通用聊天请求体。
     *
     * @param message 用户输入，必填
     */
    public record ChatRequest(@NotBlank(message = "message is required") String message) {
    }
}
