package io.kelta.ai;

import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.model.AiProposal;
import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.model.Conversation;

import java.util.Map;
import java.util.UUID;

/**
 * Pre-built domain objects for tests. Use these instead of constructing
 * records manually so tests stay concise and don't break when fields change.
 */
public final class TestFixtures {

    public static final String TENANT_ID = "tenant-1";
    public static final String USER_ID = "user-1";

    private TestFixtures() {}

    public static Conversation conversation() {
        return Conversation.create(TENANT_ID, USER_ID, "Test Conversation");
    }

    public static Conversation conversation(String title) {
        return Conversation.create(TENANT_ID, USER_ID, title);
    }

    public static ChatMessage userMessage() {
        return ChatMessage.user(TENANT_ID, UUID.randomUUID(), "Hello, can you help me?");
    }

    public static ChatMessage userMessage(UUID conversationId, String content) {
        return ChatMessage.user(TENANT_ID, conversationId, content);
    }

    public static ChatMessage assistantMessage() {
        return ChatMessage.assistant(TENANT_ID, UUID.randomUUID(),
                "I'll help you with that.", null, 100, 50);
    }

    public static ChatMessage assistantMessage(UUID conversationId, String content) {
        return ChatMessage.assistant(TENANT_ID, conversationId, content, null, 100, 50);
    }

    public static AiProposal proposal() {
        return AiProposal.pending("create_collection", Map.of(
                "name", "customers",
                "fields", Map.of("name", "string", "email", "string")
        ));
    }

    public static AiConfigProperties aiConfig() {
        return new AiConfigProperties(
                new AiConfigProperties.AnthropicProperties(
                        "test-api-key",
                        "claude-sonnet-4-20250514",
                        4096,
                        0.7
                ),
                "http://localhost:8080",
                30000L
        );
    }
}
