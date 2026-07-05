package io.kelta.worker.interceptor;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldDefinitionBuilder;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.FieldMaskingService;
import io.kelta.worker.service.RecordMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaskedFieldPredicateInterceptor")
class MaskedFieldPredicateInterceptorTest {

    @Mock private CollectionRegistry collectionRegistry;
    @Mock private CerbosPermissionResolver permissionResolver;
    @Mock private CerbosAuthorizationService authzService;

    private MaskedFieldPredicateInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        RecordMaskingService recordMaskingService =
                new RecordMaskingService(authzService, new FieldMaskingService());
        interceptor = new MaskedFieldPredicateInterceptor(
                collectionRegistry, recordMaskingService, permissionResolver, true);
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest identifiedGet(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        lenient().when(permissionResolver.hasIdentity(request)).thenReturn(true);
        lenient().when(permissionResolver.getEmail(request)).thenReturn("user@example.com");
        lenient().when(permissionResolver.getProfileId(request)).thenReturn("profile-1");
        lenient().when(permissionResolver.getTenantId(request)).thenReturn("tenant-1");
        return request;
    }

    private static FieldDefinition maskedField(String name) {
        return new FieldDefinitionBuilder()
                .name(name)
                .type(FieldType.STRING)
                .fieldTypeConfig(Map.of(FieldMaskingService.CONFIG_KEY, Map.of("type", "LAST4")))
                .build();
    }

    /** contacts with a maskable ssn field and an unconfigured name field. */
    private static CollectionDefinition maskedContacts() {
        return CollectionDefinition.builder()
                .name("contacts")
                .displayName("Contacts")
                .addField(FieldDefinition.requiredString("name"))
                .addField(maskedField("ssn"))
                .build();
    }

    /** A user collection named like the reserved `/api/flows` prefix, with a maskable ssn. */
    private static CollectionDefinition maskedFlowsheet() {
        return CollectionDefinition.builder()
                .name("flowsheet")
                .displayName("Flowsheet")
                .addField(FieldDefinition.requiredString("name"))
                .addField(maskedField("ssn"))
                .build();
    }

    private void givenSsnMaskedForUser() {
        when(collectionRegistry.get("contacts")).thenReturn(maskedContacts());
        when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("contacts"),
                anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                .thenReturn(List.of());
    }

    @Nested
    @DisplayName("Blocked probes")
    class BlockedProbes {

        @Test
        @DisplayName("Masked filter param → 403 with the exact uniform body and preHandle false")
        void maskedFilterParamBlocked() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");
            request.addParameter("filter[ssn]", "123*");
            givenSsnMaskedForUser();

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getContentAsString())
                    .isEqualTo(MaskedFieldPredicateInterceptor.ERROR_BODY);
        }

        @Test
        @DisplayName("Masked filter param with operator suffix → 403")
        void maskedFilterWithOperatorBlocked() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");
            request.addParameter("filter[ssn][like]", "1*");
            givenSsnMaskedForUser();

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString())
                    .isEqualTo(MaskedFieldPredicateInterceptor.ERROR_BODY);
        }

        @Test
        @DisplayName("Descending sort on a masked field → 403")
        void maskedDescendingSortBlocked() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");
            request.addParameter("sort", "-ssn");
            givenSsnMaskedForUser();

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("Masked field inside a comma-separated sort list → 403")
        void maskedSortInCommaListBlocked() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");
            request.addParameter("sort", "name,-ssn");
            givenSsnMaskedForUser();

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("Look-alike reserved-prefix collection (flowsheet) is NOT skipped as metadata → still guarded")
        void lookAlikeReservedPrefixIsStillGuarded() throws Exception {
            // `/api/flowsheet` merely starts with the `/api/flows` reserved token — it is a
            // real user collection and must NOT be exempted from the masked-field guard.
            MockHttpServletRequest request = identifiedGet("/api/flowsheet");
            request.addParameter("filter[ssn]", "x");
            when(collectionRegistry.get("flowsheet")).thenReturn(maskedFlowsheet());
            when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("flowsheet"),
                    anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                    .thenReturn(List.of());

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString())
                    .isEqualTo(MaskedFieldPredicateInterceptor.ERROR_BODY);
            // Guard evaluated the user collection, not any reserved metadata short-circuit.
            verify(collectionRegistry).get("flowsheet");
        }

        @Test
        @DisplayName("Child-listing path resolves the child collection")
        void childListingPathResolvesChild() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/accounts/123/contacts");
            request.addParameter("filter[ssn]", "9*");
            givenSsnMaskedForUser();

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            verify(collectionRegistry).get("contacts");
        }
    }

    @Nested
    @DisplayName("Allowed requests")
    class AllowedRequests {

        @Test
        @DisplayName("Unmask-allowed user passes the same probe")
        void unmaskedUserPasses() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");
            request.addParameter("filter[ssn]", "123*");
            when(collectionRegistry.get("contacts")).thenReturn(maskedContacts());
            when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("contacts"),
                    anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                    .thenReturn(List.of("ssn"));

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("Filter on a non-maskable field passes without an authz call")
        void unmaskedFieldReferencedPasses() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");
            request.addParameter("filter[name]", "John");
            when(collectionRegistry.get("contacts")).thenReturn(maskedContacts());

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(authzService);
        }

        @Test
        @DisplayName("Request without filter or sort passes without touching the registry")
        void noFilterOrSortPasses() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(collectionRegistry, authzService);
        }
    }

    @Nested
    @DisplayName("Skipped surfaces")
    class SkippedSurfaces {

        @Test
        @DisplayName("Non-GET requests pass untouched")
        void nonGetPasses() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/contacts");
            request.addParameter("filter[ssn]", "1*");

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(permissionResolver, collectionRegistry, authzService);
        }

        @Test
        @DisplayName("Admin paths pass untouched")
        void adminPathPasses() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/contacts");
            request.addParameter("filter[ssn]", "1*");

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(permissionResolver, collectionRegistry, authzService);
        }

        @Test
        @DisplayName("Metadata paths pass untouched")
        void metadataPathPasses() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections");
            request.addParameter("filter[ssn]", "1*");

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(permissionResolver, collectionRegistry, authzService);
        }

        @Test
        @DisplayName("Identity-less (internal) requests pass untouched")
        void noIdentityPasses() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/contacts");
            request.addParameter("filter[ssn]", "1*");
            when(permissionResolver.hasIdentity(request)).thenReturn(false);

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(collectionRegistry, authzService);
        }

        @Test
        @DisplayName("Unknown collection passes with no authz interaction")
        void unknownCollectionPasses() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/unknown-things");
            request.addParameter("filter[ssn]", "1*");
            when(collectionRegistry.get("unknown-things")).thenReturn(null);

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(authzService);
        }

        @Test
        @DisplayName("Collection without masking config passes with no authz interaction")
        void noMaskingConfigPasses() throws Exception {
            MockHttpServletRequest request = identifiedGet("/api/contacts");
            request.addParameter("filter[ssn]", "1*");
            CollectionDefinition plain = CollectionDefinition.builder()
                    .name("contacts")
                    .displayName("Contacts")
                    .addField(FieldDefinition.requiredString("name"))
                    .addField(FieldDefinition.requiredString("ssn"))
                    .build();
            when(collectionRegistry.get("contacts")).thenReturn(plain);

            boolean proceed = interceptor.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verify(authzService, never()).batchCheckFieldAccess(
                    any(), any(), any(), any(), anyList(), any());
        }

        @Test
        @DisplayName("Everything passes when permissions are disabled")
        void permissionsDisabledPasses() throws Exception {
            MaskedFieldPredicateInterceptor disabled = new MaskedFieldPredicateInterceptor(
                    collectionRegistry,
                    new RecordMaskingService(authzService, new FieldMaskingService()),
                    permissionResolver, false);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/contacts");
            request.addParameter("filter[ssn]", "1*");

            boolean proceed = disabled.preHandle(request, response, new Object());

            assertThat(proceed).isTrue();
            verifyNoInteractions(permissionResolver, collectionRegistry, authzService);
        }
    }
}
