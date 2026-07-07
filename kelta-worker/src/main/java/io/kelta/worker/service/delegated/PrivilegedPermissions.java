package io.kelta.worker.service.delegated;

import java.util.Set;

/**
 * System permissions that must never be reachable through delegated administration.
 *
 * <p>A delegated-admin scope may not list a profile in {@code manageableProfileIds} that grants
 * any of these — otherwise a delegated admin could promote a user (or themselves, via a colluding
 * account) into an administrative role. Enforced twice: at scope save time by
 * {@code DelegatedAdminScopeValidationHook} and re-checked at request time by
 * {@link DelegatedAdminService} so a profile granted a privileged permission <em>after</em> being
 * scoped silently drops out of the scope (fail-closed).
 */
public final class PrivilegedPermissions {

    /**
     * The blocklist. Rationale per entry:
     * <ul>
     *   <li>{@code MANAGE_USERS} / {@code MANAGE_DELEGATED_ADMINS} — admin-of-admins.</li>
     *   <li>{@code MANAGE_TENANTS} — tenant lifecycle control.</li>
     *   <li>{@code MODIFY_ALL_DATA} / {@code VIEW_ALL_DATA} — blanket data access; also the
     *       object-permission override Cerbos honors on every collection route.</li>
     *   <li>{@code CUSTOMIZE_APPLICATION} / {@code MANAGE_WORKFLOWS} — authoring scripts/flows
     *       that later run without request identity (the identity-collection guard admits such
     *       writes), an indirect path to identity records.</li>
     *   <li>{@code MANAGE_DATA} — bulk jobs execute in a poller thread without request identity,
     *       same indirect path.</li>
     *   <li>{@code MANAGE_GROUPS} — group membership can widen a user's effective access.</li>
     *   <li>{@code MANAGE_SHARING} — tenant-wide sharing-rule control.</li>
     *   <li>{@code MANAGE_CONNECTED_APPS} / {@code MANAGE_CREDENTIALS} — machine credentials that
     *       outlive the user's own session.</li>
     * </ul>
     */
    public static final Set<String> SET = Set.of(
            "MANAGE_USERS",
            "MANAGE_DELEGATED_ADMINS",
            "MANAGE_TENANTS",
            "MODIFY_ALL_DATA",
            "VIEW_ALL_DATA",
            "CUSTOMIZE_APPLICATION",
            "MANAGE_WORKFLOWS",
            "MANAGE_DATA",
            "MANAGE_GROUPS",
            "MANAGE_SHARING",
            "MANAGE_CONNECTED_APPS",
            "MANAGE_CREDENTIALS");

    private PrivilegedPermissions() {
    }
}
