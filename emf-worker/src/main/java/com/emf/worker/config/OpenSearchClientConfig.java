package com.emf.worker.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchClientConfig {

    @Value("${emf.opensearch.url:http://localhost:9200}")
    private String opensearchUrl;

    @Bean
    public RestHighLevelClient opensearchClient() {
        return new RestHighLevelClient(
                RestClient.builder(HttpHost.create(opensearchUrl))
        );
    }
}
