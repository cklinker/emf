package io.kelta.ai.service;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.*;
import io.kelta.ai.model.AiProposal;
import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.model.Conversation;
import io.kelta.ai.repository.ChatMessageRepository;
import io.kelta.ai.repository.ConversationRepository;
import io.kelta.ai.service.tools.DispatchResult;
import io.kelta.ai.service.tools.ToolDispatcher;
import io.kelta.ai.service.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the AI chat flow: builds context, drives Anthropic's multi-turn
 * tool-use loop with real tool_result content blocks, persists each turn, and
 * streams results to the client.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_TOOL_ITERATIONS = 8;
    private static final long TOKEN_BUDGET = 100_000L;

    private final AnthropicService anthropicService;
    private final SystemPromptService systemPromptService;
    private final ToolDispatcher toolDispatcher;
    private final ToolRegistry toolRegistry;
    private final TokenTrackingService tokenTrackingService;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public ChatService(AnthropicService anthropicService,
                        SystemPromptService systemPromptService,
                        ToolDispatcher toolDispatcher,
                        ToolRegistry toolRegistry,
                        TokenTrackingService tokenTrackingService,
                        ConversationRepository conversationRepository,
                        ChatMessageRepository messageRepository,
                        ObjectMapper objectMapper) {
        this.anthropicService = anthropicService;
        this.systemPromptService = systemPromptService;
        this.toolDispatcher = toolDispatcher;
        this.toolRegistry = toolRegistry;
        this.tokenTrackingService = tokenTrackingService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> chat(String tenantId, String userId, UUID conversationId,
                                     String userMessage, String contextType, String contextId) {
        Conversation conversation = getOrCreateConversation(tenantId, userId, conversationId, userMessage);
        messageRepository.save(ChatMessage.user(tenantId, conversation.id(), userMessage));

        String systemPrompt = systemPromptService.buildSystemPrompt(tenantId, contextType, contextId);
        List<MessageParam> conversationParams = new ArrayList<>(buildMessageHistory(conversation.id(), tenantId));

        List<AiProposal> proposals = new ArrayList<>();
        int[] tokenCounts = {0, 0};
        StringBuilder lastText = new StringBuilder();

        runSyncToolLoop(tenantId, userId, conversation, systemPrompt, conversationParams,
                proposals, tokenCounts, lastText);

        conversationRepository.updateTimestamp(conversation.id(), tenantId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversation.id().toString());
        result.put("content", lastText.toString());
        result.put("proposals", proposals);
        result.put("tokensUsed", Map.of("input", tokenCounts[0], "output", tokenCounts[1]));
        return result;
    }

    public void chatStream(String tenantId, String userId, UUID conversationId,
                            String userMessage, String contextType, String contextId,
                            SseEmitter emitter) {
        try {
            Conversation conversation = getOrCreateConversation(tenantId, userId, conversationId, userMessage);
            messageRepository.save(ChatMessage.user(tenantId, conversation.id(), userMessage));

            String systemPrompt = systemPromptService.buildSystemPrompt(tenantId, contextType, contextId);
            List<MessageParam> conversationParams = new ArrayList<>(buildMessageHistory(conversation.id(), tenantId));

            List<AiProposal> proposals = new ArrayList<>();
            int[] tokenCounts = {0, 0};

            runStreamingToolLoop(tenantId, userId, conversation, systemPrompt, conversationParams,
                    emitter, proposals, tokenCounts);

            conversationRepository.updateTimestamp(conversation.id(), tenantId);

            Map<String, Object> doneData = new LinkedHashMap<>();
            doneData.put("conversationId", conversation.id().toString());
            doneData.put("tokensUsed", Map.of("input", tokenCounts[0], "output", tokenCounts[1]));
            emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(doneData)));
            emitter.complete();
        } catch (Exception e) {
            log.error("Error in chat stream: {}", e.getMessage(), e);
            // Send the error to the client as an SSE event then close the stream cleanly.
            // Calling completeWithError(e) would re-throw on Spring's async dispatch and
            // route the exception to GlobalExceptionHandler, which then fails with
            // "No converter for LinkedHashMap with preset Content-Type 'text/event-stream'"
            // because the response is already committed as text/event-stream.
            try {
                Map<String, Object> errorData = Map.of(
                        "code", "AI_PROVIDER_ERROR",
                        "message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(errorData)));
            } catch (IOException ex) {
                log.warn("Failed to send error event to client: {}", ex.getMessage());
            }
            emitter.complete();
        }
    }

    // =========================================================================
    // Tool-loop drivers
    // =========================================================================

    private void runStreamingToolLoop(String tenantId, String userId, Conversation conversation,
                                       String systemPrompt, List<MessageParam> conversationParams,
                                       SseEmitter emitter, List<AiProposal> proposals,
                                       int[] tokenCounts) throws IOException {
        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            StreamAssembler assembler = new StreamAssembler(emitter, objectMapper, toolRegistry);
            MessageCreateParams params = anthropicService
                    .buildRequest(tenantId, systemPrompt, conversationParams).build();

            try (StreamResponse<RawMessageStreamEvent> stream = anthropicService.streamMessage(params)) {
                stream.stream().forEach(assembler::accept);
            }

            tokenTrackingService.recordUsage(tenantId, assembler.inputTokens(), assembler.outputTokens());
            tokenCounts[0] += assembler.inputTokens();
            tokenCounts[1] += assembler.outputTokens();

            boolean hasTools = !assembler.toolUses().isEmpty();
            List<DispatchResult> dispatched = hasTools
                    ? dispatchAll(tenantId, userId, assembler.toolUses(), emitter, proposals)
                    : List.of();

            enrichAssistantBlocksWithProposalIds(assembler.assistantBlocks(), dispatched);

            messageRepository.save(ChatMessage.assistant(
                    tenantId, conversation.id(), assembler.assistantBlocks(),
                    assembler.inputTokens(), assembler.outputTokens()));
            conversationParams.add(toAssistantMessageParam(assembler.assistantBlocks()));

            if (!"tool_use".equals(assembler.stopReason()) || !hasTools) break;
            if (tokenCounts[0] + tokenCounts[1] > TOKEN_BUDGET) {
                emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(
                        Map.of("code", "TOKEN_BUDGET_EXCEEDED",
                                "message", "Tool loop exceeded token budget — stopped after " + (i + 1) + " iterations"))));
                break;
            }

            List<Map<String, Object>> resultBlocks = buildToolResultBlocks(dispatched);
            messageRepository.save(ChatMessage.user(tenantId, conversation.id(), resultBlocks));
            conversationParams.add(toUserMessageParam(resultBlocks));
        }
    }

    private void runSyncToolLoop(String tenantId, String userId, Conversation conversation,
                                  String systemPrompt, List<MessageParam> conversationParams,
                                  List<AiProposal> proposals, int[] tokenCounts,
                                  StringBuilder lastText) {
        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            MessageCreateParams params = anthropicService
                    .buildRequest(tenantId, systemPrompt, conversationParams).build();
            Message response = anthropicService.sendMessage(params);

            int inTok = (int) response.usage().inputTokens();
            int outTok = (int) response.usage().outputTokens();
            tokenTrackingService.recordUsage(tenantId, inTok, outTok);
            tokenCounts[0] += inTok;
            tokenCounts[1] += outTok;

            List<Map<String, Object>> assistantBlocks = new ArrayList<>();
            List<StreamAssembler.ToolUseRecord> toolUses = new ArrayList<>();
            StringBuilder textBuf = new StringBuilder();
            for (ContentBlock block : response.content()) {
                block.text().ifPresent(t -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("type", "text");
                    b.put("text", t.text());
                    assistantBlocks.add(b);
                    textBuf.append(t.text());
                });
                block.toolUse().ifPresent(t -> {
                    Map<String, Object> input = convertToolInput(t._input());
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("type", "tool_use");
                    b.put("id", t.id());
                    b.put("name", t.name());
                    b.put("input", input);
                    assistantBlocks.add(b);
                    toolUses.add(new StreamAssembler.ToolUseRecord(t.id(), t.name(), input));
                });
            }
            lastText.setLength(0);
            lastText.append(textBuf);

            boolean hasTools = !toolUses.isEmpty();
            List<DispatchResult> dispatched = hasTools
                    ? dispatchAll(tenantId, userId, toolUses, null, proposals)
                    : List.of();

            enrichAssistantBlocksWithProposalIds(assistantBlocks, dispatched);

            messageRepository.save(ChatMessage.assistant(
                    tenantId, conversation.id(), assistantBlocks, inTok, outTok));
            conversationParams.add(toAssistantMessageParam(assistantBlocks));

            String stopReason = response.stopReason()
                    .map(sr -> sr.toString().toLowerCase().replace("\"", ""))
                    .orElse(null);
            if (!"tool_use".equals(stopReason) || !hasTools) break;
            if (tokenCounts[0] + tokenCounts[1] > TOKEN_BUDGET) {
                log.warn("Sync tool loop exceeded token budget for conversation {}", conversation.id());
                break;
            }

            List<Map<String, Object>> resultBlocks = buildToolResultBlocks(dispatched);
            messageRepository.save(ChatMessage.user(tenantId, conversation.id(), resultBlocks));
            conversationParams.add(toUserMessageParam(resultBlocks));
        }
    }

    // =========================================================================
    // Dispatch + persistence helpers
    // =========================================================================

    private List<DispatchResult> dispatchAll(String tenantId, String userId,
                                              List<StreamAssembler.ToolUseRecord> toolUses,
                                              SseEmitter emitter, List<AiProposal> proposalsOut) {
        List<DispatchResult> results = new ArrayList<>(toolUses.size());
        for (StreamAssembler.ToolUseRecord tu : toolUses) {
            DispatchResult result = toolDispatcher.dispatch(tenantId, userId, tu.id(), tu.name(), tu.input());
            results.add(result);
            emitDispatchEvent(emitter, result);
            if (result.proposal() != null) {
                proposalsOut.add(result.proposal());
            }
        }
        return results;
    }

    private void emitDispatchEvent(SseEmitter emitter, DispatchResult result) {
        if (emitter == null) return;
        try {
            if (result.proposal() != null) {
                emitter.send(SseEmitter.event().name("proposal")
                        .data(objectMapper.writeValueAsString(result.proposal())));
            } else {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("toolUseId", result.toolUseId());
                payload.put("name", result.toolName());
                payload.put("isError", result.isError());
                payload.put("status", result.isError() ? "error" : "done");
                emitter.send(SseEmitter.event().name("tool_result")
                        .data(objectMapper.writeValueAsString(payload)));
            }
        } catch (IOException e) {
            log.error("Failed to emit dispatch event: {}", e.getMessage());
        }
    }

    private void enrichAssistantBlocksWithProposalIds(List<Map<String, Object>> blocks,
                                                       List<DispatchResult> dispatched) {
        if (blocks == null || dispatched == null || dispatched.isEmpty()) return;
        Map<String, UUID> proposalByToolUseId = new LinkedHashMap<>();
        for (DispatchResult r : dispatched) {
            if (r.proposal() != null) {
                proposalByToolUseId.put(r.toolUseId(), r.proposal().id());
            }
        }
        for (Map<String, Object> b : blocks) {
            if ("tool_use".equals(b.get("type"))) {
                UUID pid = proposalByToolUseId.get(String.valueOf(b.get("id")));
                if (pid != null) b.put("proposalId", pid.toString());
            }
        }
    }

    private List<Map<String, Object>> buildToolResultBlocks(List<DispatchResult> dispatched) {
        List<Map<String, Object>> blocks = new ArrayList<>(dispatched.size());
        for (DispatchResult r : dispatched) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("type", "tool_result");
            b.put("tool_use_id", r.toolUseId());
            b.put("content", r.resultJson());
            b.put("is_error", r.isError());
            blocks.add(b);
        }
        return blocks;
    }

    // =========================================================================
    // History reconstruction
    // =========================================================================

    private List<MessageParam> buildMessageHistory(UUID conversationId, String tenantId) {
        List<ChatMessage> history = messageRepository.findByConversation(conversationId, tenantId);
        List<MessageParam> messages = new ArrayList<>(history.size());
        for (ChatMessage msg : history) {
            if ("system".equals(msg.role())) continue;
            MessageParam.Role role = "user".equals(msg.role())
                    ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT;
            List<ContentBlockParam> blocks = toContentBlockParams(msg);
            if (blocks.isEmpty()) continue;
            messages.add(MessageParam.builder()
                    .role(role)
                    .contentOfBlockParams(blocks)
                    .build());
        }
        return messages;
    }

    private List<ContentBlockParam> toContentBlockParams(ChatMessage msg) {
        List<ContentBlockParam> result = new ArrayList<>();
        List<Map<String, Object>> blocks = msg.contentBlocks();
        if (blocks == null || blocks.isEmpty()) {
            String text = msg.legacyContent();
            if (text != null && !text.isEmpty()) {
                result.add(ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()));
            }
            return result;
        }
        for (Map<String, Object> block : blocks) {
            ContentBlockParam param = convertPersistedBlock(block);
            if (param != null) result.add(param);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private ContentBlockParam convertPersistedBlock(Map<String, Object> block) {
        String type = String.valueOf(block.getOrDefault("type", ""));
        return switch (type) {
            case "text" -> ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(String.valueOf(block.getOrDefault("text", "")))
                    .build());
            case "tool_use" -> {
                ToolUseBlockParam.Input.Builder inputBuilder = ToolUseBlockParam.Input.builder();
                Object rawInput = block.get("input");
                if (rawInput instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        inputBuilder.putAdditionalProperty(String.valueOf(e.getKey()), JsonValue.from(e.getValue()));
                    }
                }
                yield ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                        .id(String.valueOf(block.get("id")))
                        .name(String.valueOf(block.get("name")))
                        .input(inputBuilder.build())
                        .build());
            }
            case "tool_result" -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(String.valueOf(block.get("tool_use_id")))
                    .content(String.valueOf(block.getOrDefault("content", "")))
                    .isError(Boolean.TRUE.equals(block.get("is_error")))
                    .build());
            // Skip legacy proposals — they have no matching tool_result and would
            // fail Anthropic validation when replayed.
            case "tool_use_legacy" -> null;
            default -> null;
        };
    }

    // =========================================================================
    // Block ↔ MessageParam conversion
    // =========================================================================

    private MessageParam toAssistantMessageParam(List<Map<String, Object>> blocks) {
        List<ContentBlockParam> params = new ArrayList<>();
        for (Map<String, Object> b : blocks) {
            ContentBlockParam p = convertPersistedBlock(b);
            if (p != null) params.add(p);
        }
        return MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .contentOfBlockParams(params)
                .build();
    }

    private MessageParam toUserMessageParam(List<Map<String, Object>> blocks) {
        List<ContentBlockParam> params = new ArrayList<>();
        for (Map<String, Object> b : blocks) {
            ContentBlockParam p = convertPersistedBlock(b);
            if (p != null) params.add(p);
        }
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(params)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToolInput(Object rawInput) {
        if (rawInput instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        try {
            return objectMapper.convertValue(rawInput, Map.class);
        } catch (Exception e) {
            log.error("Failed to convert tool input: {}", e.getMessage());
            return Map.of();
        }
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
}
