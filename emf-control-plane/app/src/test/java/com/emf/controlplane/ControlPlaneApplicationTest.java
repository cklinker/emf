package com.emf.controlplane;

import com.emf.controlplane.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic smoke test to verify the application context loads correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ControlPlaneApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context loads without errors
    }
}
