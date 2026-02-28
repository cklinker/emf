package com.emf.runtime.router;

/**
 * Resolves user identifiers (e.g., email addresses) to platform_user UUIDs.
 *
 * <p>System collections such as {@code flow}, {@code dashboard}, and {@code report}
 * have foreign key constraints from {@code created_by}/{@code updated_by} to
 * {@code platform_user(id)}, requiring a UUID. The gateway forwards the JWT
 * subject (typically an email address) in the {@code X-User-Id} header, so
 * this resolver translates that identifier to the corresponding UUID before
 * the record is persisted.
 */
@FunctionalInterface
public interface UserIdResolver {

    /**
     * Resolves a user identifier to a platform_user UUID.
     *
     * @param userIdentifier the identifier from the X-User-Id header (typically an email)
     * @param tenantId       the tenant context for the lookup
     * @return the platform_user UUID, or the original identifier if resolution fails
     */
    String resolve(String userIdentifier, String tenantId);
}
