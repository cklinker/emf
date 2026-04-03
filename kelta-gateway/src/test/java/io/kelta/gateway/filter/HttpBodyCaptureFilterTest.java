package io.kelta.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HttpBodyCaptureFilter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HttpBodyCaptureFilter Tests")
class HttpBodyCaptureFilterTest {

    @Mock
    private GatewayFilterChain filterChain;

    private HttpBodyCaptureFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HttpBodyCaptureFilter();
    }

    @Test
    @DisplayName("Should have order -80 (after ObservabilityContextFilter)")
    void shouldHaveCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-80);
    }

    @Nested
    @DisplayName("Content type detection")
    class ContentTypeDetection {

        @Test
        @DisplayName("Should identify JSON content type as capturable")
        void shouldCaptureJsonContentType() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            assertThat(HttpBodyCaptureFilter.isCapturableContentType(headers)).isTrue();
        }

        @Test
        @DisplayName("Should identify JSON UTF-8 content type as capturable")
        void shouldCaptureJsonUtf8ContentType() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            assertThat(HttpBodyCaptureFilter.isCapturableContentType(headers)).isTrue();
        }

        @Test
        @DisplayName("Should not capture text/plain content type")
        void shouldNotCaptureTextPlain() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            assertThat(HttpBodyCaptureFilter.isCapturableContentType(headers)).isFalse();
        }

        @Test
        @DisplayName("Should not capture multipart content type")
        void shouldNotCaptureMultipart() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            assertThat(HttpBodyCaptureFilter.isCapturableContentType(headers)).isFalse();
        }

        @Test
        @DisplayName("Should not capture when content type is absent")
        void shouldNotCaptureAbsentContentType() {
            HttpHeaders headers = new HttpHeaders();
            assertThat(HttpBodyCaptureFilter.isCapturableContentType(headers)).isFalse();
        }

        @Test
        @DisplayName("Should not capture octet-stream content type")
        void shouldNotCaptureOctetStream() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            assertThat(HttpBodyCaptureFilter.isCapturableContentType(headers)).isFalse();
        }
    }

    @Nested
    @DisplayName("GET requests (no request body)")
    class GetRequests {

        @Test
        @DisplayName("Should pass GET request through and decorate response only")
        void shouldPassGetRequestThrough() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();

            // Verify chain was called with a mutated exchange (response decorated)
            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(filterChain).filter(captor.capture());

            ServerWebExchange passedExchange = captor.getValue();
            // Response should be decorated
            assertThat(passedExchange.getResponse()).isInstanceOf(ServerHttpResponseDecorator.class);
        }
    }

    @Nested
    @DisplayName("POST requests with body")
    class PostRequests {

        @Test
        @DisplayName("Should decorate POST request with JSON content type")
        void shouldDecoratePostWithJsonBody() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"name\":\"test\"}");
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();

            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(filterChain).filter(captor.capture());

            ServerWebExchange passedExchange = captor.getValue();
            // Both request and response should be decorated
            assertThat(passedExchange.getResponse()).isInstanceOf(ServerHttpResponseDecorator.class);
        }

        @Test
        @DisplayName("Should not decorate POST request with non-JSON content type")
        void shouldNotDecoratePostWithNonJsonBody() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body("binary-data");
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();

            // Chain should still be called (response decorated, but request not)
            verify(filterChain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("Should skip request body capture when content length exceeds max size")
        void shouldSkipLargeRequestBody() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/data")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_LENGTH,
                            String.valueOf(HttpBodyCaptureFilter.MAX_BODY_SIZE + 1))
                    .body("{\"large\":true}");
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();

            verify(filterChain).filter(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("PUT and PATCH requests")
    class PutPatchRequests {

        @Test
        @DisplayName("Should decorate PUT request with JSON body")
        void shouldDecoratePutWithJsonBody() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .put("/api/users/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"name\":\"updated\"}");
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();

            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(filterChain).filter(captor.capture());
            assertThat(captor.getValue().getResponse()).isInstanceOf(ServerHttpResponseDecorator.class);
        }

        @Test
        @DisplayName("Should decorate PATCH request with JSON body")
        void shouldDecoratePatchWithJsonBody() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .patch("/api/users/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"name\":\"patched\"}");
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();

            verify(filterChain).filter(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("DELETE requests (no request body capture)")
    class DeleteRequests {

        @Test
        @DisplayName("Should not capture request body for DELETE")
        void shouldNotCaptureDeleteRequestBody() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .delete("/api/users/1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();

            // Response should still be decorated for response body capture
            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(filterChain).filter(captor.capture());
            assertThat(captor.getValue().getResponse()).isInstanceOf(ServerHttpResponseDecorator.class);
        }
    }

    @Nested
    @DisplayName("Response body capture")
    class ResponseBodyCapture {

        @Test
        @DisplayName("Should pass response body through when writing JSON response")
        void shouldPassResponseBodyThrough() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            byte[] responseBytes = "{\"users\":[]}".getBytes(StandardCharsets.UTF_8);

            // Simulate the chain writing a response body
            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenAnswer(invocation -> {
                        ServerWebExchange ex = invocation.getArgument(0);
                        ServerHttpResponse response = ex.getResponse();
                        response.setStatusCode(HttpStatus.OK);
                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        DataBuffer buffer = new DefaultDataBufferFactory().wrap(responseBytes);
                        return response.writeWith(Mono.just(buffer));
                    });

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should pass non-JSON response body through without capturing")
        void shouldPassNonJsonResponseThrough() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/index.html")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            byte[] responseBytes = "<html></html>".getBytes(StandardCharsets.UTF_8);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenAnswer(invocation -> {
                        ServerWebExchange ex = invocation.getArgument(0);
                        ServerHttpResponse response = ex.getResponse();
                        response.setStatusCode(HttpStatus.OK);
                        response.getHeaders().setContentType(MediaType.TEXT_HTML);
                        DataBuffer buffer = new DefaultDataBufferFactory().wrap(responseBytes);
                        return response.writeWith(Mono.just(buffer));
                    });

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should propagate filter chain errors")
        void shouldPropagateFilterChainErrors() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new RuntimeException("downstream error")));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }
}
