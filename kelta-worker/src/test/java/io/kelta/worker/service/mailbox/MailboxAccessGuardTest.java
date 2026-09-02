package io.kelta.worker.service.mailbox;

import io.kelta.worker.repository.MailboxAccessRepository;
import io.kelta.worker.repository.MailboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The entire authorization story for the support console.
 *
 * <p>The 404-not-403 behaviour is the point of most of these: a 403 confirms a mailbox or thread
 * exists, which is enough to enumerate them.
 */
@DisplayName("MailboxAccessGuard")
class MailboxAccessGuardTest {

    private static final String TENANT = "t1";
    private static final String MAILBOX = "mb1";
    private static final String USER = "u1";

    private MailboxRepository mailboxRepository;
    private MailboxAccessRepository accessRepository;
    private MailboxAccessGuard guard;

    @BeforeEach
    void setUp() {
        mailboxRepository = mock(MailboxRepository.class);
        accessRepository = mock(MailboxAccessRepository.class);
        guard = new MailboxAccessGuard(mailboxRepository, accessRepository,
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
        when(mailboxRepository.findById(MAILBOX, TENANT))
                .thenReturn(Optional.of(Map.of("id", MAILBOX, "tenant_id", TENANT)));
    }

    @Test
    @DisplayName("A member gets their roles back")
    void memberGetsRoles() {
        when(accessRepository.rolesForUser(TENANT, MAILBOX, USER)).thenReturn(List.of("AGENT"));

        MailboxAccessGuard.Access access = guard.require(TENANT, MAILBOX, USER);

        assertThat(access.canRead()).isTrue();
        assertThat(access.canAct()).isTrue();
        assertThat(access.canManage()).isFalse();
    }

    @Test
    @DisplayName("A non-member gets 404, not 403 — 403 would confirm the mailbox exists")
    void nonMemberIsNotFound() {
        when(accessRepository.rolesForUser(TENANT, MAILBOX, USER)).thenReturn(List.of());

        assertThatThrownBy(() -> guard.require(TENANT, MAILBOX, USER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("A mailbox in another tenant is indistinguishable from one with no access")
    void foreignMailboxLooksIdentical() {
        when(mailboxRepository.findById("other", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.require(TENANT, "other", USER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Roles are most-permissive-wins across direct and group grants")
    void mostPermissiveWins() {
        // Direct AGENT plus MANAGER via a group: collapsing to one role in the repository would
        // silently pick a winner, so the guard resolves it.
        when(accessRepository.rolesForUser(TENANT, MAILBOX, USER))
                .thenReturn(List.of("AGENT", "MANAGER"));

        MailboxAccessGuard.Access access = guard.require(TENANT, MAILBOX, USER);

        assertThat(access.canManage()).isTrue();
        assertThat(access.canAct()).isTrue();
    }

    @Test
    @DisplayName("VIEWER can read but not act")
    void viewerCannotAct() {
        when(accessRepository.rolesForUser(TENANT, MAILBOX, USER)).thenReturn(List.of("VIEWER"));

        MailboxAccessGuard.Access access = guard.require(TENANT, MAILBOX, USER);

        assertThat(access.canRead()).isTrue();
        assertThat(access.canAct()).isFalse();
        assertThatThrownBy(() -> guard.requireAct(access))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("AGENT can act but not manage")
    void agentCannotManage() {
        when(accessRepository.rolesForUser(TENANT, MAILBOX, USER)).thenReturn(List.of("AGENT"));
        MailboxAccessGuard.Access access = guard.require(TENANT, MAILBOX, USER);

        assertThatThrownBy(() -> guard.requireManage(access))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MANAGER");
    }

    @Test
    @DisplayName("A missing identity is 404 before any lookup happens")
    void missingIdentityIsNotFound() {
        assertThatThrownBy(() -> guard.require(TENANT, MAILBOX, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> guard.require(TENANT, MAILBOX, "  "))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("A thread is authorised against its OWNING mailbox")
    void threadAuthorisedAgainstItsOwnMailbox() {
        // Without this, membership of any one mailbox would grant read access to every thread in
        // the tenant by id.
        Map<String, Object> thread = Map.of("id", "th1", "mailbox_id", "mb-other");
        when(mailboxRepository.findById("mb-other", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireForThread(TENANT, thread, USER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("A user with no identity sees no mailboxes rather than erroring")
    void noIdentitySeesNothing() {
        assertThat(guard.visibleMailboxIds(TENANT, null)).isEmpty();
        assertThat(guard.visibleMailboxIds(TENANT, "")).isEmpty();
    }
}
