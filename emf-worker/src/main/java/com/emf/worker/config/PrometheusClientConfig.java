package com.emf.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the Prometheus HTTP client.
 *
 * <p>Provides a {@link RestTemplate} bean configured with the Prometheus server
 * URL for querying metrics via the Prometheus HTTP API.
 *
 * @since 1.0.0
 */
@Configuration
public class PrometheusClientConfig {

    @Value("${emf.prometheus.url:http://prometheus.prometheus.svc.cluster.local:9090}")
    private String prometheusUrl;

    /**
     * Creates a RestTemplate configured to connect to the Prometheus server.
     *
     * @param builder the RestTemplateBuilder
     * @return a configured RestTemplate for Prometheus API calls
     */
    @Bean
    public RestTemplate prometheusRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri(prometheusUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
