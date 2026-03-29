package io.kelta.ai.service;

import com.anthropic.core.http.StreamResponse;
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
    public Map<String, Object> chat(String tenantId, String userId, UUID conversationId,
                                     String userMessage, String contextType, String contextId) {
        Conversation conversation = getOrCreateConversation(tenantId, userId, conversationId, userMessage);

        ChatMessage userMsg = ChatMessage.user(tenantId, conversation.id(), userMessage);
        messageRepository.save(userMsg);

        String systemPrompt = systemPromptService.buildSystemPrompt(
                tenantId, contextType, contextId);

        List<MessageParam> messages = buildMessageHistory(conversation.id(), tenantId);

        MessageCreateParams params = anthropicService.buildRequest(tenantId, systemPrompt, messages).build();
        Message response = anthropicService.sendMessage(params);

        return processResponse(tenantId, conversation, response);
    }

    /**
     * Handles a streaming chat request. Sends SSE events as Claude generates output.
     */
    public void chatStream(String tenantId, String userId, UUID conversationId,
                            String userMessage, String contextType, String contextId,
                            SseEmitter emitter) {
        try {
            Conversation conversation = getOrCreateConversation(tenantId, userId, conversationId, userMessage);

            ChatMessage userMsg = ChatMessage.user(tenantId, conversation.id(), userMessage);
            messageRepository.save(userMsg);

            String systemPrompt = systemPromptService.buildSystemPrompt(
                    tenantId, contextType, contextId);

            List<MessageParam> messages = buildMessageHistory(conversation.id(), tenantId);

            MessageCreateParams params = anthropicService.buildRequest(tenantId, systemPrompt, messages).build();

            StringBuilder fullText = new StringBuilder();
            List<AiProposal> proposals = new ArrayList<>();
            int[] tokenCounts = {0, 0}; // input, output

            // Track current tool use across stream events
            String[] currentToolName = {null};
            StringBuilder toolInputJson = new StringBuilder();

            try (StreamResponse<RawMessageStreamEvent> stream = anthropicService.streamMessage(params)) {
                stream.stream().forEach(event -> {
                    try {
                        processStreamEvent(event, emitter, fullText, proposals, tokenCounts,
                                currentToolName, toolInputJson);
                    } catch (Exception e) {
                        log.error("Error processing stream event: {}", e.getMessage());
                    }
                });
            }

            tokenTrackingService.recordUsage(tenantId, tokenCounts[0], tokenCounts[1]);

            String proposalJson = proposals.isEmpty() ? null :
                    objectMapper.writeValueAsString(proposals.getFirst());
            ChatMessage assistantMsg = ChatMessage.assistant(
                    tenantId, conversation.id(), fullText.toString(),
                    proposalJson, tokenCounts[0], tokenCounts[1]);
            messageRepository.save(assistantMsg);

            conversationRepository.updateTimestamp(conversation.id(), tenantId);

            Map<String, Object> doneData = new LinkedHashMap<>();
            doneData.put("conversationId", conversation.id().toString());
            doneData.put("tokensUsed", Map.of("input", tokenCounts[0], "output", tokenCounts[1]));
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

    @SuppressWarnings("unchecked")
    private void processStreamEvent(RawMessageStreamEvent event, SseEmitter emitter,
                                     StringBuilder fullText, List<AiProposal> proposals,
                                     int[] tokenCounts, String[] currentToolName,
                                     StringBuilder toolInputJson) throws IOException {
        // Handle content block delta (text or tool input JSON)
        event.contentBlockDelta().ifPresent(delta -> {
            // Text delta — stream to client
            delta.delta().text().ifPresent(textDelta -> {
                String text = textDelta.text();
                fullText.append(text);
                try {
                    Map<String, Object> deltaData = Map.of("text", text);
                    emitter.send(SseEmitter.event().name("delta").data(objectMapper.writeValueAsString(deltaData)));
                } catch (IOException e) {
                    log.error("Failed to send delta event: {}", e.getMessage());
                }
            });

            // Tool input JSON delta — accumulate chunks
            delta.delta().inputJson().ifPresent(inputJsonDelta -> {
                toolInputJson.append(inputJsonDelta.partialJson());
            });
        });

        // Handle content block start (tool use detection)
        event.contentBlockStart().ifPresent(blockStart -> {
            blockStart.contentBlock().toolUse().ifPresent(toolUse -> {
                currentToolName[0] = toolUse.name();
                toolInputJson.setLength(0); // Reset for new tool
                try {
                    Map<String, Object> toolData = Map.of("name", toolUse.name(), "id", toolUse.id());
                    emitter.send(SseEmitter.event().name("tool_use").data(objectMapper.writeValueAsString(toolData)));
                } catch (IOException e) {
                    log.error("Failed to send tool_use event: {}", e.getMessage());
                }
            });
        });

        // Handle content block stop — finalize tool use and emit proposal
        event.contentBlockStop().ifPresent(stop -> {
            if (currentToolName[0] != null && toolInputJson.length() > 0) {
                try {
                    Map<String, Object> toolInput = objectMapper.readValue(
                            toolInputJson.toString(), Map.class);
                    String proposalType = "propose_collection".equals(currentToolName[0])
                            ? "collection" : "layout";
                    AiProposal proposal = proposalService.createProposal(proposalType, toolInput);
                    proposals.add(proposal);

                    String proposalJson = objectMapper.writeValueAsString(proposal);
                    emitter.send(SseEmitter.event().name("proposal").data(proposalJson));
                    log.info("Emitted {} proposal: {}", proposalType, proposal.id());
                } catch (Exception e) {
                    log.error("Failed to process tool result: {}", e.getMessage());
                }
                currentToolName[0] = null;
                toolInputJson.setLength(0);
            }
        });

        // Handle message delta (output token count)
        event.messageDelta().ifPresent(msgDelta -> {
            tokenCounts[1] = (int) msgDelta.usage().outputTokens();
        });

        // Handle message start (input token count)
        event.messageStart().ifPresent(msgStart -> {
            tokenCounts[0] = (int) msgStart.message().usage().inputTokens();
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processResponse(String tenantId, Conversation conversation, Message response) {
        int inputTokens = (int) response.usage().inputTokens();
        int outputTokens = (int) response.usage().outputTokens();

        tokenTrackingService.recordUsage(tenantId, inputTokens, outputTokens);

        StringBuilder textContent = new StringBuilder();
        List<AiProposal> proposals = new ArrayList<>();

        for (ContentBlock block : response.content()) {
            block.text().ifPresent(textBlock -> textContent.append(textBlock.text()));

            block.toolUse().ifPresent(toolUse -> {
                String toolName = toolUse.name();
                // Get the raw input as a JsonValue and convert to Map
                Object rawInput = toolUse._input();
                Map<String, Object> input;
                if (rawInput instanceof Map) {
                    input = (Map<String, Object>) rawInput;
                } else {
                    input = objectMapper.convertValue(rawInput, Map.class);
                }

                String proposalType = "propose_collection".equals(toolName) ? "collection" : "layout";
                AiProposal proposal = proposalService.createProposal(proposalType, input);
                proposals.add(proposal);
            });
        }

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

        conversationRepository.updateTimestamp(conversation.id(), tenantId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversation.id().toString());
        result.put("content", textContent.toString());
        result.put("proposals", proposals);
        result.put("tokensUsed", Map.of("input", inputTokens, "output", outputTokens));

        return result;
    }

    private Conversation getOrCreateConversation(String tenantId, String userId,
                                                   UUID conversationId, String userMessage) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        }

        String title = userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage;
        Conversation conversation = Conversation.create(tenantId, userId, title);
        conversationRepository.save(conversation);
        return conversation;
    }

    private List<MessageParam> buildMessageHistory(UUID conversationId, String tenantId) {
        List<ChatMessage> history = messageRepository.findByConversation(conversationId, tenantId);
        List<MessageParam> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            if ("system".equals(msg.role())) continue;

            MessageParam.Role role = "user".equals(msg.role())
                    ? MessageParam.Role.USER
                    : MessageParam.Role.ASSISTANT;

            messages.add(MessageParam.builder()
                    .role(role)
                    .content(msg.content())
                    .build());
        }
        return messages;
    }
}
