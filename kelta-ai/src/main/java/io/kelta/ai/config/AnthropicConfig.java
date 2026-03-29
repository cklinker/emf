package io.kelta.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.OkHttpAnthropicClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiConfigProperties.class)
public class AnthropicConfig {

    @Bean
    public AnthropicClient anthropicClient(AiConfigProperties config) {
        return OkHttpAnthropicClient.builder()
                .apiKey(config.anthropic().apiKey())
                .build();
    }
}
