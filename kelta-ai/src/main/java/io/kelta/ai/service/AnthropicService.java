package io.kelta.ai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.*;
import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.repository.AiConfigRepository;
import io.kelta.ai.service.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Wraps the Anthropic Java SDK for sending messages to Claude.
 * Resolves model selection from tenant config with fallback chain.
 * Tool definitions are supplied by {@link ToolRegistry}.
 */
@Service
public class AnthropicService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicService.class);

    private final AnthropicClient client;
    private final AiConfigProperties config;
    private final AiConfigRepository aiConfigRepository;
    private final ToolRegistry toolRegistry;

    public AnthropicService(AnthropicClient client, AiConfigProperties config,
                             AiConfigRepository aiConfigRepository, ToolRegistry toolRegistry) {
        this.client = client;
        this.config = config;
        this.aiConfigRepository = aiConfigRepository;
        this.toolRegistry = toolRegistry;
    }

    public MessageCreateParams.Builder buildRequest(String tenantId, String systemPrompt,
                                                      List<MessageParam> messages) {
        String model = resolveModel(tenantId);
        int maxTokens = resolveMaxTokens(tenantId);

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens((long) maxTokens)
                .system(systemPrompt);

        for (MessageParam msg : messages) {
            builder.addMessage(msg);
        }

        for (ToolUnion tool : toolRegistry.toolDefinitions()) {
            builder.addTool(tool);
        }

        return builder;
    }

    public Message sendMessage(MessageCreateParams params) {
        log.debug("Sending message to Anthropic with model {}", params.model());
        return client.messages().create(params);
    }

    public StreamResponse<RawMessageStreamEvent> streamMessage(MessageCreateParams params) {
        log.debug("Streaming message from Anthropic with model {}", params.model());
        return client.messages().createStreaming(params);
    }

    private String resolveModel(String tenantId) {
        return aiConfigRepository.getConfig(tenantId, "anthropic.model")
                .or(() -> aiConfigRepository.getConfig("0", "anthropic.model"))
                .orElse(config.anthropic().defaultModel());
    }

    private int resolveMaxTokens(String tenantId) {
        return aiConfigRepository.getConfig(tenantId, "anthropic.maxTokens")
                .map(Integer::parseInt)
                .or(() -> aiConfigRepository.getConfig("0", "anthropic.maxTokens").map(Integer::parseInt))
                .orElse(config.anthropic().defaultMaxTokens());
    }
}
