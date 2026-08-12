package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.module.ModuleSigningKey;
import io.kelta.runtime.module.ModuleSigningKeyStore;
import io.kelta.worker.module.ModuleSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Management of a tenant's module JAR signing keys — the trust anchors a module JAR signature is
 * checked against before the JAR is allowed to run arbitrary Java in the worker JVM.
 *
 * <p>Several keys may be active at once, and a signature is accepted if it matches any of them.
 * The rotation that buys is:
 * <ol>
 *   <li>{@code POST /api/modules/signing-keys} — add the new key; both are now trusted</li>
 *   <li>re-sign and re-install modules, checking {@code dependentModules} as it drains</li>
 *   <li>{@code POST /api/modules/signing-keys/{id}/retire} — once nothing depends on the old key</li>
 * </ol>
 *
 * <p>Only public keys pass through here, so responses may safely include the PEM. Adding a key is
 * nonetheless a grant of code-execution authority over the tenant, which is why writes on this
 * path require {@code MANAGE_CREDENTIALS} at the gateway rather than the blanket
 * {@code API_ACCESS} the rest of {@code /api/modules/**} carries.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/modules/signing-keys")
@ConditionalOnBean(ModuleSigningKeyStore.class)
public class ModuleSigningKeyController {

    private static final Logger log = LoggerFactory.getLogger(ModuleSigningKeyController.class);

    private static final int MAX_LABEL_LENGTH = 100;

    private final ModuleSigningKeyStore keyStore;
    private final ModuleSignatureVerifier signatureVerifier;

    public ModuleSigningKeyController(ModuleSigningKeyStore keyStore,
                                       ModuleSignatureVerifier signatureVerifier) {
        this.keyStore = keyStore;
        this.signatureVerifier = signatureVerifier;
    }

    /**
     * Lists the tenant's signing keys, retired ones included.
     *
     * <p>Each entry carries {@code dependentModules}: how many installed modules were signed by
     * that key. Retiring a key with a non-zero count makes those modules fail verification on
     * their next load and fall back to inert stub handlers, while {@code /api/modules} continues
     * to report them {@code ACTIVE} — so this count is the pre-flight check for a rotation, not a
     * statistic.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listKeys(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> records = keyStore.findByTenant(tenantId).stream()
                .map(key -> keyToMap(key, keyStore.countModulesSignedBy(tenantId, key.fingerprint())))
                .toList();

        Map<String, Object> response =
                new LinkedHashMap<>(JsonApiResponseBuilder.collection("module-signing-keys", records));
        // Surfaced because it changes what these keys mean: while a platform-wide key is
        // configured it is trusted for every tenant, so this tenant's key set is not the whole
        // trust boundary. Its fingerprint is included so a module recorded against it is
        // identifiable rather than looking like an unknown key.
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("signingRequired", signatureVerifier.isRequired());
        String platformFingerprint = signatureVerifier.platformKeyFingerprint();
        meta.put("platformKeyConfigured", platformFingerprint != null);
        if (platformFingerprint != null) {
            meta.put("platformKeyFingerprint", platformFingerprint);
        }
        response.put("meta", meta);
        return ResponseEntity.ok(response);
    }

    /**
     * Adds a trust anchor for this tenant.
     *
     * @param body {@code label}, {@code publicKeyPem}, and optional {@code algorithm}
     *             (default {@code Ed25519})
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addKey(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestBody Map<String, Object> body) {

        String label = string(body.get("label"));
        String publicKeyPem = string(body.get("publicKeyPem"));
        String algorithm = string(body.get("algorithm"));
        if (algorithm == null || algorithm.isBlank()) {
            algorithm = "Ed25519";
        }

        if (label == null || label.isBlank()) {
            return badRequest("label is required");
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            return badRequest("label must be at most " + MAX_LABEL_LENGTH + " characters");
        }
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            return badRequest("publicKeyPem is required");
        }

        // Parse and fingerprint before persisting: a key that cannot be parsed would sit in the
        // trust set verifying nothing, and be skipped with a warning on every single install.
        String fingerprint;
        try {
            fingerprint = ModuleSignatureVerifier.fingerprint(publicKeyPem, algorithm);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }

        ModuleSigningKey key = new ModuleSigningKey(
                UUID.randomUUID().toString(), tenantId, label.trim(), algorithm.trim(),
                publicKeyPem.trim(), fingerprint, true, null, null,
                userId != null ? userId : "system");

        try {
            keyStore.create(key);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409).body(JsonApiResponseBuilder.error("409", "Conflict",
                    "This tenant already has a signing key with that label or public key"));
        }

        log.info("Added module signing key '{}' ({}, fingerprint {}) for tenant {}",
                key.label(), key.algorithm(), key.shortFingerprint(), tenantId);
        return ResponseEntity.ok(JsonApiResponseBuilder.single(
                "module-signing-keys", key.id(), keyToMap(key, 0)));
    }

    /**
     * Retires a key: signatures from it stop being accepted, the row stays.
     *
     * <p>Any module still recorded against it will fail verification on its next load and fall
     * back to stub handlers. The response reports that count so the consequence is visible at the
     * moment of the change rather than at the next pod restart.
     */
    @PostMapping("/{id}/retire")
    public ResponseEntity<Map<String, Object>> retireKey(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @PathVariable String id) {
        return setActive(tenantId, userId, id, false);
    }

    /**
     * Re-activates a retired key — the rollback for a rotation retired too early.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, Object>> activateKey(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @PathVariable String id) {
        return setActive(tenantId, userId, id, true);
    }

    /**
     * Permanently deletes a retired key.
     *
     * <p>Refused while the key is active. With signing required, a tenant's last key going away is
     * the difference between installs being blocked and installs being unverified, and that should
     * not be reachable in one call — retire first, which is reversible and leaves the fingerprint
     * on affected modules resolvable.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteKey(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String id) {
        ModuleSigningKey key = keyStore.findById(tenantId, id).orElse(null);
        if (key == null) {
            return notFound();
        }
        if (key.active()) {
            return ResponseEntity.status(409).body(JsonApiResponseBuilder.error("409", "Conflict",
                    "Retire the key before deleting it"));
        }
        int dependents = keyStore.countModulesSignedBy(tenantId, key.fingerprint());
        if (dependents > 0) {
            return ResponseEntity.status(409).body(JsonApiResponseBuilder.error("409", "Conflict",
                    dependents + " installed module(s) are recorded against this key — deleting it "
                    + "would leave them unexplainable. Re-sign and re-install them first"));
        }
        keyStore.delete(tenantId, id);
        log.info("Deleted retired module signing key '{}' for tenant {}", key.label(), tenantId);
        return ResponseEntity.ok(JsonApiResponseBuilder.single(
                "module-signing-keys", id, keyToMap(key, 0)));
    }

    private ResponseEntity<Map<String, Object>> setActive(
            String tenantId, String userId, String id, boolean active) {
        ModuleSigningKey existing = keyStore.findById(tenantId, id).orElse(null);
        if (existing == null) {
            return notFound();
        }
        keyStore.setActive(tenantId, id, active, userId != null ? userId : "system");

        int dependents = keyStore.countModulesSignedBy(tenantId, existing.fingerprint());
        if (!active && dependents > 0) {
            log.warn("Retired module signing key '{}' for tenant {} while {} installed module(s) "
                    + "still depend on it — they will load as stub handlers until re-signed",
                    existing.label(), tenantId, dependents);
        } else {
            log.info("Set module signing key '{}' active={} for tenant {}",
                    existing.label(), active, tenantId);
        }

        ModuleSigningKey updated = keyStore.findById(tenantId, id).orElse(existing);
        Map<String, Object> attributes = keyToMap(updated, dependents);
        if (!active && dependents > 0) {
            attributes.put("warning", dependents + " installed module(s) were signed by this key "
                    + "and will fall back to stub handlers on their next load until re-signed "
                    + "with an active key");
        }
        return ResponseEntity.ok(JsonApiResponseBuilder.single(
                "module-signing-keys", id, attributes));
    }

    private Map<String, Object> keyToMap(ModuleSigningKey key, int dependentModules) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", key.label());
        map.put("algorithm", key.algorithm());
        map.put("fingerprint", key.fingerprint());
        map.put("publicKeyPem", key.publicKeyPem());
        map.put("active", key.active());
        map.put("dependentModules", dependentModules);
        map.put("retiredAt", key.retiredAt());
        map.put("createdAt", key.createdAt());
        map.put("createdBy", key.createdBy());
        return map;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String detail) {
        return ResponseEntity.badRequest().body(
                JsonApiResponseBuilder.error("400", "Validation Error", detail));
    }

    private static ResponseEntity<Map<String, Object>> notFound() {
        return ResponseEntity.status(404).body(
                JsonApiResponseBuilder.error("404", "Not Found", "Signing key not found"));
    }
}
