package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ApprovalProcessConfigHook")
class ApprovalProcessConfigHookTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String TARGET_ID = "a4f55cde-d3bd-43f4-b6c5-b95e59e01926";

    private PlatformEventPublisher eventPublisher;
    private JdbcTemplate jdbcTemplate;
    private ApprovalProcessConfigHook hook;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        hook = new ApprovalProcessConfigHook(eventPublisher, jdbcTemplate);
    }

    @Test
    @DisplayName("publishes the TARGET collection's id AND name — never the literal 'approval-processes'")
    void publishesTargetCollectionIdentity() {
        // Regression: the payload used to carry the target collection's id with the
        // name "approval-processes". The gateway consumed that as a real collection
        // change and re-pointed the target's route to /api/approval-processes/**,
        // making the target collection unroutable (404) until its next real event.
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(TARGET_ID)))
                .thenReturn(List.of("customers"));

        hook.afterCreate(Map.of("collectionId", TARGET_ID, "name", "Actor Probe"), TENANT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<PlatformEvent<CollectionChangedPayload>> captor =
                ArgumentCaptor.forClass((Class) PlatformEvent.class);
        verify(eventPublisher).publish(
                eq(CollectionConfigEventPublisher.SUBJECT_PREFIX + TARGET_ID), captor.capture());

        CollectionChangedPayload payload = captor.getValue().getPayload();
        assertThat(payload.getId()).isEqualTo(TARGET_ID);
        assertThat(payload.getName()).isEqualTo("customers");
        assertThat(payload.getChangeType()).isEqualTo(ChangeType.UPDATED);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("skips the broadcast when the target collection cannot be resolved")
    void skipsWhenTargetUnknown() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(TARGET_ID)))
                .thenReturn(List.of());

        hook.afterUpdate("p1", Map.of("collectionId", TARGET_ID), Map.of(), TENANT);

        verify(eventPublisher, org.mockito.Mockito.never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("skips the broadcast when the record has no collectionId")
    void skipsWithoutCollectionId() {
        hook.afterCreate(Map.of("name", "No Target"), TENANT);

        verifyNoInteractions(eventPublisher, jdbcTemplate);
    }
}
