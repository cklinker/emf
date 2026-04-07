package io.kelta.auth;

import io.kelta.auth.model.KeltaSession;
import io.kelta.auth.model.KeltaUserDetails;

import java.time.Instant;
import java.util.List;

/**
 * Pre-built domain objects for tests. Use these instead of constructing
 * objects manually so tests stay concise and don't break when constructors change.
 */
public final class TestFixtures {

    public static final String TENANT_ID = "tenant-1";
    public static final String USER_ID = "user-1";
    public static final String PROFILE_ID = "profile-1";
    public static final String EMAIL = "user@example.com";

    private TestFixtures() {}

    public static KeltaUserDetails userDetails() {
        return new KeltaUserDetails(
                USER_ID, EMAIL, TENANT_ID, PROFILE_ID,
                "default", "Test User",
                "$2a$10$dummyPasswordHash",
                true, false, false
        );
    }

    public static KeltaUserDetails userDetails(String email) {
        return new KeltaUserDetails(
                USER_ID, email, TENANT_ID, PROFILE_ID,
                "default", "Test User",
                "$2a$10$dummyPasswordHash",
                true, false, false
        );
    }

    public static KeltaUserDetails lockedUser() {
        return new KeltaUserDetails(
                USER_ID, EMAIL, TENANT_ID, PROFILE_ID,
                "default", "Locked User",
                "$2a$10$dummyPasswordHash",
                true, true, false
        );
    }

    public static KeltaUserDetails forcePasswordChangeUser() {
        return new KeltaUserDetails(
                USER_ID, EMAIL, TENANT_ID, PROFILE_ID,
                "default", "New User",
                "$2a$10$dummyPasswordHash",
                true, false, true
        );
    }

    public static KeltaSession session() {
        return new KeltaSession(
                EMAIL, TENANT_ID, "acme", PROFILE_ID,
                "default", "Test User", List.of("users"),
                "internal", Instant.now()
        );
    }

    public static KeltaSession session(String authSource) {
        return new KeltaSession(
                EMAIL, TENANT_ID, "acme", PROFILE_ID,
                "default", "Test User", List.of("users"),
                authSource, Instant.now()
        );
    }
}
