package io.kelta.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures a RestClient for querying OpenSearch REST API directly.
 * Replaces the heavyweight opensearch-rest-high-level-client for AOT compatibility.
 */
@Configuration
public class ObservabilityQueryConfig {

    @Value("${kelta.opensearch.url:http://localhost:9200}")
    private String opensearchUrl;

    @Bean("opensearchRestClient")
    public RestClient opensearchRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return RestClient.builder()
                .baseUrl(opensearchUrl)
                .requestFactory(factory)
                .build();
    }
}
