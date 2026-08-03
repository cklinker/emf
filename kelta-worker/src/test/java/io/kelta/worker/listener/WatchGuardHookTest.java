package io.kelta.worker.listener;

import io.kelta.runtime.router.UserIdResolver;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The {@code watches} collection is reachable through the generic dynamic route,
 * so without this guard any authenticated tenant user could read, edit, delete,
 * or re-own another member's watches. These tests pin that shut.
 */
@DisplayName("WatchGuardHook Tests")
class WatchGuardHookTest {

    private static final String TENANT = "tenant-1";
    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";

    private UserIdResolver userIdResolver;
    private JdbcTemplate jdbcTemplate;
    private WatchGuardHook hook;

    @BeforeEach
    void setUp() {
        userIdResolver = mock(UserIdResolver.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        hook = new WatchGuardHook(userIdResolver, jdbcTemplate);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void callerIs(String email, String resolvedUuid) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", email);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(userIdResolver.resolve(anyString(), anyString())).thenReturn(resolvedUuid);
    }

    private static Map<String, Object> record(String memberId) {
        Map<String, Object> record = new HashMap<>();
        record.put("targetId", "target-1");
        if (memberId != null) {
            record.put("memberId", memberId);
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    private void watchOwnedBy(String memberId) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(memberId == null ? List.of() : List.of(memberId));
    }

    @Nested
    @DisplayName("Registration contract")
    class RegistrationContract {

        @Test
        @DisplayName("guards the watches collection, before other hooks")
        void guardsWatches() {
            assertThat(hook.getCollectionName()).isEqualTo("watches");
            assertThat(hook.getOrder()).isEqualTo(-100);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("allows a member to create their own watch")
        void allowsOwnCreate() {
            callerIs("alice@example.com", ALICE);

            assertThat(hook.beforeCreate(record(ALICE), TENANT).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("blocks creating a watch owned by someone else")
        void blocksForeignCreate() {
            callerIs("bob@example.com", BOB);

            BeforeSaveResult result = hook.beforeCreate(record(ALICE), TENANT);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors().get(0).message())
                    .isEqualTo("Watches can only be modified by their owner");
        }

        @Test
        @DisplayName("blocks a create with no owner at all")
        void blocksOwnerlessCreate() {
            callerIs("bob@example.com", BOB);

            assertThat(hook.beforeCreate(record(null), TENANT).isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("allows editing one's own watch")
        void allowsOwnUpdate() {
            callerIs("alice@example.com", ALICE);

            assertThat(hook.beforeUpdate("w1", record(ALICE), record(ALICE), TENANT).isSuccess())
                    .isTrue();
        }

        @Test
        @DisplayName("blocks editing someone else's watch")
        void blocksForeignUpdate() {
            callerIs("bob@example.com", BOB);

            assertThat(hook.beforeUpdate("w1", record(null), record(ALICE), TENANT).isSuccess())
                    .isFalse();
        }

        @Test
        @DisplayName("blocks re-owning a watch to another member")
        void blocksReOwning() {
            // The subtler attack: edit your OWN watch to hand it to someone else,
            // or to claim theirs by rewriting the owner field.
            callerIs("alice@example.com", ALICE);

            assertThat(hook.beforeUpdate("w1", record(BOB), record(ALICE), TENANT).isSuccess())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("allows deleting one's own watch")
        void allowsOwnDelete() {
            callerIs("alice@example.com", ALICE);
            watchOwnedBy(ALICE);

            assertThat(hook.beforeDelete("w1", TENANT).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("blocks deleting someone else's watch")
        void blocksForeignDelete() {
            callerIs("bob@example.com", BOB);
            watchOwnedBy(ALICE);

            assertThat(hook.beforeDelete("w1", TENANT).isSuccess()).isFalse();
        }

        @Test
        @DisplayName("allows deleting a row that no longer exists")
        void allowsMissingRowDelete() {
            callerIs("alice@example.com", ALICE);
            watchOwnedBy(null); // no row

            assertThat(hook.beforeDelete("gone", TENANT).isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Identity handling")
    class IdentityHandling {

        @Test
        @DisplayName("admits internal-tier writes with no HTTP identity")
        void admitsInternalTier() {
            // Flows, schedulers and the matcher legitimately write watches.
            RequestContextHolder.resetRequestAttributes();

            assertThat(hook.beforeCreate(record(ALICE), TENANT).isSuccess()).isTrue();
            assertThat(hook.beforeUpdate("w1", record(ALICE), record(BOB), TENANT).isSuccess())
                    .isTrue();
            assertThat(hook.beforeDelete("w1", TENANT).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("admits a request with an empty identity header")
        void admitsBlankHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-Id", "");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThat(hook.beforeCreate(record(ALICE), TENANT).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("FAILS CLOSED when an identity is present but unresolvable")
        void failsClosedOnUnresolvableIdentity() {
            // UserIdResolver returns the original identifier on failure rather than
            // null, so a non-UUID result means "could not resolve" — treating it as
            // an owner id would compare an email against the owner column.
            callerIs("ghost@example.com", "ghost@example.com");

            assertThat(hook.beforeCreate(record(ALICE), TENANT).isSuccess()).isFalse();
            assertThat(hook.beforeUpdate("w1", record(ALICE), record(ALICE), TENANT).isSuccess())
                    .isFalse();
            assertThat(hook.beforeDelete("w1", TENANT).isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fails closed when the resolver returns null")
        void failsClosedOnNullResolution() {
            callerIs("ghost@example.com", null);

            assertThat(hook.beforeCreate(record(ALICE), TENANT).isSuccess()).isFalse();
        }
    }
}
