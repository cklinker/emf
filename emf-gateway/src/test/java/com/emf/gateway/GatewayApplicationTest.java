package com.emf.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic application context test to verify the gateway application starts correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class GatewayApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the Spring Boot application context loads successfully
    }
}
