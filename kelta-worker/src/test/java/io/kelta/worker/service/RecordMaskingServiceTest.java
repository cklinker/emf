package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldDefinitionBuilder;
import io.kelta.runtime.model.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordMaskingService")
class RecordMaskingServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PROFILE_ID = "profile-1";
    private static final String TENANT_ID = "tenant-1";

    @Mock
    private CerbosAuthorizationService authzService;

    private RecordMaskingService service;

    @BeforeEach
    void setUp() {
        service = new RecordMaskingService(authzService, new FieldMaskingService());
    }

    private static FieldDefinition maskedField(String name, FieldType type, String maskType) {
        return new FieldDefinitionBuilder()
                .name(name)
                .type(type)
                .fieldTypeConfig(Map.of(FieldMaskingService.CONFIG_KEY, Map.of("type", maskType)))
                .build();
    }

    /** contacts: ssn (LAST4) + email (EMAIL) maskable, name unconfigured. */
    private static CollectionDefinition maskedCollection() {
        return CollectionDefinition.builder()
                .name("contacts")
                .displayName("Contacts")
                .addField(FieldDefinition.requiredString("name"))
                .addField(maskedField("ssn", FieldType.STRING, "LAST4"))
                .addField(maskedField("email", FieldType.EMAIL, "EMAIL"))
                .build();
    }

    private static CollectionDefinition plainCollection() {
        return CollectionDefinition.builder()
                .name("notes")
                .displayName("Notes")
                .addField(FieldDefinition.requiredString("title"))
                .build();
    }

    @Nested
    @DisplayName("maskableConfigs")
    class MaskableConfigs {

        @Test
        @DisplayName("Should map only masking-configured fields by name")
        void mapsOnlyConfiguredFields() {
            Map<String, FieldMaskingService.MaskingConfig> configs =
                    service.maskableConfigs(maskedCollection());

            assertThat(configs).containsOnlyKeys("ssn", "email");
            assertThat(configs.get("ssn").type()).isEqualTo(FieldMaskingService.MaskType.LAST4);
        }

        @Test
        @DisplayName("Should be empty for a collection without masking config")
        void emptyForPlainCollection() {
            assertThat(service.maskableConfigs(plainCollection())).isEmpty();
        }

        @Test
        @DisplayName("Should be empty for a null definition")
        void emptyForNullDefinition() {
            assertThat(service.maskableConfigs(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("maskedFieldsFor")
    class MaskedFieldsFor {

        @Test
        @DisplayName("Should return maskable minus unmask-allowed fields")
        void returnsMaskableMinusAllowed() {
            when(authzService.batchCheckFieldAccess(eq(EMAIL), eq(PROFILE_ID), eq(TENANT_ID),
                    eq("contacts"), anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                    .thenReturn(List.of("email"));

            Set<String> masked = service.maskedFieldsFor(
                    EMAIL, PROFILE_ID, TENANT_ID, "contacts", Set.of("ssn", "email"));

            assertThat(masked).containsExactly("ssn");
        }

        @Test
        @DisplayName("Should short-circuit on an empty maskable set without calling Cerbos")
        void emptyMaskableSkipsAuthz() {
            Set<String> masked = service.maskedFieldsFor(
                    EMAIL, PROFILE_ID, TENANT_ID, "contacts", Set.of());

            assertThat(masked).isEmpty();
            verifyNoInteractions(authzService);
        }
    }

    @Nested
    @DisplayName("maskRows")
    class MaskRows {

        @Test
        @DisplayName("Should mask only unmask-denied fields in place and return the masked set")
        void masksOnlyDeniedFields() {
            when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("contacts"),
                    anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                    .thenReturn(List.of("email"));

            Map<String, Object> row = new HashMap<>();
            row.put("name", "John");
            row.put("ssn", "123-45-6789");
            row.put("email", "john@example.com");

            Set<String> masked = service.maskRows(
                    maskedCollection(), List.of(row), EMAIL, PROFILE_ID, TENANT_ID);

            assertThat(masked).containsExactly("ssn");
            assertThat(row.get("ssn")).isEqualTo("***-**-6789");
            assertThat(row.get("email")).isEqualTo("john@example.com");
            assertThat(row.get("name")).isEqualTo("John");
        }

        @Test
        @DisplayName("Should leave null values null")
        void leavesNullsAlone() {
            when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("contacts"),
                    anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                    .thenReturn(List.of());

            Map<String, Object> row = new HashMap<>();
            row.put("ssn", null);
            row.put("email", "john@example.com");

            service.maskRows(maskedCollection(), List.of(row), EMAIL, PROFILE_ID, TENANT_ID);

            assertThat(row.get("ssn")).isNull();
            assertThat(row.get("email")).isEqualTo("j***@example.com");
        }

        @Test
        @DisplayName("Should make no authz call when the collection has no masking config")
        void noAuthzCallWithoutMaskingConfig() {
            Map<String, Object> row = new HashMap<>(Map.of("title", "hello"));

            Set<String> masked = service.maskRows(
                    plainCollection(), List.of(row), EMAIL, PROFILE_ID, TENANT_ID);

            assertThat(masked).isEmpty();
            assertThat(row).containsEntry("title", "hello");
            verify(authzService, never()).batchCheckFieldAccess(
                    any(), any(), any(), any(), anyList(), any());
        }

        @Test
        @DisplayName("Should short-circuit on empty rows without calling Cerbos")
        void emptyRowsShortCircuit() {
            Set<String> masked = service.maskRows(
                    maskedCollection(), List.of(), EMAIL, PROFILE_ID, TENANT_ID);

            assertThat(masked).isEmpty();
            verifyNoInteractions(authzService);
        }

        @Test
        @DisplayName("Should return empty and leave rows untouched when the user may unmask everything")
        void allAllowedLeavesRowsUntouched() {
            when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("contacts"),
                    anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                    .thenReturn(List.of("ssn", "email"));

            Map<String, Object> row = new HashMap<>();
            row.put("ssn", "123-45-6789");
            row.put("email", "john@example.com");

            Set<String> masked = service.maskRows(
                    maskedCollection(), List.of(row), EMAIL, PROFILE_ID, TENANT_ID);

            assertThat(masked).isEmpty();
            assertThat(row.get("ssn")).isEqualTo("123-45-6789");
            assertThat(row.get("email")).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("Should mask every configured field when Cerbos allows nothing (fail-closed)")
        void cerbosFailureMasksEverything() {
            when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("contacts"),
                    anyList(), eq(RecordMaskingService.UNMASK_ACTION)))
                    .thenReturn(List.of());

            Map<String, Object> row = new HashMap<>();
            row.put("ssn", "123-45-6789");
            row.put("email", "john@example.com");

            Set<String> masked = service.maskRows(
                    maskedCollection(), List.of(row), EMAIL, PROFILE_ID, TENANT_ID);

            assertThat(masked).containsExactlyInAnyOrder("ssn", "email");
            assertThat(row.get("ssn")).isEqualTo("***-**-6789");
            assertThat(row.get("email")).isEqualTo("j***@example.com");
        }
    }
}
