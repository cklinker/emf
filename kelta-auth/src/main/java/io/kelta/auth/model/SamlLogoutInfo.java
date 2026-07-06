package io.kelta.auth.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The minimal SAML assertion state captured at login so the platform can later
 * emit an SP-initiated {@code LogoutRequest} to the IdP (Single Logout).
 *
 * <p>The {@link io.kelta.auth.federation.SamlFederatedLoginSuccessHandler} replaces
 * the {@code Saml2Authentication} with a {@code UsernamePasswordAuthenticationToken}
 * (so the Authorization Server mints platform tokens), which discards the NameID and
 * SessionIndex(es) that an SP-initiated {@code LogoutRequest} must carry. This record
 * preserves them in the HTTP session under {@link #SESSION_ATTRIBUTE} so the logout
 * initiator can reconstruct a {@code Saml2Authentication} and resolve a signed
 * {@code LogoutRequest} for the IdP's SingleLogoutService.
 *
 * @param registrationId the relying-party registration id ({@code tenantId:providerId})
 * @param nameId         the SAML NameID of the authenticated subject
 * @param sessionIndexes the SessionIndex values from the assertion (may be empty)
 */
public record SamlLogoutInfo(
        String registrationId,
        String nameId,
        List<String> sessionIndexes) implements Serializable {

    /** HTTP session attribute under which the login handler stashes this record. */
    public static final String SESSION_ATTRIBUTE = "io.kelta.auth.SAML_LOGOUT_INFO";

    public SamlLogoutInfo {
        // A plain ArrayList (not List.of/copyOf) keeps this JDK-serializable with
        // the standard collection metadata GraalVM already bundles — the record is
        // stored in the Redis-backed HTTP session (see AuthRuntimeHints).
        sessionIndexes = new ArrayList<>(sessionIndexes == null ? List.of() : sessionIndexes);
    }
}
