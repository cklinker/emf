package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.PackageService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("PackageController")
class PackageControllerTest {

    private PackageService packageService;
    private ObjectMapper objectMapper;
    private PackageController controller;

    @BeforeEach
    void setUp() {
        packageService = mock(PackageService.class);
        objectMapper = new ObjectMapper();
        controller = new PackageController(packageService, objectMapper);
        TenantContext.set("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistory {

        @Test
        @DisplayName("Should return package history in JSON:API format")
        void shouldReturnHistory() {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "pkg-1");
            entry.put("name", "test-export");
            entry.put("version", "1.0.0");
            entry.put("type", "export");
            entry.put("status", "success");
            entry.put("createdAt", "2024-01-15T10:30:00Z");
            entry.put("items", List.of());

            when(packageService.getHistory("t1")).thenReturn(List.of(entry));

            var response = controller.getHistory();
            assertThat(response.getStatusCode().value()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            var body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("data");

            @SuppressWarnings("unchecked")
            var data = (List<Map<String, Object>>) body.get("data");
            assertThat(data).hasSize(1);
            assertThat(data.get(0).get("type")).isEqualTo("packages");
        }

        @Test
        @DisplayName("Should return 400 when no tenant context")
        void shouldReturn400WhenNoTenant() {
            TenantContext.clear();
            var response = controller.getHistory();
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("exportPackage")
    class ExportPackage {

        @Test
        @DisplayName("Should export package with valid options")
        void shouldExportPackage() {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("name", "my-package");
            options.put("version", "1.0.0");
            options.put("collectionIds", List.of("col-1"));
            options.put("roleIds", List.of());
            options.put("policyIds", List.of());
            options.put("uiPageIds", List.of());
            options.put("uiMenuIds", List.of());

            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("name", "my-package");
            pkg.put("version", "1.0.0");
            pkg.put("items", List.of());

            when(packageService.exportPackage(eq("t1"), any())).thenReturn(pkg);

            var response = controller.exportPackage(options);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType().toString()).contains("application/json");
        }

        @Test
        @DisplayName("Should unwrap JSON:API envelope")
        void shouldUnwrapJsonApiEnvelope() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("name", "my-package");
            attributes.put("version", "1.0.0");
            attributes.put("collectionIds", List.of());
            attributes.put("roleIds", List.of());
            attributes.put("policyIds", List.of());
            attributes.put("uiPageIds", List.of());
            attributes.put("uiMenuIds", List.of());

            Map<String, Object> wrappedBody = Map.of(
                    "data", Map.of(
                            "type", "export",
                            "attributes", attributes
                    )
            );

            Map<String, Object> pkg = Map.of("name", "my-package", "version", "1.0.0", "items", List.of());
            when(packageService.exportPackage(eq("t1"), any())).thenReturn(pkg);

            var response = controller.exportPackage(wrappedBody);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(packageService).exportPackage(eq("t1"), argThat(opts ->
                    "my-package".equals(opts.get("name")) && "1.0.0".equals(opts.get("version"))
            ));
        }

        @Test
        @DisplayName("Should return 400 when name is missing")
        void shouldReturn400WhenNameMissing() {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("version", "1.0.0");

            var response = controller.exportPackage(options);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 when version is missing")
        void shouldReturn400WhenVersionMissing() {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("name", "test");

            var response = controller.exportPackage(options);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("previewImport")
    class PreviewImport {

        @Test
        @DisplayName("Should return preview for valid package file")
        void shouldReturnPreview() throws Exception {
            Map<String, Object> preview = Map.of(
                    "creates", List.of(Map.of("type", "collection", "id", "col-new", "name", "new_collection")),
                    "updates", List.of(),
                    "conflicts", List.of()
            );

            when(packageService.previewImport(eq("t1"), any())).thenReturn(preview);

            String packageJson = objectMapper.writeValueAsString(Map.of(
                    "name", "test-pkg",
                    "version", "1.0.0",
                    "items", List.of()
            ));
            MockMultipartFile file = new MockMultipartFile("file", "package.json",
                    "application/json", packageJson.getBytes());

            var response = controller.previewImport(file);
            assertThat(response.getStatusCode().value()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            var body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKeys("creates", "updates", "conflicts");
        }

        @Test
        @DisplayName("Should return 400 for empty file")
        void shouldReturn400ForEmptyFile() {
            MockMultipartFile file = new MockMultipartFile("file", "empty.json",
                    "application/json", new byte[0]);

            var response = controller.previewImport(file);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("importPackage")
    class ImportPackage {

        @Test
        @DisplayName("Should import package successfully")
        void shouldImportPackage() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("created", 2);
            result.put("updated", 0);
            result.put("skipped", 0);
            result.put("errors", List.of());

            when(packageService.importPackage(eq("t1"), any(), eq(false))).thenReturn(result);

            String packageJson = objectMapper.writeValueAsString(Map.of(
                    "name", "test-pkg",
                    "version", "1.0.0",
                    "items", List.of()
            ));
            MockMultipartFile file = new MockMultipartFile("file", "package.json",
                    "application/json", packageJson.getBytes());

            var response = controller.importPackage(file, false);
            assertThat(response.getStatusCode().value()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            var body = (Map<String, Object>) response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("created")).isEqualTo(2);
        }

        @Test
        @DisplayName("Should support dry run mode")
        void shouldSupportDryRun() throws Exception {
            Map<String, Object> result = Map.of(
                    "success", true, "created", 1, "updated", 0, "skipped", 0, "errors", List.of()
            );

            when(packageService.importPackage(eq("t1"), any(), eq(true))).thenReturn(result);

            String packageJson = objectMapper.writeValueAsString(Map.of(
                    "name", "test-pkg", "version", "1.0.0", "items", List.of()
            ));
            MockMultipartFile file = new MockMultipartFile("file", "package.json",
                    "application/json", packageJson.getBytes());

            var response = controller.importPackage(file, true);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(packageService).importPackage(eq("t1"), any(), eq(true));
        }
    }
}
