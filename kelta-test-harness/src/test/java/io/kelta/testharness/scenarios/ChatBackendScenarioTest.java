package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chat backend through the real stack (telehealth slice 2, V168): start a
 * conversation over /api/chat, send + list messages, watch the hook bump
 * last_message_at, and verify the V168 DDL (RLS-scoped tables, participant
 * uniqueness) — the pieces Mockito worker tests can't reach.
 */
@DisplayName("Chat Backend Scenario")
class ChatBackendScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("starts a conversation, messages it, and persists chat rows under RLS")
    @SuppressWarnings("unchecked")
    void chatRoundTrip() throws Exception {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/chat/conversations?view=mine",
                HttpStatus.OK, 20);

        // Start a conversation.
        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/chat/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("subject", "Harness chat"))
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String conversationId = String.valueOf(created.getBody().get("id"));
        assertThat(created.getBody().get("status")).isEqualTo("OPEN");

        // Send a message; the hook must accept the participant sender.
        ResponseEntity<Map> message = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/chat/conversations/" + conversationId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", "hello from the harness"))
                .retrieve().toEntity(Map.class);
        assertThat(message.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // History comes back over the participant-checked read path.
        ResponseEntity<Map> history = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/chat/conversations/" + conversationId + "/messages")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) history.getBody().get("data");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("body")).isEqualTo("hello from the harness");
        assertThat(messages.get(0).get("senderType")).isEqualTo("INTERNAL");

        // Inbox 'mine' view lists it, with last_message_at bumped by the hook.
        ResponseEntity<Map> mine = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/chat/conversations?view=mine")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> conversations = (List<Map<String, Object>>) mine.getBody().get("data");
        Map<String, Object> row = conversations.stream()
                .filter(c -> conversationId.equals(c.get("id"))).findFirst().orElseThrow();
        assertThat(row.get("lastMessageAt")).isNotNull();

        try (Connection conn = openDbConnection()) {
            // Participant row exists exactly once (unique constraint) with AGENT role.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT role, COUNT(*) OVER () AS total FROM chat_participant WHERE conversation_id = ?")) {
                ps.setString(1, conversationId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("role")).isEqualTo("AGENT");
                    assertThat(rs.getInt("total")).isEqualTo(1);
                }
            }
            // Tenant column stamped on every chat row (RLS predicate feed).
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT tenant_id FROM chat_message WHERE conversation_id = ?")) {
                ps.setString(1, conversationId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("tenant_id")).isEqualTo(tenantId);
                }
            }
        }

        // Close, then the hook must reject further messages.
        ResponseEntity<Map> closed = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/chat/conversations/" + conversationId + "/close")
                .retrieve().toEntity(Map.class);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closed.getBody().get("status")).isEqualTo("CLOSED");

        assertThat(postExpectingError(token,
                "/" + slug + "/api/chat/conversations/" + conversationId + "/messages",
                Map.of("body", "too late")))
                .as("message into CLOSED conversation is rejected")
                .isIn(400, 409, 422);
    }

    private int postExpectingError(String token, String uri, Map<String, ?> body) {
        try {
            ResponseEntity<Map> response = gatewayClientWithToken(token)
                    .post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().toEntity(Map.class);
            return response.getStatusCode().value();
        } catch (org.springframework.web.client.HttpClientErrorException
                 | org.springframework.web.client.HttpServerErrorException e) {
            return e.getStatusCode().value();
        }
    }
}
