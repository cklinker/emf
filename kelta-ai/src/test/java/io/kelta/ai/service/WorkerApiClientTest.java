package io.kelta.ai.service;

import io.kelta.ai.config.AiConfigProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerApiClient")
class WorkerApiClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private WorkerApiClient client;

    @BeforeEach
    void setUp() {
        WebClient.Builder mockBuilder = mock(WebClient.Builder.class);
        when(mockBuilder.baseUrl(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(webClient);

        AiConfigProperties config = new AiConfigProperties(
                new AiConfigProperties.AnthropicProperties("key", "model", 4096, 0.7),
                "http://localhost:8080", 30000L);
        client = new WorkerApiClient(mockBuilder, config);
    }

    @SuppressWarnings("unchecked")
    private void stubPost(Map<String, Object> responseBody) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));
    }

    @SuppressWarnings("unchecked")
    private void stubPostWithErrorHandling(Map<String, Object> responseBody) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));
    }

    @SuppressWarnings("unchecked")
    private void stubGet(Map<String, Object> responseBody) {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));
    }

    @SuppressWarnings("unchecked")
    private void stubGetWithUri(Map<String, Object> responseBody) {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString(), any(Object[].class));
        doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));
    }

    @Nested
    @DisplayName("createCollection")
    class CreateCollection {

        @Test
        @DisplayName("sends JSON:API formatted request to worker")
        void sendsJsonApiRequest() {
            Map<String, Object> responseBody = Map.of("data", Map.of("id", "col-1", "type", "collections"));
            stubPostWithErrorHandling(responseBody);

            Map<String, Object> collectionData = Map.of("name", "accounts", "displayName", "Accounts");
            Map<String, Object> result = client.createCollection("tenant-1", "user-1", collectionData);

            assertThat(result).containsKey("data");
            verify(webClient).post();
        }
    }

    @Nested
    @DisplayName("listCollections")
    class ListCollections {

        @Test
        @DisplayName("returns collection data from response")
        void returnsCollectionData() {
            Map<String, Object> responseBody = Map.of(
                    "data", List.of(Map.of("id", "1", "attributes", Map.of("name", "accounts")))
            );
            stubGet(responseBody);

            List<Map<String, Object>> result = client.listCollections("tenant-1");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list when no data key")
        void returnsEmptyWhenNoData() {
            stubGet(Map.of());

            List<Map<String, Object>> result = client.listCollections("tenant-1");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listFields")
    class ListFields {

        @Test
        @DisplayName("returns fields for collection")
        void returnsFields() {
            Map<String, Object> responseBody = Map.of(
                    "data", List.of(Map.of("id", "f1", "attributes", Map.of("name", "email")))
            );
            stubGetWithUri(responseBody);

            List<Map<String, Object>> result = client.listFields("tenant-1", "col-1");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("createFields")
    class CreateFields {

        @Test
        @DisplayName("creates each field individually")
        void createsFieldsIndividually() {
            Map<String, Object> responseBody = Map.of("data", Map.of("id", "f1"));
            stubPostWithErrorHandling(responseBody);

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", "email");
            field.put("type", "EMAIL");

            List<String> errors = client.createFields("tenant-1", "user-1", "col-1", List.of(field));

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("maps nullable to required")
        void mapsNullableToRequired() {
            Map<String, Object> responseBody = Map.of("data", Map.of("id", "f1"));
            stubPostWithErrorHandling(responseBody);

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", "status");
            field.put("type", "STRING");
            field.put("nullable", false);
            field.put("defaultValue", "active");

            client.createFields("tenant-1", "user-1", "col-1", List.of(field));

            ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(requestBodySpec).bodyValue(bodyCaptor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) bodyCaptor.getValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            assertThat(attrs).containsEntry("required", true);
            assertThat(attrs).doesNotContainKey("nullable");
            assertThat(attrs).doesNotContainKey("defaultValue");
        }

        @Test
        @DisplayName("flattens referenceConfig into top-level properties")
        void flattensReferenceConfig() {
            Map<String, Object> responseBody = Map.of("data", Map.of("id", "f1"));
            stubPostWithErrorHandling(responseBody);

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", "account_id");
            field.put("type", "MASTER_DETAIL");
            field.put("referenceConfig", Map.of(
                    "targetCollection", "accounts",
                    "relationshipName", "account_contacts",
                    "cascadeDelete", true
            ));

            client.createFields("tenant-1", "user-1", "col-1", List.of(field));

            ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(requestBodySpec).bodyValue(bodyCaptor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) bodyCaptor.getValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            assertThat(attrs).containsEntry("referenceTarget", "accounts");
            assertThat(attrs).containsEntry("relationshipName", "account_contacts");
            assertThat(attrs).doesNotContainKey("referenceConfig");
        }
    }

    @Nested
    @DisplayName("createPageLayout")
    class CreatePageLayout {

        @Test
        @DisplayName("sends layout creation request")
        void sendsLayoutRequest() {
            stubPost(Map.of("data", Map.of("id", "l1")));

            Map<String, Object> result = client.createPageLayout("tenant-1", "user-1",
                    Map.of("name", "Default", "layoutType", "DETAIL"));

            assertThat(result).containsKey("data");
            verify(webClient).post();
        }
    }

    @Nested
    @DisplayName("listMenus")
    class ListMenus {

        @Test
        @DisplayName("returns menus from response")
        void returnsMenus() {
            Map<String, Object> responseBody = Map.of(
                    "data", List.of(Map.of("id", "m1", "type", "ui-menus"))
            );
            stubGet(responseBody);

            List<Map<String, Object>> result = client.listMenus("tenant-1");

            assertThat(result).hasSize(1);
        }
    }
}
