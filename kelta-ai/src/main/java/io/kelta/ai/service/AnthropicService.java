package io.kelta.ai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.*;
import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.repository.AiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Wraps the Anthropic Java SDK for sending messages to Claude.
 * Resolves model selection from tenant config with fallback chain.
 */
@Service
public class AnthropicService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicService.class);

    private final AnthropicClient client;
    private final AiConfigProperties config;
    private final AiConfigRepository aiConfigRepository;

    public AnthropicService(AnthropicClient client, AiConfigProperties config,
                             AiConfigRepository aiConfigRepository) {
        this.client = client;
        this.config = config;
        this.aiConfigRepository = aiConfigRepository;
    }

    public MessageCreateParams.Builder buildRequest(String tenantId, String systemPrompt,
                                                      List<MessageParam> messages) {
        String model = resolveModel(tenantId);
        int maxTokens = resolveMaxTokens(tenantId);

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens((long) maxTokens)
                .system(systemPrompt);

        // Add messages
        for (MessageParam msg : messages) {
            builder.addMessage(msg);
        }

        // Add tool definitions
        for (ToolUnion tool : getToolDefinitions()) {
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

    private List<ToolUnion> getToolDefinitions() {
        Tool collectionTool = Tool.builder()
                .name("propose_collection")
                .description("Propose creating a new collection (data model) with fields. " +
                        "Use this when the user wants to create a new collection or entity type.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(com.anthropic.core.JsonValue.from(getCollectionToolSchema()))
                        .build())
                .build();

        Tool layoutTool = Tool.builder()
                .name("propose_layout")
                .description("Propose creating a page layout for a collection. " +
                        "Use this when the user wants to create or customize how a collection's records are displayed.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(com.anthropic.core.JsonValue.from(getLayoutToolSchema()))
                        .build())
                .build();

        return List.of(
                ToolUnion.ofTool(collectionTool),
                ToolUnion.ofTool(layoutTool)
        );
    }

    private Object getCollectionToolSchema() {
        return Map.of(
                "name", Map.of("type", "string", "description", "Collection name (lowercase, alphanumeric, underscores)"),
                "displayName", Map.of("type", "string", "description", "Human-readable collection name"),
                "description", Map.of("type", "string", "description", "Collection description"),
                "displayFieldName", Map.of("type", "string", "description", "Name of the field used as record label"),
                "fields", Map.of(
                        "type", "array",
                        "description", "Fields for the collection",
                        "items", Map.of("type", "object")
                )
        );
    }

    private Object getLayoutToolSchema() {
        return Map.of(
                "collectionName", Map.of("type", "string", "description", "Name of the collection this layout is for"),
                "name", Map.of("type", "string", "description", "Layout name"),
                "layoutType", Map.of("type", "string", "description", "Layout type: DETAIL, EDIT, MINI, or LIST"),
                "sections", Map.of(
                        "type", "array",
                        "description", "Layout sections with field placements",
                        "items", Map.of("type", "object")
                ),
                "relatedLists", Map.of(
                        "type", "array",
                        "description", "Related list configurations",
                        "items", Map.of("type", "object")
                )
        );
    }
}
