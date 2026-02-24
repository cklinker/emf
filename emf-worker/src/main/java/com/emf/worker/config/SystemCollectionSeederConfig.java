package com.emf.worker.config;

import com.emf.runtime.model.system.SystemCollectionSeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring configuration that creates and runs the {@link SystemCollectionSeeder}
 * on worker startup.
 *
 * <p>Runs at {@code @Order(5)} to ensure system collection definitions are
 * seeded into the database before the worker bootstrap service initializes
 * collection routes (which runs at a later order).
 *
 * @since 1.0.0
 */
@Configuration
public class SystemCollectionSeederConfig {

    private static final Logger log = LoggerFactory.getLogger(SystemCollectionSeederConfig.class);

    @Bean
    public SystemCollectionSeeder systemCollectionSeeder(JdbcTemplate jdbcTemplate,
                                                          ObjectMapper objectMapper) {
        return new SystemCollectionSeeder(jdbcTemplate, objectMapper);
    }

    @Bean
    @Order(5)
    public ApplicationRunner systemCollectionSeederRunner(SystemCollectionSeeder seeder) {
        return args -> {
            log.info("Running system collection seeder...");
            seeder.seed();
        };
    }
}
