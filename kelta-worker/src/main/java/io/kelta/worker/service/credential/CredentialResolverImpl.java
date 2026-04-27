package io.kelta.worker.service.credential;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kelta.crypto.EncryptionService;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.worker.repository.CredentialRepository;
import io.kelta.worker.service.SetupAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default {@link CredentialResolver}. Caches by
 * {@code tenantId + ":" + credentialId} with a short TTL. Cache invalidation
 * is driven by NATS events ({@code kelta.config.credential.changed.<id>}) via
 * {@link io.kelta.worker.listener.CredentialCacheInvalidationListener}.
 */
@Service
@ConditionalOnBean(EncryptionService.class)
public class CredentialResolverImpl implements CredentialResolver {

    private static final Logger log = LoggerFactory.getLogger(CredentialResolverImpl.class);
    private static final String AUDIT_ACTION = "CREDENTIAL_RESOLVE";
    private static final String AUDIT_SECTION = "credentials";

    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final SetupAuditService auditService;
    private final ObjectMapper objectMapper;

    private final Cache<String, ResolvedCredential> cache;
    /** Index of credentialId -> set of cache keys that reference it, for fast invalidation. */
    private final ConcurrentMap<String, ConcurrentMap<String, Boolean>> idIndex =
        new ConcurrentHashMap<>();

    public CredentialResolverImpl(CredentialRepository credentialRepository,
                                   EncryptionService encryptionService,
                                   SetupAuditService auditService,
                                   ObjectMapper objectMapper,
                                   @Value("${kelta.credentials.cache.ttl-seconds:300}") long ttlSeconds,
                                   @Value("${kelta.credentials.cache.max-size:1000}") long maxSize) {
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .maximumSize(maxSize)
            .build();
    }

    @Override
    public ResolvedCredential resolve(String tenantId, String reference, ResolutionContext ctx) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("credential reference required");
        }

        // RLS-scoped DB read. Run inside a tenant context so the policy filter applies.
        Map<String, Object> row = TenantContext.callWithTenant(tenantId, () ->
            credentialRepository.findById(reference, tenantId)
                .or(() -> credentialRepository.findByName(reference, tenantId))
                .orElse(null));
        if (row == null) {
            throw new CredentialNotFoundException(tenantId, reference);
        }

        String credentialId = (String) row.get("id");
        String name = (String) row.get("name");
        String type = (String) row.get("type");
        Boolean active = (Boolean) row.get("active");
        if (active != null && !active) {
            throw new CredentialDisabledException(credentialId, name);
        }

        String cacheKey = tenantId + ":" + credentialId;
        ResolvedCredential cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            writeAudit(tenantId, credentialId, name, ctx, true);
            return cached;
        }

        Map<String, Object> secrets = decryptSecrets(credentialId, (String) row.get("data_enc"));
        Map<String, Object> metadata = parseMetadata(row.get("metadata"));

        ResolvedCredential resolved = new ResolvedCredential(
            credentialId, name, type, secrets, metadata, Instant.now());

        cache.put(cacheKey, resolved);
        idIndex.computeIfAbsent(credentialId, k -> new ConcurrentHashMap<>())
            .put(cacheKey, Boolean.TRUE);

        writeAudit(tenantId, credentialId, name, ctx, false);
        return resolved;
    }

    @Override
    public void invalidate(String credentialId) {
        ConcurrentMap<String, Boolean> keys = idIndex.remove(credentialId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys.keySet()) {
            cache.invalidate(key);
        }
        log.debug("Invalidated {} cache entries for credential {}", keys.size(), credentialId);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
        idIndex.clear();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> decryptSecrets(String credentialId, String dataEnc) {
        if (dataEnc == null || dataEnc.isBlank()) {
            return Map.of();
        }
        try {
            String json = encryptionService.decrypt(dataEnc);
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            node.properties().forEach(entry ->
                result.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
            return result;
        } catch (Exception e) {
            throw new CredentialDecryptException(credentialId, e);
        }
    }

    private Map<String, Object> parseMetadata(Object metadata) {
        if (metadata == null) {
            return Map.of();
        }
        if (metadata instanceof Map<?, ?> map) {
            // Defensive copy with safe key/value types
            Map<String, Object> out = new HashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata.toString());
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            node.properties().forEach(entry ->
                result.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Records the resolution event without leaking decrypted material. */
    private void writeAudit(String tenantId, String credentialId, String name,
                             ResolutionContext ctx, boolean cacheHit) {
        // Audit body is a tiny JSON snippet so reviewers can correlate the
        // resolution with a flow run and a stated purpose. NEVER include
        // secret fields here.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cacheHit", cacheHit);
        if (ctx != null) {
            if (ctx.workflowRuleId() != null) body.put("workflowRuleId", ctx.workflowRuleId());
            if (ctx.executionLogId() != null) body.put("executionLogId", ctx.executionLogId());
            if (ctx.purpose() != null) body.put("purpose", ctx.purpose());
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            json = "{\"cacheHit\":" + cacheHit + "}";
        }
        auditService.log(
            tenantId,
            ctx == null ? null : ctx.userId(),
            AUDIT_ACTION,
            AUDIT_SECTION,
            "credential",
            credentialId,
            name,
            null,
            json);
    }

    /** Test-only hook for asserting the cache is populated. */
    Optional<ResolvedCredential> peek(String tenantId, String credentialId) {
        return Optional.ofNullable(cache.getIfPresent(tenantId + ":" + credentialId));
    }
}
