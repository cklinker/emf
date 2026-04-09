package io.kelta.testharness;

import io.kelta.testharness.fixtures.AuthFixture;
import io.kelta.testharness.fixtures.TenantFixture;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * Base class for cross-service scenario tests.
 *
 * <p>Extends with {@link KeltaStackExtension} so the full stack starts before
 * any test in this hierarchy runs. Provides convenience accessors for fixtures
 * and REST clients pointed at each service.
 */
@ExtendWith(KeltaStackExtension.class)
public abstract class ScenarioBase {

    protected final AuthFixture   auth   = new AuthFixture();
    protected final TenantFixture tenants = new TenantFixture();

    protected RestClient workerClient() {
        return RestClient.builder().baseUrl(KeltaStack.workerBaseUrl()).build();
    }

    protected RestClient authClient() {
        return RestClient.builder().baseUrl(KeltaStack.authBaseUrl()).build();
    }

    protected RestClient gatewayClient() {
        return RestClient.builder().baseUrl(KeltaStack.gatewayBaseUrl()).build();
    }

    protected RestClient gatewayClientWithToken(String token) {
        return RestClient.builder()
                .baseUrl(KeltaStack.gatewayBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    /**
     * Polls a URL until the response matches the expected status, up to {@code maxAttempts}.
     * Useful for waiting for NATS-propagated changes (e.g. new routes in the gateway).
     */
    protected void waitForStatus(RestClient client, String uri, HttpStatusCode expected, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                var status = client.get().uri(uri).retrieve()
                        .onStatus(s -> !s.equals(expected), (req, resp) -> {})
                        .toBodilessEntity()
                        .getStatusCode();
                if (status.equals(expected)) return;
            } catch (Exception ignored) {
                // not ready yet
            }
            try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        throw new AssertionError("URL " + uri + " did not return " + expected + " after " + maxAttempts + " attempts");
    }
}
