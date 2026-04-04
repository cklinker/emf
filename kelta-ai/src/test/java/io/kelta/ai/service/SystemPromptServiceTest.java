package io.kelta.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemPromptService")
class SystemPromptServiceTest {

    @Mock
    private WorkerApiClient workerApiClient;

    private SystemPromptService service;

    @BeforeEach
    void setUp() {
        service = new SystemPromptService(workerApiClient);
    }

    @Test
    @DisplayName("includes static prompt with platform instructions")
    void includesStaticPrompt() {
        when(workerApiClient.listCollections("tenant-1")).thenReturn(List.of());

        String prompt = service.buildSystemPrompt("tenant-1", null, null);

        assertThat(prompt).contains("Kelta platform");
        assertThat(prompt).contains("propose_collection");
        assertThat(prompt).contains("propose_layout");
        assertThat(prompt).contains("MASTER_DETAIL");
    }

    @Test
    @DisplayName("includes existing collections in tenant context")
    void includesExistingCollections() {
        Map<String, Object> collection = Map.of(
                "attributes", Map.of(
                        "name", "accounts",
                        "displayName", "Accounts",
                        "description", "Customer accounts"
                )
        );
        when(workerApiClient.listCollections("tenant-1")).thenReturn(List.of(collection));

        String prompt = service.buildSystemPrompt("tenant-1", null, null);

        assertThat(prompt).contains("Accounts");
        assertThat(prompt).contains("`accounts`");
        assertThat(prompt).contains("Customer accounts");
    }

    @Test
    @DisplayName("shows fresh setup message when no collections exist")
    void showsFreshSetupMessage() {
        when(workerApiClient.listCollections("tenant-1")).thenReturn(List.of());

        String prompt = service.buildSystemPrompt("tenant-1", null, null);

        assertThat(prompt).contains("no collections yet");
    }

    @Nested
    @DisplayName("collection context")
    class CollectionContext {

        @Test
        @DisplayName("includes field table when viewing a collection")
        void includesFieldTable() {
            when(workerApiClient.listCollections("tenant-1")).thenReturn(List.of());

            Map<String, Object> field = Map.of(
                    "attributes", Map.of(
                            "name", "email",
                            "type", "EMAIL",
                            "required", true
                    )
            );
            when(workerApiClient.listFields("tenant-1", "accounts"))
                    .thenReturn(List.of(field));

            String prompt = service.buildSystemPrompt("tenant-1", "collection", "accounts");

            assertThat(prompt).contains("Currently Viewing Collection");
            assertThat(prompt).contains("email");
            assertThat(prompt).contains("EMAIL");
            assertThat(prompt).contains("Yes");
        }

        @Test
        @DisplayName("handles field fetch errors gracefully")
        void handlesFieldFetchError() {
            when(workerApiClient.listCollections("tenant-1")).thenReturn(List.of());
            when(workerApiClient.listFields("tenant-1", "broken"))
                    .thenThrow(new RuntimeException("Connection refused"));

            String prompt = service.buildSystemPrompt("tenant-1", "collection", "broken");

            // Should not throw, just skip field context
            assertThat(prompt).contains("Kelta platform");
        }
    }

    @Test
    @DisplayName("handles tenant context build failure gracefully")
    void handlesTenantContextError() {
        when(workerApiClient.listCollections("tenant-1"))
                .thenThrow(new RuntimeException("Worker API down"));

        String prompt = service.buildSystemPrompt("tenant-1", null, null);

        assertThat(prompt).contains("Kelta platform");
    }
}
