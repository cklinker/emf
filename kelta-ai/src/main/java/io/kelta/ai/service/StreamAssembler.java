package io.kelta.ai.service;

import com.anthropic.models.messages.RawMessageStreamEvent;
import io.kelta.ai.service.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles a streamed Anthropic message into structured content blocks
 * (text + tool_use). Emits SSE events to the client as content arrives,
 * and exposes the final assembled blocks once the stream completes.
 *
 * SSE event vocabulary emitted here:
 *  - delta       : text token (text blocks)
 *  - tool_call   : tool invocation start (READ tool — transient UI indicator)
 *  - tool_use    : tool invocation start (PROPOSE tool — back-compat)
 */
public class StreamAssembler {

    private static final Logger log = LoggerFactory.getLogger(StreamAssembler.class);

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;

    // Final assembled state — read after stream completes
    private final List<Map<String, Object>> assistantBlocks = new ArrayList<>();
    private final List<ToolUseRecord> toolUses = new ArrayList<>();

    // Per-block accumulation state
    private final StringBuilder currentText = new StringBuilder();
    private final StringBuilder currentToolInputJson = new StringBuilder();
    private String currentToolId;
    private String currentToolName;

    // Per-message state
    private int inputTokens;
    private int outputTokens;
    private String stopReason;

    public StreamAssembler(SseEmitter emitter, ObjectMapper objectMapper, ToolRegistry toolRegistry) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
    }

    public void accept(RawMessageStreamEvent event) {
        try {
            handleMessageStart(event);
            handleContentBlockStart(event);
            handleContentBlockDelta(event);
            handleContentBlockStop(event);
            handleMessageDelta(event);
        } catch (Exception e) {
            log.error("Error processing stream event: {}", e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> assistantBlocks() {
        return assistantBlocks;
    }

    public List<ToolUseRecord> toolUses() {
        return toolUses;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public String stopReason() {
        return stopReason;
    }

    private void handleMessageStart(RawMessageStreamEvent event) {
        event.messageStart().ifPresent(msgStart ->
                inputTokens = (int) msgStart.message().usage().inputTokens());
    }

    private void handleContentBlockStart(RawMessageStreamEvent event) {
        event.contentBlockStart().ifPresent(blockStart -> {
            blockStart.contentBlock().toolUse().ifPresent(toolUse -> {
                currentToolId = toolUse.id();
                currentToolName = toolUse.name();
                currentToolInputJson.setLength(0);

                String eventName = toolRegistry.isReadTool(currentToolName) ? "tool_call" : "tool_use";
                emit(eventName, Map.of(
                        "toolUseId", currentToolId,
                        "name", currentToolName,
                        "status", "pending"
                ));
            });
        });
    }

    private void handleContentBlockDelta(RawMessageStreamEvent event) {
        event.contentBlockDelta().ifPresent(delta -> {
            delta.delta().text().ifPresent(textDelta -> {
                String text = textDelta.text();
                currentText.append(text);
                emit("delta", Map.of("text", text));
            });
            delta.delta().inputJson().ifPresent(inputJsonDelta ->
                    currentToolInputJson.append(inputJsonDelta.partialJson()));
        });
    }

    @SuppressWarnings("unchecked")
    private void handleContentBlockStop(RawMessageStreamEvent event) {
        event.contentBlockStop().ifPresent(stop -> {
            if (currentToolId != null) {
                Map<String, Object> input = parseToolInput();
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_use");
                block.put("id", currentToolId);
                block.put("name", currentToolName);
                block.put("input", input);
                assistantBlocks.add(block);
                toolUses.add(new ToolUseRecord(currentToolId, currentToolName, input));
                currentToolId = null;
                currentToolName = null;
                currentToolInputJson.setLength(0);
            } else if (currentText.length() > 0) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "text");
                block.put("text", currentText.toString());
                assistantBlocks.add(block);
                currentText.setLength(0);
            }
        });
    }

    private void handleMessageDelta(RawMessageStreamEvent event) {
        event.messageDelta().ifPresent(msgDelta -> {
            outputTokens = (int) msgDelta.usage().outputTokens();
            try {
                msgDelta.delta().stopReason().ifPresent(sr ->
                        stopReason = sr.toString().toLowerCase().replace("\"", ""));
            } catch (Exception e) {
                log.debug("Could not extract stop reason: {}", e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolInput() {
        if (currentToolInputJson.isEmpty()) return Map.of();
        try {
            return objectMapper.readValue(currentToolInputJson.toString(), Map.class);
        } catch (Exception e) {
            log.error("Failed to parse tool input JSON for {}: {}", currentToolName, e.getMessage());
            return Map.of();
        }
    }

    private void emit(String name, Object data) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.error("Failed to send {} event: {}", name, e.getMessage());
        }
    }

    public record ToolUseRecord(String id, String name, Map<String, Object> input) {
    }
}
