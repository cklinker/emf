package io.kelta.ai.service;

import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.ai.model.AiProposal;
import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.model.Conversation;
import io.kelta.ai.repository.ChatMessageRepository;
import io.kelta.ai.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the AI chat flow: builds context, sends to Anthropic,
 * processes tool calls, persists messages, and streams responses.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final AnthropicService anthropicService;
    private final SystemPromptService systemPromptService;
    private final ProposalService proposalService;
    private final TokenTrackingService tokenTrackingService;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public ChatService(AnthropicService anthropicService, SystemPromptService systemPromptService,
                        ProposalService proposalService, TokenTrackingService tokenTrackingService,
                        ConversationRepository conversationRepository, ChatMessageRepository messageRepository,
                        ObjectMapper objectMapper) {
        this.anthropicService = anthropicService;
        this.systemPromptService = systemPromptService;
        this.proposalService = proposalService;
        this.tokenTrackingService = tokenTrackingService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles a synchronous chat request. Returns the full response.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(long tenantId, String userId, UUID conversationId,
                                     String userMessage, String contextType, String contextId) {
        // Get or create conversation
        Conversation conversation = getOrCreateConversation(tenantId, userId, conversationId, userMessage);

        // Save user message
        ChatMessage userMsg = ChatMessage.user(tenantId, conversation.id(), userMessage);
        messageRepository.save(userMsg);

        // Build system prompt with dynamic context
        String systemPrompt = systemPromptService.buildSystemPrompt(
                String.valueOf(tenantId), contextType, contextId);

        // Build message history
        List<MessageParam> messages = buildMessageHistory(conversation.id(), tenantId);

        // Send to Anthropic
        MessageCreateParams params = anthropicService.buildRequest(tenantId, systemPrompt, messages).build();
        Message response = anthropicService.sendMessage(params);

        // Process the response
        return processResponse(tenantId, conversation, response);
    }

    /**
     * Handles a streaming chat request. Sends SSE events as Claude generates output.
     */
    public void chatStream(long tenantId, String userId, UUID conversationId,
                            String userMessage, String contextType, String contextId,
                            SseEmitter emitter) {
        try {
            // Get or create conversation
            Conversation conversation = getOrCreateConversation(tenantId, userId, conversationId, userMessage);

            // Save user message
            ChatMessage userMsg = ChatMessage.user(tenantId, conversation.id(), userMessage);
            messageRepository.save(userMsg);

            // Build system prompt
            String systemPrompt = systemPromptService.buildSystemPrompt(
                    String.valueOf(tenantId), contextType, contextId);

            // Build message history
            List<MessageParam> messages = buildMessageHistory(conversation.id(), tenantId);

            // Send to Anthropic with streaming
            MessageCreateParams params = anthropicService.buildRequest(tenantId, systemPrompt, messages).build();

            StringBuilder fullText = new StringBuilder();
            List<AiProposal> proposals = new ArrayList<>();
            int[] tokenCounts = {0, 0}; // input, output

            try (MessageStreamResponse stream = anthropicService.streamMessage(params)) {
                stream.stream().forEach(event -> {
                    try {
                        processStreamEvent(event, emitter, fullText, proposals, tokenCounts);
                    } catch (Exception e) {
                        log.error("Error processing stream event: {}", e.getMessage());
                    }
                });
            }

            // Record token usage
            tokenTrackingService.recordUsage(tenantId, tokenCounts[0], tokenCounts[1]);

            // Save assistant message
            String proposalJson = proposals.isEmpty() ? null :
                    objectMapper.writeValueAsString(proposals.getFirst());
            ChatMessage assistantMsg = ChatMessage.assistant(
                    tenantId, conversation.id(), fullText.toString(),
                    proposalJson, tokenCounts[0], tokenCounts[1]);
            messageRepository.save(assistantMsg);

            // Update conversation timestamp
            conversationRepository.updateTimestamp(conversation.id(), tenantId);

            // Send done event
            Map<String, Object> doneData = new LinkedHashMap<>();
            doneData.put("conversationId", conversation.id().toString());
            doneData.put("tokensUsed", Map.of(
                    "input", tokenCounts[0],
                    "output", tokenCounts[1]
            ));
            emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(doneData)));
            emitter.complete();

        } catch (Exception e) {
            log.error("Error in chat stream: {}", e.getMessage(), e);
            try {
                Map<String, Object> errorData = Map.of("code", "AI_PROVIDER_ERROR", "message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(errorData)));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                log.error("Failed to send error event: {}", ex.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    private void processStreamEvent(RawMessageStreamEvent event, SseEmitter emitter,
                                     StringBuilder fullText, List<AiProposal> proposals,
                                     int[] tokenCounts) throws IOException {
        if (event.isContentBlockDelta()) {
            ContentBlockDeltaEvent delta = event.asContentBlockDelta();
            if (delta.delta().isTextDelta()) {
                String text = delta.delta().asTextDelta().text();
                fullText.append(text);
                Map<String, Object> deltaData = Map.of("text", text);
                emitter.send(SseEmitter.event().name("delta").data(objectMapper.writeValueAsString(deltaData)));
            }
        } else if (event.isContentBlockStart()) {
            ContentBlockStartEvent blockStart = event.asContentBlockStart();
            if (blockStart.contentBlock().isToolUse()) {
                ToolUseBlock toolUse = blockStart.contentBlock().asToolUse();
                Map<String, Object> toolData = Map.of(
                        "name", toolUse.name(),
                        "id", toolUse.id()
                );
                emitter.send(SseEmitter.event().name("tool_use").data(objectMapper.writeValueAsString(toolData)));
            }
        } else if (event.isMessageDelta()) {
            MessageDeltaEvent msgDelta = event.asMessageDelta();
            tokenCounts[1] = (int) msgDelta.usage().outputTokens();
        } else if (event.isMessageStart()) {
            MessageStartEvent msgStart = event.asMessageStart();
            tokenCounts[0] = (int) msgStart.message().usage().inputTokens();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processResponse(long tenantId, Conversation conversation, Message response) {
        int inputTokens = (int) response.usage().inputTokens();
        int outputTokens = (int) response.usage().outputTokens();

        // Record token usage
        tokenTrackingService.recordUsage(tenantId, inputTokens, outputTokens);

        // Extract content
        StringBuilder textContent = new StringBuilder();
        List<AiProposal> proposals = new ArrayList<>();

        for (ContentBlock block : response.content()) {
            if (block.isText()) {
                textContent.append(block.asText().text());
            } else if (block.isToolUse()) {
                ToolUseBlock toolUse = block.asToolUse();
                String toolName = toolUse.name();
                Map<String, Object> input = (Map<String, Object>) toolUse.input();

                String proposalType = "propose_collection".equals(toolName) ? "collection" : "layout";
                AiProposal proposal = proposalService.createProposal(proposalType, input);
                proposals.add(proposal);
            }
        }

        // Save assistant message
        String proposalJson = null;
        try {
            proposalJson = proposals.isEmpty() ? null :
                    objectMapper.writeValueAsString(proposals.getFirst());
        } catch (Exception e) {
            log.error("Failed to serialize proposal: {}", e.getMessage());
        }

        ChatMessage assistantMsg = ChatMessage.assistant(
                tenantId, conversation.id(), textContent.toString(),
                proposalJson, inputTokens, outputTokens);
        messageRepository.save(assistantMsg);

        // Update conversation timestamp
        conversationRepository.updateTimestamp(conversation.id(), tenantId);

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversation.id().toString());
        result.put("content", textContent.toString());
        result.put("proposals", proposals);
        result.put("tokensUsed", Map.of("input", inputTokens, "output", outputTokens));

        return result;
    }

    private Conversation getOrCreateConversation(long tenantId, String userId,
                                                   UUID conversationId, String userMessage) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        }

        // Create new conversation with first message as title
        String title = userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage;
        Conversation conversation = Conversation.create(tenantId, userId, title);
        conversationRepository.save(conversation);
        return conversation;
    }

    private List<MessageParam> buildMessageHistory(UUID conversationId, long tenantId) {
        List<ChatMessage> history = messageRepository.findByConversation(conversationId, tenantId);
        return history.stream()
                .filter(msg -> !"system".equals(msg.role()))
                .map(msg -> MessageParam.builder()
                        .role(MessageParam.Role.valueOf(msg.role().toUpperCase()))
                        .content(msg.content())
                        .build())
                .collect(Collectors.toList());
    }
}
