package io.kelta.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiConfigProperties.class)
public class AnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

    @Bean
    public AnthropicClient anthropicClient(AiConfigProperties config) {
        String apiKey = config.anthropic().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("Creating Anthropic client with configured API key ({}...)", apiKey.substring(0, Math.min(10, apiKey.length())));
            return AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();
        }
        // Fall back to ANTHROPIC_API_KEY env var via SDK's built-in env detection
        log.info("Creating Anthropic client from environment variable");
        return AnthropicOkHttpClient.fromEnv();
    }
}
