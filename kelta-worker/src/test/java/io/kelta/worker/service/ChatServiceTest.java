package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ChatService")
class ChatServiceTest {

    private static final String TENANT = "t1";
    private static final ChatService.ChatActor PORTAL_ACTOR =
            new ChatService.ChatActor("u-portal", "pat@example.com", "PORTAL");
    private static final ChatService.ChatActor STAFF_ACTOR =
            new ChatService.ChatActor("u-staff", "agent@example.com", "INTERNAL");

    private QueryEngine queryEngine;
    private CollectionRegistry collectionRegistry;
    private JdbcTemplate jdbcTemplate;
    private ParticipantShareSupport shareSupport;
    private ChatService service;

    @BeforeEach
    void setUp() {
        queryEngine = mock(QueryEngine.class);
        collectionRegistry = mock(CollectionRegistry.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        shareSupport = mock(ParticipantShareSupport.class);
        when(collectionRegistry.get(anyString()))
                .thenReturn(mock(CollectionDefinition.class));
        service = new ChatService(queryEngine, collectionRegistry, jdbcTemplate, shareSupport);
    }

    private void stubMembership(String userId, boolean member) {
        when(jdbcTemplate.queryForObject(contains("chat_participant"), eq(Integer.class),
                eq(TENANT), anyString(), eq(userId), eq(userId)))
                .thenReturn(member ? 1 : 0);
    }

    @Test
    @DisplayName("portal actor starting a conversation sets PORTAL origin, adds participant, grants share")
    void portalStartGrantsShare() {
        when(queryEngine.create(any(), any()))
                .thenReturn(Map.of("id", "conv-1", "status", "OPEN"))   // conversation
                .thenReturn(Map.of("id", "part-1"));                    // participant
        stubMembership("u-portal", false);

        Map<String, Object> conversation =
                service.startConversation(TENANT, PORTAL_ACTOR, null, "Help", null);

        assertThat(conversation.get("id")).isEqualTo("conv-1");

        ArgumentCaptor<Map<String, Object>> creates = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine, times(2)).create(any(), creates.capture());
        Map<String, Object> conversationData = creates.getAllValues().get(0);
        assertThat(conversationData).containsEntry("origin", "PORTAL")
                .containsEntry("status", "OPEN").containsEntry("tenantId", TENANT);
        Map<String, Object> participantData = creates.getAllValues().get(1);
        assertThat(participantData).containsEntry("userId", "u-portal")
                .containsEntry("role", "PORTAL");

        verify(shareSupport).grant("chat-conversations", "conv-1", "u-portal", "READ");
    }

    @Test
    @DisplayName("staff participants never get a record_share grant")
    void staffStartNoShare() {
        when(queryEngine.create(any(), any()))
                .thenReturn(Map.of("id", "conv-1", "status", "OPEN"))
                .thenReturn(Map.of("id", "part-1"));
        stubMembership("u-staff", false);

        service.startConversation(TENANT, STAFF_ACTOR, null, null, null);

        verifyNoInteractions(shareSupport);
    }

    @Test
    @DisplayName("sendMessage forces sender identity from the actor and requires membership")
    void sendMessageForcesSender() {
        stubMembership("u-portal", true);
        when(queryEngine.create(any(), any())).thenReturn(Map.of("id", "msg-1"));

        service.sendMessage(TENANT, PORTAL_ACTOR, "conv-1", "hello", null);

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine).create(any(), data.capture());
        assertThat(data.getValue())
                .containsEntry("senderId", "u-portal")
                .containsEntry("senderType", "PORTAL")
                .containsEntry("kind", "TEXT")
                .containsEntry("conversationId", "conv-1");
    }

    @Test
    @DisplayName("non-participants are denied and audited on send")
    void sendMessageDeniesNonParticipant() {
        stubMembership("u-portal", false);

        assertThatThrownBy(() -> service.sendMessage(TENANT, PORTAL_ACTOR, "conv-1", "hi", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(queryEngine, never()).create(any(), any());
    }

    @Test
    @DisplayName("portal actors cannot assign; staff claim OPEN conversations for themselves")
    void assignRules() {
        assertThatThrownBy(() -> service.assign(TENANT, PORTAL_ACTOR, "conv-1", null, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        when(queryEngine.getById(any(), eq("conv-1")))
                .thenReturn(Optional.of(Map.of("id", "conv-1", "status", "OPEN")));
        when(queryEngine.update(any(), eq("conv-1"), any()))
                .thenReturn(Optional.of(Map.of("id", "conv-1", "status", "ASSIGNED",
                        "assignedTo", "u-staff")));
        when(queryEngine.create(any(), any())).thenReturn(Map.of("id", "part-2"));
        stubMembership("u-staff", false);

        Map<String, Object> updated = service.assign(TENANT, STAFF_ACTOR, "conv-1", null, false);
        assertThat(updated.get("status")).isEqualTo("ASSIGNED");

        ArgumentCaptor<Map<String, Object>> update = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine).update(any(), eq("conv-1"), update.capture());
        assertThat(update.getValue()).containsEntry("assignedTo", "u-staff");
    }

    @Test
    @DisplayName("assigning to another user requires MANAGE_CHAT; closed conversations reject assignment")
    void assignGuards() {
        when(queryEngine.getById(any(), eq("conv-1")))
                .thenReturn(Optional.of(Map.of("id", "conv-1", "status", "OPEN")));
        assertThatThrownBy(() -> service.assign(TENANT, STAFF_ACTOR, "conv-1", "someone-else", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        when(queryEngine.getById(any(), eq("conv-2")))
                .thenReturn(Optional.of(Map.of("id", "conv-2", "status", "CLOSED")));
        assertThatThrownBy(() -> service.assign(TENANT, STAFF_ACTOR, "conv-2", null, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("read receipts hit chat_participant directly and 403 for strangers")
    void readReceipt() {
        when(jdbcTemplate.update(contains("last_read_at"), any(), eq(TENANT), eq("conv-1"),
                eq("u-portal"))).thenReturn(1);
        service.markRead(TENANT, PORTAL_ACTOR, "conv-1");
        verify(queryEngine, never()).update(any(), anyString(), any());

        when(jdbcTemplate.update(contains("last_read_at"), any(), eq(TENANT), eq("conv-1"),
                eq("u-staff"))).thenReturn(0);
        assertThatThrownBy(() -> service.markRead(TENANT, STAFF_ACTOR, "conv-1"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("listConversations rejects unknown views")
    void listRejectsUnknownView() {
        assertThatThrownBy(() -> service.listConversations(TENANT, STAFF_ACTOR,
                "everything", null, null, 0, 50))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
