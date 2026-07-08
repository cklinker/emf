package io.kelta.worker.listener;

import io.kelta.runtime.router.UserIdResolver;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPreferenceGuardHook Tests")
class UserPreferenceGuardHookTest {

    private static final String ME = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";

    @Mock private UserIdResolver userIdResolver;
    @Mock private JdbcTemplate jdbcTemplate;

    private UserPreferenceGuardHook hook;

    @BeforeEach
    void setUp() {
        hook = new UserPreferenceGuardHook(userIdResolver, jdbcTemplate);
        lenient().when(userIdResolver.resolve(anyString(), any()))
                .thenAnswer(inv -> "me@example.com".equals(inv.getArgument(0))
                        ? ME : inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(String userIdHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userIdHeader != null) {
            request.addHeader("X-User-Id", userIdHeader);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("allows creating a row the caller owns")
    void allowsSelfCreate() {
        bindRequest("me@example.com");
        BeforeSaveResult result = hook.beforeCreate(Map.of("userId", ME), "t1");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects creating a row for another user")
    void rejectsCrossUserCreate() {
        bindRequest("me@example.com");
        BeforeSaveResult result = hook.beforeCreate(Map.of("userId", OTHER), "t1");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("rejects updating another user's row and re-owning to another user")
    void rejectsCrossUserUpdate() {
        bindRequest("me@example.com");
        assertThat(hook.beforeUpdate("p1", Map.of("value", "{}"),
                Map.of("userId", OTHER), "t1").isSuccess()).isFalse();
        assertThat(hook.beforeUpdate("p1", Map.of("userId", OTHER),
                Map.of("userId", ME), "t1").isSuccess()).isFalse();
        assertThat(hook.beforeUpdate("p1", Map.of("value", "{}"),
                Map.of("userId", ME), "t1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("delete looks up the owner and rejects a non-owner")
    void rejectsCrossUserDelete() {
        bindRequest("me@example.com");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("p1")))
                .thenReturn(List.of(OTHER));
        assertThat(hook.beforeDelete("p1", "t1").isSuccess()).isFalse();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("p2")))
                .thenReturn(List.of(ME));
        assertThat(hook.beforeDelete("p2", "t1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects a present-but-unresolvable identity (fail-closed)")
    void rejectsUnresolvableIdentity() {
        bindRequest("ghost@example.com");
        BeforeSaveResult result = hook.beforeCreate(Map.of("userId", ME), "t1");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("admits internal writes with no HTTP request identity")
    void admitsInternalTier() {
        // no request bound at all
        assertThat(hook.beforeCreate(Map.of("userId", OTHER), "t1").isSuccess()).isTrue();
        // request bound but no identity header (SCIM/internal)
        bindRequest(null);
        assertThat(hook.beforeCreate(Map.of("userId", OTHER), "t1").isSuccess()).isTrue();
    }
}
