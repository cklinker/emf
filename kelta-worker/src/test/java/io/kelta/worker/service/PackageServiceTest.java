package io.kelta.worker.service;

import io.kelta.worker.repository.PackageRepository;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("PackageService")
class PackageServiceTest {

    private PackageRepository repository;
    private ObjectMapper objectMapper;
    private PackageService service;

    @BeforeEach
    void setUp() {
        repository = mock(PackageRepository.class);
        objectMapper = new ObjectMapper();
        service = new PackageService(repository, objectMapper);
    }

    @Nested
    @DisplayName("exportPackage")
    class ExportPackage {

        @Test
        @DisplayName("Should export selected collections with their fields")
        void shouldExportCollectionsWithFields() {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("name", "test-export");
            options.put("version", "1.0.0");
            options.put("collectionIds", List.of("col-1"));
            options.put("roleIds", List.of());
            options.put("policyIds", List.of());
            options.put("uiPageIds", List.of());
            options.put("uiMenuIds", List.of());

            Map<String, Object> collection = new LinkedHashMap<>();
            collection.put("id", "col-1");
            collection.put("name", "users");
            collection.put("tenant_id", "t1");
            when(repository.findCollectionsByIds("t1", List.of("col-1"))).thenReturn(List.of(collection));

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", "f-1");
            field.put("collection_id", "col-1");
            field.put("name", "email");
            field.put("type", "STRING");
            when(repository.findFieldsByCollectionIds("t1", List.of("col-1"))).thenReturn(List.of(field));

            when(repository.findRolesByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findPoliciesByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findRoutePoliciesByPolicyIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findFieldPoliciesByPolicyIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiPagesByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiMenusByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiMenuItemsByMenuIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.save(any(), any(), any(), any(), any(), any(), any())).thenReturn("hist-1");

            var result = service.exportPackage("t1", options);

            assertThat(result.get("name")).isEqualTo("test-export");
            assertThat(result.get("version")).isEqualTo("1.0.0");

            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) result.get("items");
            assertThat(items).hasSize(2);
            assertThat(items.get(0).get("type")).isEqualTo("COLLECTION");
            assertThat(items.get(1).get("type")).isEqualTo("FIELD");

            // Verify tenant_id is removed from exported data
            @SuppressWarnings("unchecked")
            var collectionData = (Map<String, Object>) items.get(0).get("data");
            assertThat(collectionData).doesNotContainKey("tenant_id");
        }

        @Test
        @DisplayName("Should export roles and policies")
        void shouldExportRolesAndPolicies() {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("name", "role-pkg");
            options.put("version", "1.0.0");
            options.put("collectionIds", List.of());
            options.put("roleIds", List.of("role-1"));
            options.put("policyIds", List.of("pol-1"));
            options.put("uiPageIds", List.of());
            options.put("uiMenuIds", List.of());

            when(repository.findCollectionsByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findFieldsByCollectionIds(eq("t1"), anyList())).thenReturn(List.of());

            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", "role-1");
            role.put("name", "admin");
            role.put("tenant_id", "t1");
            when(repository.findRolesByIds("t1", List.of("role-1"))).thenReturn(List.of(role));

            Map<String, Object> policy = new LinkedHashMap<>();
            policy.put("id", "pol-1");
            policy.put("name", "read-all");
            policy.put("tenant_id", "t1");
            when(repository.findPoliciesByIds("t1", List.of("pol-1"))).thenReturn(List.of(policy));

            when(repository.findRoutePoliciesByPolicyIds("t1", List.of("pol-1"))).thenReturn(List.of());
            when(repository.findFieldPoliciesByPolicyIds("t1", List.of("pol-1"))).thenReturn(List.of());
            when(repository.findUiPagesByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiMenusByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiMenuItemsByMenuIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.save(any(), any(), any(), any(), any(), any(), any())).thenReturn("hist-1");

            var result = service.exportPackage("t1", options);

            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) result.get("items");
            assertThat(items).hasSize(2);
            assertThat(items.stream().map(i -> i.get("type")).toList())
                    .containsExactly("ROLE", "POLICY");
        }

        @Test
        @DisplayName("Should record export in history")
        void shouldRecordExportHistory() {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("name", "hist-test");
            options.put("version", "2.0.0");
            options.put("description", "Test export");
            options.put("collectionIds", List.of());
            options.put("roleIds", List.of());
            options.put("policyIds", List.of());
            options.put("uiPageIds", List.of());
            options.put("uiMenuIds", List.of());

            when(repository.findCollectionsByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findFieldsByCollectionIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findRolesByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findPoliciesByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findRoutePoliciesByPolicyIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findFieldPoliciesByPolicyIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiPagesByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiMenusByIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.findUiMenuItemsByMenuIds(eq("t1"), anyList())).thenReturn(List.of());
            when(repository.save(any(), any(), any(), any(), any(), any(), any())).thenReturn("hist-1");

            service.exportPackage("t1", options);

            verify(repository).save(eq("t1"), eq("hist-test"), eq("2.0.0"), eq("Test export"),
                    eq("export"), eq("success"), any());
        }
    }

    @Nested
    @DisplayName("previewImport")
    class PreviewImport {

        @Test
        @DisplayName("Should identify new items as creates")
        void shouldIdentifyCreates() {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("items", List.of(
                    Map.of("type", "COLLECTION", "data", Map.of("id", "col-1", "name", "new_col"))
            ));

            when(repository.findCollectionsByNames("t1", List.of("new_col"))).thenReturn(List.of());

            var result = service.previewImport("t1", pkg);

            @SuppressWarnings("unchecked")
            var creates = (List<Map<String, Object>>) result.get("creates");
            assertThat(creates).hasSize(1);
            assertThat(creates.get(0).get("name")).isEqualTo("new_col");
        }

        @Test
        @DisplayName("Should identify existing items as conflicts")
        void shouldIdentifyConflicts() {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("items", List.of(
                    Map.of("type", "ROLE", "data", Map.of("id", "role-1", "name", "admin"))
            ));

            when(repository.findRolesByNames("t1", List.of("admin")))
                    .thenReturn(List.of(Map.of("id", "role-existing", "name", "admin")));

            var result = service.previewImport("t1", pkg);

            @SuppressWarnings("unchecked")
            var conflicts = (List<Map<String, Object>>) result.get("conflicts");
            assertThat(conflicts).hasSize(1);
        }

        @Test
        @DisplayName("Should be idempotent with no side effects")
        void shouldBeIdempotent() {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("items", List.of());

            service.previewImport("t1", pkg);
            service.previewImport("t1", pkg);

            // No save calls should be made
            verify(repository, never()).save(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("importPackage")
    class ImportPackage {

        @Test
        @DisplayName("Should return preview counts for dry run")
        void shouldReturnPreviewForDryRun() {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("name", "test-pkg");
            pkg.put("version", "1.0.0");
            pkg.put("items", List.of(
                    Map.of("type", "COLLECTION", "data", Map.of("id", "col-1", "name", "new_col"))
            ));

            when(repository.findCollectionsByNames("t1", List.of("new_col"))).thenReturn(List.of());

            var result = service.importPackage("t1", pkg, true);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("created")).isEqualTo(1);
            assertThat(result.get("errors")).isEqualTo(List.of());

            // Verify no actual imports happened
            verify(repository, never()).getJdbcTemplate();
        }

        @Test
        @DisplayName("Should record import in history")
        void shouldRecordImportHistory() {
            org.springframework.jdbc.core.JdbcTemplate mockJdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
            when(repository.getJdbcTemplate()).thenReturn(mockJdbc);
            when(repository.save(any(), any(), any(), any(), any(), any(), any())).thenReturn("hist-1");

            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("name", "import-test");
            pkg.put("version", "1.0.0");
            pkg.put("items", List.of(
                    Map.of("type", "ROLE", "data", Map.of("id", "role-1", "name", "viewer"))
            ));

            service.importPackage("t1", pkg, false);

            verify(repository).save(eq("t1"), eq("import-test"), eq("1.0.0"), any(),
                    eq("import"), any(), any());
        }
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistory {

        @Test
        @DisplayName("Should return mapped history rows")
        void shouldReturnMappedHistory() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "pkg-1");
            row.put("name", "export-2024");
            row.put("version", "1.0.0");
            row.put("type", "export");
            row.put("status", "success");
            row.put("created_at", java.sql.Timestamp.from(java.time.Instant.parse("2024-01-15T10:30:00Z")));
            row.put("items", "[{\"type\":\"collection\",\"id\":\"col-1\",\"name\":\"users\"}]");

            when(repository.findAllByTenantId("t1")).thenReturn(List.of(row));

            var result = service.getHistory("t1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("name")).isEqualTo("export-2024");
            assertThat(result.get(0).get("type")).isEqualTo("export");

            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) result.get(0).get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("name")).isEqualTo("users");
        }

        @Test
        @DisplayName("Should handle null items JSON")
        void shouldHandleNullItems() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "pkg-2");
            row.put("name", "empty");
            row.put("version", "1.0.0");
            row.put("type", "import");
            row.put("status", "failed");
            row.put("created_at", null);
            row.put("items", null);

            when(repository.findAllByTenantId("t1")).thenReturn(List.of(row));

            var result = service.getHistory("t1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("items")).isEqualTo(List.of());
        }

        @Test
        @DisplayName("Should return empty list when no history")
        void shouldReturnEmptyWhenNoHistory() {
            when(repository.findAllByTenantId("t1")).thenReturn(List.of());

            var result = service.getHistory("t1");
            assertThat(result).isEmpty();
        }
    }
}
