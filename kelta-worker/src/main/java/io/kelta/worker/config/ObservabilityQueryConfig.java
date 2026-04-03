package io.kelta.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures RestClient beans for querying the Grafana observability stack:
 * Tempo (traces), Loki (logs), and Mimir (metrics via PromQL).
 */
@Configuration
public class ObservabilityQueryConfig {

    @Value("${kelta.tempo.url:http://localhost:3200}")
    private String tempoUrl;

    @Value("${kelta.loki.url:http://localhost:3100}")
    private String lokiUrl;

    @Value("${kelta.mimir.url:http://localhost:8080/prometheus}")
    private String mimirUrl;

    @Bean("tempoRestClient")
    public RestClient tempoRestClient() {
        return buildRestClient(tempoUrl);
    }

    @Bean("lokiRestClient")
    public RestClient lokiRestClient() {
        return buildRestClient(lokiUrl);
    }

    @Bean("mimirRestClient")
    public RestClient mimirRestClient() {
        return buildRestClient(mimirUrl);
    }

    private RestClient buildRestClient(String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
