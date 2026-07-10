package io.kelta.gateway.websocket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Verifies conversation membership against the worker before a socket may
 * {@code chat.join} (telehealth slice 2). Reactive — never blocks the
 * WebSocket event loop. Positive AND negative results are cached ~30s
 * (a just-removed participant may receive id-only events for at most the
 * cache window; message bodies never ride the socket).
 */
@Component
public class ChatMembershipClient {

    private static final Logger log = LoggerFactory.getLogger(ChatMembershipClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final Cache<String, Boolean> membershipCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(50_000)
            .build();

    public ChatMembershipClient(
            WebClient.Builder webClientBuilder,
            @Value("${kelta.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();
    }

    /**
     * @param userIdentifier whatever identity the WS JWT carries — a
     *                       platform_user UUID (direct-login {@code sub}) or an
     *                       email (auth-code flow); the worker matches either.
     */
    public Mono<Boolean> isMember(String tenantId, String conversationId, String userIdentifier) {
        if (tenantId == null || conversationId == null
                || userIdentifier == null || userIdentifier.isBlank()) {
            return Mono.just(false);
        }
        String key = tenantId + ":" + conversationId + ":" + userIdentifier;
        Boolean cached = membershipCache.getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/chat/conversations/{id}/members")
                        .queryParam("tenantId", tenantId)
                        .queryParam("user", userIdentifier)
                        .build(conversationId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {})
                .map(body -> Boolean.TRUE.equals(body.get("member")))
                .timeout(TIMEOUT)
                .doOnNext(member -> membershipCache.put(key, member))
                .onErrorResume(e -> {
                    // Fail closed: a worker hiccup must not grant a join.
                    log.warn("Chat membership check failed for {}: {}", key, e.getMessage());
                    return Mono.just(false);
                });
    }
}
