package io.kelta.worker.listener;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhotoGuardHook Tests")
class PhotoGuardHookTest {

    private static final String ME = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";

    @Mock private UserIdResolver userIdResolver;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private QueryEngine queryEngine;

    private PhotoGuardHook hook;

    @BeforeEach
    void setUp() {
        hook = new PhotoGuardHook(userIdResolver, collectionRegistry, queryEngine);
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
        assertThat(hook.beforeCreate(Map.of("createdBy", ME), "t1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects creating a row for another member")
    void rejectsCrossMemberCreate() {
        bindRequest("me@example.com");
        assertThat(hook.beforeCreate(Map.of("createdBy", OTHER), "t1").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("allows a Guest create -- createdBy is stamped \"guest\" too, and they match")
    void allowsGuestCreate() {
        bindRequest("guest");
        BeforeSaveResult result = hook.beforeCreate(Map.of("createdBy", "guest"), "t1");
        assertThat(result.isSuccess())
                .as("a real bug: without the guest special case, this would fail closed as "
                        + "'unresolvable identity' and block every anonymous upload")
                .isTrue();
    }

    @Test
    @DisplayName("rejects updating another member's row and re-owning to another member")
    void rejectsCrossMemberUpdate() {
        bindRequest("me@example.com");
        assertThat(hook.beforeUpdate("p1", Map.of("caption", "nice view"),
                Map.of("createdBy", OTHER), "t1").isSuccess()).isFalse();
        assertThat(hook.beforeUpdate("p1", Map.of("createdBy", OTHER),
                Map.of("createdBy", ME), "t1").isSuccess()).isFalse();
        assertThat(hook.beforeUpdate("p1", Map.of("caption", "nice view"),
                Map.of("createdBy", ME), "t1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("delete looks the owner up through QueryEngine and rejects a non-owner")
    void rejectsCrossMemberDelete() {
        bindRequest("me@example.com");
        CollectionDefinition definition = mock(CollectionDefinition.class);
        when(collectionRegistry.get("facility-photos")).thenReturn(definition);

        when(queryEngine.getById(eq(definition), eq("p1")))
                .thenReturn(Optional.of(Map.of("createdBy", OTHER)));
        assertThat(hook.beforeDelete("p1", "t1").isSuccess()).isFalse();

        when(queryEngine.getById(eq(definition), eq("p2")))
                .thenReturn(Optional.of(Map.of("createdBy", ME)));
        assertThat(hook.beforeDelete("p2", "t1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("delete allows when the collection is not in the registry (fails open, logged)")
    void allowsDeleteWhenCollectionMissing() {
        bindRequest("me@example.com");
        when(collectionRegistry.get("facility-photos")).thenReturn(null);
        assertThat(hook.beforeDelete("p1", "t1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects a present-but-unresolvable identity that is not the guest sentinel")
    void rejectsUnresolvableIdentity() {
        bindRequest("ghost@example.com");
        assertThat(hook.beforeCreate(Map.of("createdBy", ME), "t1").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("admits internal writes with no HTTP request identity")
    void admitsInternalTier() {
        assertThat(hook.beforeCreate(Map.of("createdBy", OTHER), "t1").isSuccess()).isTrue();
        bindRequest(null);
        assertThat(hook.beforeCreate(Map.of("createdBy", OTHER), "t1").isSuccess()).isTrue();
    }
}
