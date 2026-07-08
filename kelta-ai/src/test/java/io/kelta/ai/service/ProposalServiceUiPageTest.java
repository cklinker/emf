package io.kelta.ai.service;

import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** applyProposal for the ui_page type (app-intelligence slice 2). */
class ProposalServiceUiPageTest {

    private ChatMessageRepository messageRepository;
    private WorkerApiClient workerApiClient;
    private ProposalService service;
    private UUID proposalId;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ChatMessageRepository.class);
        workerApiClient = mock(WorkerApiClient.class);
        service = new ProposalService(messageRepository, workerApiClient,
                mock(AnthropicService.class), new ObjectMapper());
        proposalId = UUID.randomUUID();
    }

    private void stubProposalMessage(Map<String, Object> input) {
        Map<String, Object> toolUse = new LinkedHashMap<>();
        toolUse.put("type", "tool_use");
        toolUse.put("proposalId", proposalId.toString());
        toolUse.put("name", "propose_ui_page");
        toolUse.put("input", input);
        ChatMessage message = new ChatMessage(UUID.randomUUID(), "tenant-1", UUID.randomUUID(),
                "assistant", List.of(toolUse), null, null, 0, 0, Instant.now());
        when(messageRepository.findByProposalId(proposalId, "tenant-1"))
                .thenReturn(Optional.of(message));
    }

    private static Map<String, Object> node(String type) {
        Map<String, Object> n = new HashMap<>();
        n.put("type", type);
        return n;
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesADraftUiPageWithValidatedTree() {
        Map<String, Object> heading = node("heading");
        Map<String, Object> metric = node("metric");
        Map<String, Object> grid = node("grid");
        grid.put("children", new ArrayList<>(List.of(metric)));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "customer_overview");
        input.put("title", "Customer Overview");
        input.put("components", new ArrayList<>(List.of(heading, grid)));
        input.put("dataSources", List.of(Map.of("name", "customers", "collection", "customers")));
        stubProposalMessage(input);
        when(workerApiClient.createUiPage(anyString(), anyString(), any()))
                .thenReturn(Map.of("data", Map.of("id", "page-1")));

        Map<String, Object> result = service.applyProposal(proposalId, "tenant-1", "user-1");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(workerApiClient).createUiPage(anyString(), anyString(), captor.capture());
        Map<String, Object> attributes = captor.getValue();
        assertEquals(false, attributes.get("published")); // draft — never auto-published
        Map<String, Object> config = (Map<String, Object>) attributes.get("config");
        assertEquals(2, config.get("schemaVersion"));
        assertNotNull(config.get("dataSources"));
        List<Map<String, Object>> components = (List<Map<String, Object>>) config.get("components");
        // Missing ids were assigned during validation.
        assertNotNull(components.get(0).get("id"));

        assertEquals("page-1", result.get("pageId"));
        assertEquals(3, result.get("componentCount"));
        assertEquals(false, result.get("published"));
    }

    @Test
    void rejectsUnknownWidgetTypesWithTheirNames() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "bad_page");
        input.put("components", new ArrayList<>(List.of(node("heading"), node("hologram"))));
        stubProposalMessage(input);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.applyProposal(proposalId, "tenant-1", "user-1"));
        assertTrue(ex.getMessage().contains("hologram"));
        verify(workerApiClient, never()).createUiPage(anyString(), anyString(), any());
    }

    @Test
    void rejectsATreeOverTheNodeCap() {
        List<Map<String, Object>> components = new ArrayList<>();
        for (int i = 0; i < 201; i++) components.add(node("text"));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "huge_page");
        input.put("components", components);
        stubProposalMessage(input);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.applyProposal(proposalId, "tenant-1", "user-1"));
        assertTrue(ex.getMessage().contains("200"));
    }

    @Test
    void rejectsATreeOverTheDepthCap() {
        Map<String, Object> leaf = node("text");
        Map<String, Object> current = leaf;
        for (int i = 0; i < 9; i++) {
            Map<String, Object> parent = node("container");
            parent.put("children", new ArrayList<>(List.of(current)));
            current = parent;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "deep_page");
        input.put("components", new ArrayList<>(List.of(current)));
        stubProposalMessage(input);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.applyProposal(proposalId, "tenant-1", "user-1"));
        assertTrue(ex.getMessage().toLowerCase().contains("depth"));
    }

    @Test
    void rejectsAnEmptyTree() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "empty_page");
        input.put("components", new ArrayList<Map<String, Object>>());
        stubProposalMessage(input);

        assertThrows(IllegalArgumentException.class,
                () -> service.applyProposal(proposalId, "tenant-1", "user-1"));
    }
}
