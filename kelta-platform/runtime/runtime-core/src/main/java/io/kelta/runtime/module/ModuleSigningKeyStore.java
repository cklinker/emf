package io.kelta.runtime.module;

import java.util.List;
import java.util.Optional;

/**
 * Storage for per-tenant module JAR signing keys.
 * Implemented by {@code JdbcModuleSigningKeyStore} in the worker service.
 *
 * @since 1.0.0
 */
public interface ModuleSigningKeyStore {

    /**
     * The keys whose signatures are currently accepted for a tenant.
     *
     * <p>Read on every JAR install and on every module load, so implementations should keep
     * this cheap. Returning several keys is normal — that is how rotation works.
     *
     * @param tenantId the tenant ID
     * @return active keys, newest first; empty when the tenant has designated none
     */
    List<ModuleSigningKey> findActiveByTenant(String tenantId);

    /**
     * Every key for a tenant, retired ones included, for the management API.
     *
     * @param tenantId the tenant ID
     * @return all keys, active first then newest first
     */
    List<ModuleSigningKey> findByTenant(String tenantId);

    /**
     * Loads one key scoped to its tenant.
     *
     * <p>The tenant is a parameter rather than being inferred from the id so a caller cannot
     * address another tenant's key by guessing a UUID.
     *
     * @param tenantId the tenant ID
     * @param id       the key primary key
     * @return the key, or empty when it does not exist for this tenant
     */
    Optional<ModuleSigningKey> findById(String tenantId, String id);

    /**
     * Adds a key. The caller is expected to have parsed and fingerprinted the PEM already.
     *
     * @param key the key to persist
     * @return the persisted key ID
     * @throws RuntimeException when the tenant already trusts this key, or already has a key
     *         with this label
     */
    String create(ModuleSigningKey key);

    /**
     * Activates or retires a key.
     *
     * <p>Retirement is a flag rather than a delete so the fingerprint recorded against an
     * installed module stays resolvable — which is the only way to explain why that module
     * started loading as a stub.
     *
     * @param tenantId  the tenant ID
     * @param id        the key primary key
     * @param active    whether signatures from this key should be accepted
     * @param updatedBy user making the change
     * @return whether a row was updated
     */
    boolean setActive(String tenantId, String id, boolean active, String updatedBy);

    /**
     * Permanently removes a key.
     *
     * <p>Callers should refuse this for an active key: with signing required, dropping every
     * key is the difference between "installs are blocked" and "installs are unverified", and
     * that decision should not be one API call away.
     *
     * @param tenantId the tenant ID
     * @param id       the key primary key
     * @return whether a row was deleted
     */
    boolean delete(String tenantId, String id);

    /**
     * How many installed modules were signed by this key.
     *
     * <p>The number that has to be zero before a key is safe to retire: a module whose
     * signature no longer verifies falls back to inert stub handlers on the next load while
     * still reporting {@code ACTIVE}, so nothing else surfaces the breakage.
     *
     * @param tenantId    the tenant ID
     * @param fingerprint the key fingerprint
     * @return count of modules recorded against this key
     */
    int countModulesSignedBy(String tenantId, String fingerprint);
}
