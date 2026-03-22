package io.kelta.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for {@link EncryptionService}.
 *
 * <p>Activated when {@code kelta.encryption.key} is set. The key must be a
 * Base64-encoded 32-byte (256-bit) AES key.
 *
 * <p>Generate a key:
 * <pre>{@code
 * openssl rand -base64 32
 * }</pre>
 */
@Configuration
@ConditionalOnProperty(name = "kelta.encryption.key")
public class EncryptionAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EncryptionAutoConfiguration.class);

    @Bean
    public EncryptionService encryptionService(Environment env) {
        String key = env.getProperty("kelta.encryption.key");
        log.info("EncryptionService initialized with AES-256-GCM");
        return new EncryptionService(key);
    }
}
