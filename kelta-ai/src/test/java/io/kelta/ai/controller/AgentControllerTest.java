package io.kelta.ai.controller;

import io.kelta.ai.model.AgentDefinition;
import io.kelta.ai.service.AgentService;
import io.kelta.ai.service.AgentUpsertRequest;
import io.kelta.ai.service.agent.AgentExecutionException;
import io.kelta.ai.service.agent.AgentRunRequest;
import io.kelta.ai.service.agent.AgentRunResult;
import io.kelta.ai.service.agent.AgentRuntimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentController")
class AgentControllerTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private AgentService agentService;

    @Mock
    private AgentRuntimeService agentRuntimeService;

    private AgentController controller() {
        return new AgentController(agentService, agentRuntimeService);
    }

    private static AgentDefinition sample(UUID id) {
        return new AgentDefinition(id, TENANT, "Bot", "d", "prompt", null, null,
                List.of("search"), null, true, "u", "u",
                java.time.Instant.now(), java.time.Instant.now());
    }

    @Test
    @DisplayName("GET list returns the tenant's agents")
    void list() {
        UUID id = UUID.randomUUID();
        when(agentService.list(TENANT)).thenReturn(List.of(sample(id)));
        List<AgentDefinition> result = controller().list(TENANT);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("GET by id returns 200 when found, 404 when missing")
    void getById() {
        UUID id = UUID.randomUUID();
        when(agentService.get(TENANT, id)).thenReturn(Optional.of(sample(id)));
        assertThat(controller().get(TENANT, id).getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID missing = UUID.randomUUID();
        when(agentService.get(TENANT, missing)).thenReturn(Optional.empty());
        assertThat(controller().get(TENANT, missing).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST create returns 201 with the created agent")
    void create() {
        UUID id = UUID.randomUUID();
        AgentUpsertRequest req = new AgentUpsertRequest("Bot", "d", "prompt", null, null, List.of("search"), null, true);
        when(agentService.create(eq(TENANT), eq("user-1"), any())).thenReturn(sample(id));

        ResponseEntity<AgentDefinition> response = controller().create(TENANT, "user-1", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("PUT update returns 200 when found, 404 when missing")
    void update() {
        UUID id = UUID.randomUUID();
        AgentUpsertRequest req = new AgentUpsertRequest("Bot", "d", "prompt", null, null, List.of(), null, true);
        when(agentService.update(eq(TENANT), eq("user-1"), eq(id), any())).thenReturn(Optional.of(sample(id)));
        assertThat(controller().update(TENANT, "user-1", id, req).getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID missing = UUID.randomUUID();
        when(agentService.update(eq(TENANT), eq("user-1"), eq(missing), any())).thenReturn(Optional.empty());
        assertThat(controller().update(TENANT, "user-1", missing, req).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE returns 204 when removed, 404 when absent")
    void delete() {
        UUID id = UUID.randomUUID();
        when(agentService.delete(TENANT, id)).thenReturn(true);
        assertThat(controller().delete(TENANT, id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        UUID missing = UUID.randomUUID();
        when(agentService.delete(TENANT, missing)).thenReturn(false);
        assertThat(controller().delete(TENANT, missing).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("validation errors map to 400")
    void badRequest() {
        ResponseEntity<Map<String, Object>> response =
                controller().handleBadRequest(new IllegalArgumentException("'name' is required"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("'name' is required");
    }

    @Test
    @DisplayName("duplicate name maps to 409")
    void conflict() {
        ResponseEntity<Map<String, Object>> response =
                controller().handleDuplicate(new DuplicateKeyException("dup"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error")).isEqualTo("An agent with that name already exists");
    }

    @Test
    @DisplayName("POST run executes the agent and returns the result")
    void run() {
        UUID id = UUID.randomUUID();
        AgentRunResult expected = new AgentRunResult("done", List.of(), 5, 7, 1, "end_turn", false, false);
        when(agentService.get(TENANT, id)).thenReturn(Optional.of(sample(id)));
        when(agentRuntimeService.run(eq(TENANT), eq("user-1"), any(), eq("hello"))).thenReturn(expected);

        ResponseEntity<AgentRunResult> response =
                controller().run(TENANT, "user-1", id, new AgentRunRequest("hello"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().finalText()).isEqualTo("done");
    }

    @Test
    @DisplayName("POST run returns 404 for an unknown agent")
    void runNotFound() {
        UUID id = UUID.randomUUID();
        when(agentService.get(TENANT, id)).thenReturn(Optional.empty());

        ResponseEntity<AgentRunResult> response =
                controller().run(TENANT, "user-1", id, new AgentRunRequest("hello"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(agentRuntimeService);
    }

    @Test
    @DisplayName("POST run rejects blank input with 400")
    void runBlankInput() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> controller().run(TENANT, "user-1", id, new AgentRunRequest("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");
        verifyNoInteractions(agentService, agentRuntimeService);
    }

    @Test
    @DisplayName("token-limit exception maps to 429, disabled maps to 409")
    void executionErrors() {
        ResponseEntity<Map<String, Object>> tooMany = controller().handleExecution(
                new AgentExecutionException(AgentExecutionException.Reason.TOKEN_LIMIT_EXCEEDED, "limit"));
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        ResponseEntity<Map<String, Object>> disabled = controller().handleExecution(
                new AgentExecutionException(AgentExecutionException.Reason.AGENT_DISABLED, "off"));
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
