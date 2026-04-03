package io.kelta.gateway.filter;

import io.opentelemetry.api.trace.Span;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Global filter that captures HTTP request and response bodies as OpenTelemetry
 * span attributes for observability.
 *
 * <p>Bodies are stored as {@code http.request.body} and {@code http.response.body}
 * span attributes, which are then visible in Tempo traces and the monitoring UI.
 *
 * <p>Safety guards:
 * <ul>
 *   <li>Only captures JSON content types</li>
 *   <li>Truncates bodies exceeding {@link #MAX_BODY_SIZE} bytes</li>
 *   <li>Skips requests with Content-Length exceeding the limit</li>
 * </ul>
 */
@Component
public class HttpBodyCaptureFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HttpBodyCaptureFilter.class);

    /** Maximum body size to capture (32 KB). */
    static final int MAX_BODY_SIZE = 32 * 1024;

    /** HTTP methods that may carry a request body. */
    private static final Set<HttpMethod> BODY_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

    /**
     * Runs after ObservabilityContextFilter (-90) so span context is available,
     * but before most other filters.
     */
    private static final int ORDER = -80;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // Decorate the response to capture the response body
        DataBufferFactory bufferFactory = response.bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!isCapturableContentType(getDelegate().getHeaders())) {
                    return super.writeWith(body);
                }

                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(dataBuffer -> {
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);

                            captureResponseBody(content);

                            return getDelegate().writeWith(
                                    Mono.just(bufferFactory.wrap(content)));
                        });
            }
        };

        // For methods with a request body, decorate the request to capture it
        if (BODY_METHODS.contains(request.getMethod())
                && isCapturableContentType(request.getHeaders())
                && !exceedsMaxSize(request.getHeaders())) {

            ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(request) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return DataBufferUtils.join(super.getBody())
                            .flatMapMany(dataBuffer -> {
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                DataBufferUtils.release(dataBuffer);

                                captureRequestBody(content);

                                // Re-emit the buffered content so downstream can read it
                                return Mono.just(bufferFactory.wrap(content));
                            });
                }
            };

            return chain.filter(exchange.mutate()
                    .request(decoratedRequest)
                    .response(decoratedResponse)
                    .build());
        }

        // No request body to capture — just decorate the response
        return chain.filter(exchange.mutate()
                .response(decoratedResponse)
                .build());
    }

    private void captureRequestBody(byte[] content) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) return;

        if (content.length == 0) return;

        if (content.length > MAX_BODY_SIZE) {
            span.setAttribute("http.request.body",
                    new String(content, 0, MAX_BODY_SIZE, StandardCharsets.UTF_8));
            span.setAttribute("http.request.body.truncated", true);
        } else {
            span.setAttribute("http.request.body",
                    new String(content, StandardCharsets.UTF_8));
        }
    }

    private void captureResponseBody(byte[] content) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) return;

        if (content.length == 0) return;

        if (content.length > MAX_BODY_SIZE) {
            span.setAttribute("http.response.body",
                    new String(content, 0, MAX_BODY_SIZE, StandardCharsets.UTF_8));
            span.setAttribute("http.response.body.truncated", true);
        } else {
            span.setAttribute("http.response.body",
                    new String(content, StandardCharsets.UTF_8));
        }
    }

    /**
     * Checks whether the content type is JSON (the only type we capture).
     */
    static boolean isCapturableContentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null) return false;
        return contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
    }

    /**
     * Checks whether the declared Content-Length exceeds the capture limit.
     */
    private boolean exceedsMaxSize(HttpHeaders headers) {
        long contentLength = headers.getContentLength();
        return contentLength > MAX_BODY_SIZE;
    }
}
