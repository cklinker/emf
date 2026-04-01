package io.kelta.auth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires full infrastructure (PostgreSQL, Redis). Run manually with docker-compose.")
class AuthApplicationTest {

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void contextLoads() {
        // Verifies the application context starts correctly
    }
}
