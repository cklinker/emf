package io.kelta.worker.service.storage;

import io.kelta.runtime.storage.RestExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link RestExecutor} implementation backed by Spring's {@link RestClient}, used by
 * {@code ExternalRestStorageAdapter} for external-rest connectors.
 *
 * <p>Non-2xx responses are NOT raised as exceptions (a no-op {@code onStatus} handler
 * suppresses the default error handling) — the adapter inspects the status itself
 * (e.g. 404 → empty). Only genuine transport failures propagate.
 */
@Component
public class RestClientExecutor implements RestExecutor {

    private final RestClient restClient;

    public RestClientExecutor() {
        this.restClient = RestClient.create();
    }

    @Override
    public RestResponse exchange(RestRequest request) {
        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.method()))
                .uri(request.url());
        if (request.headers() != null) {
            request.headers().forEach(spec::header);
        }
        if (request.body() != null) {
            spec.body(request.body());
        }
        ResponseEntity<String> response = spec.retrieve()
                .onStatus(status -> true, (req, res) -> { /* don't throw — adapter maps status */ })
                .toEntity(String.class);
        return new RestResponse(response.getStatusCode().value(), response.getBody());
    }
}
