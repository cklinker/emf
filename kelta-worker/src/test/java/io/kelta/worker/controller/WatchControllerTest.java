package io.kelta.worker.controller;

import tools.jackson.databind.ObjectMapper;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.controller.WatchController.UpdateWatchRequest;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.repository.Watch;
import io.kelta.worker.repository.WatchRepository;
import io.kelta.worker.repository.WatchTarget;
import io.kelta.worker.repository.WatchTargetRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.billing.EntitlementService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers re-pointing a watch at another target, and support staff acting on a
 * member's watch.
 *
 * <p>Both were previously impossible: {@code targetId} was absent from the update
 * contract, and {@code update}/{@code delete} ignored the support permission that
 * {@code list} and {@code create} already honoured — so correcting a member's
 * watch meant a direct database write, skipping every guard asserted here.
 */
@DisplayName("WatchController — retarget and support writes")
class WatchControllerTest {

    private static final String TENANT = "t1";
    private static final String OWNER = "member-1";
    private static final String OTHER = "member-2";
    private static final String STAFF = "staff-1";
    private static final String WATCH_ID = "watch-1";

    private WatchRepository watchRepository;
    private WatchTargetRepository targetRepository;
    private QueryEngine queryEngine;
    private CerbosPermissionResolver permissionResolver;
    private BootstrapRepository bootstrapRepository;
    private WatchController controller;

    @BeforeEach
    void setUp() {
        watchRepository = mock(WatchRepository.class);
        targetRepository = mock(WatchTargetRepository.class);
        queryEngine = mock(QueryEngine.class);
        permissionResolver = mock(CerbosPermissionResolver.class);
        bootstrapRepository = mock(BootstrapRepository.class);

        CollectionRegistry registry = mock(CollectionRegistry.class);
        when(registry.get(WatchController.COLLECTION)).thenReturn(mock(CollectionDefinition.class));

        UserIdResolver userIdResolver = mock(UserIdResolver.class);
        when(userIdResolver.resolve(anyString(), any())).thenAnswer(i -> i.getArgument(0));

        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.intLimit(anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(i -> (int) i.getArgument(3));

        controller = new WatchController(watchRepository, targetRepository, queryEngine,
                registry, entitlements, userIdResolver, permissionResolver,
                bootstrapRepository, new ObjectMapper());
        TenantContext.set(TENANT);

        when(queryEngine.update(any(), anyString(), any()))
                .thenAnswer(i -> Optional.of(Map.of("id", i.getArgument(1))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private HttpServletRequest requestFrom(String actor, boolean staff) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(actor);
        when(request.getHeader("X-User-Type")).thenReturn(staff ? "INTERNAL" : "PORTAL");
        when(permissionResolver.getProfileId(request)).thenReturn(staff ? "profile-staff" : "profile-member");
        when(bootstrapRepository.findProfileSystemPermissions(anyString())).thenReturn(
                staff
                        ? List.of(Map.of("permission_name", WatchController.SUPPORT_PERMISSION,
                                         "granted", Boolean.TRUE))
                        : List.of());
        return request;
    }

    private void watchOwnedBy(String member) {
        Watch watch = new Watch(WATCH_ID, TENANT, member, "target-old",
                null, null, Watch.STATUS_ACTIVE, null);
        when(watchRepository.findByMember(TENANT, member)).thenReturn(List.of(watch));
    }

    private void targetExists(String id, boolean active) {
        when(targetRepository.findById(TENANT, id))
                .thenReturn(Optional.of(
                        new WatchTarget(id, TENANT, "src", "ext", "Name", "cat", null, active)));
    }

    @Test
    @DisplayName("re-points a watch at another active target")
    void retargets() {
        watchOwnedBy(OWNER);
        targetExists("target-new", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);

        controller.update(WATCH_ID,
                new UpdateWatchRequest("target-new", null, null, null, null),
                requestFrom(OWNER, false));

        verify(queryEngine).update(any(), eq(WATCH_ID), data.capture());
        assertThat(data.getValue()).containsEntry("targetId", "target-new");
    }

    @Test
    @DisplayName("rejects an unknown target rather than storing a dangling id")
    void unknownTarget() {
        watchOwnedBy(OWNER);
        when(targetRepository.findById(TENANT, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(WATCH_ID,
                new UpdateWatchRequest("nope", null, null, null, null),
                requestFrom(OWNER, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown target");
        verify(queryEngine, never()).update(any(), anyString(), any());
    }

    @Test
    @DisplayName("rejects a retired target — the same guard create applies")
    void retiredTarget() {
        watchOwnedBy(OWNER);
        targetExists("target-retired", false);

        assertThatThrownBy(() -> controller.update(WATCH_ID,
                new UpdateWatchRequest("target-retired", null, null, null, null),
                requestFrom(OWNER, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not currently watchable");
        verify(queryEngine, never()).update(any(), anyString(), any());
    }

    @Test
    @DisplayName("a member naming someone else is refused")
    void memberCannotNameAnother() {
        assertThatThrownBy(() -> controller.update(WATCH_ID,
                new UpdateWatchRequest(null, null, null, "PAUSED", OTHER),
                requestFrom(OWNER, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not permitted");
    }

    @Test
    @DisplayName("support staff may update a named member's watch")
    void supportCanUpdate() {
        watchOwnedBy(OTHER);
        targetExists("target-new", true);

        controller.update(WATCH_ID,
                new UpdateWatchRequest("target-new", null, null, null, OTHER),
                requestFrom(STAFF, true));

        verify(queryEngine).update(any(), eq(WATCH_ID), any());
    }

    @Test
    @DisplayName("support staff may delete a named member's watch")
    void supportCanDelete() {
        watchOwnedBy(OTHER);

        controller.delete(WATCH_ID, OTHER, requestFrom(STAFF, true));

        verify(queryEngine).delete(any(), eq(WATCH_ID));
    }

    @Test
    @DisplayName("a member naming someone else on delete is refused")
    void memberCannotDeleteAnothers() {
        assertThatThrownBy(() -> controller.delete(WATCH_ID, OTHER, requestFrom(OWNER, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not permitted");
        verify(queryEngine, never()).delete(any(), anyString());
    }

    @Test
    @DisplayName("an omitted targetId leaves the target alone")
    void omittedTargetIdIsNotAChange() {
        watchOwnedBy(OWNER);

        controller.update(WATCH_ID,
                new UpdateWatchRequest(null, null, null, "PAUSED", null),
                requestFrom(OWNER, false));

        verify(targetRepository, never()).findById(anyString(), anyString());
    }
}
