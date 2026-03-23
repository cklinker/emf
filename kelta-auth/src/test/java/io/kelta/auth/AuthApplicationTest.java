package io.kelta.auth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires full infrastructure (PostgreSQL, Redis). Run manually with docker-compose.")
class AuthApplicationTest {

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Test
    void contextLoads() {
        // Verifies the application context starts correctly
    }
}
