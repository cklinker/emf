package com.emf.controlplane.test;

import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Test helper for setting up tenant context in unit and integration tests.
 * Provides convenient methods to set and clear tenant context.
 */
public final class TestTenantContextHelper {

    public static final String DEFAULT_TENANT_ID = "test-tenant-001";
    public static final String DEFAULT_TENANT_SLUG = "test-org";
    public static final String DEFAULT_USER_ID = "test-user-001";

    private TestTenantContextHelper() {}

    /**
     * Sets up default tenant and user context for tests.
     */
    public static void setUpDefaultContext() {
        setUpContext(DEFAULT_TENANT_ID, DEFAULT_TENANT_SLUG, DEFAULT_USER_ID);
    }

    /**
     * Sets up tenant and user context with custom values.
     */
    public static void setUpContext(String tenantId, String tenantSlug, String userId) {
        TenantContextHolder.set(tenantId, tenantSlug);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null, "ROLE_ADMIN"));
    }

    /**
     * Clears all context (tenant + security). Call in @AfterEach.
     */
    public static void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }
}
