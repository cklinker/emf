package io.kelta.worker.service.api;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.module.integration.api.ApiSpec;
import io.kelta.runtime.module.integration.api.OpenApiParseException;
import io.kelta.runtime.module.integration.api.OpenApiSpecParser;
import io.kelta.runtime.module.integration.api.ParsedSpec;
import io.kelta.worker.repository.ApiOperationRepository;
import io.kelta.worker.repository.ApiOperationRepository.OperationSyncResult;
import io.kelta.worker.repository.ApiSpecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the import lifecycle for an OpenAPI spec:
 * <ol>
 *   <li>Fetch the document (URL) or accept inline JSON / YAML</li>
 *   <li>Parse + validate via {@link OpenApiSpecParser}</li>
 *   <li>Insert (first-time) or update (re-import) the {@code api_spec} row,
 *       bumping {@code revision} and the spec hash</li>
 *   <li>Sync the operation index — adds new, updates changed, deprecates
 *       removed</li>
 * </ol>
 *
 * Storage is wrapped in {@code TenantContext.callWithTenant(...)} so the V127
 * RLS policies engage on every read and write.
 */
@Service
public class ApiSpecService {

    private static final Logger log = LoggerFactory.getLogger(ApiSpecService.class);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);

    private final OpenApiSpecParser parser;
    private final ApiSpecRepository specRepository;
    private final ApiOperationRepository operationRepository;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    public ApiSpecService(OpenApiSpecParser parser,
                          ApiSpecRepository specRepository,
                          ApiOperationRepository operationRepository,
                          ObjectMapper objectMapper) {
        this.parser = parser;
        this.specRepository = specRepository;
        this.operationRepository = operationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Imports a new spec or re-imports an existing one (matched by name within
     * the tenant). Returns the resulting record.
     */
    public ImportResult importSpec(ImportRequest req, String tenantId, String userId) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String raw = resolveRaw(req);
        String format = detectFormat(raw, req.rawFormat());

        ParsedSpec parsed;
        try {
            parsed = parser.parse(raw);
        } catch (OpenApiParseException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenApiParseException("Failed to parse spec: " + e.getMessage(), e);
        }

        String hash = parser.hash(raw);

        return TenantContext.callWithTenant(tenantId, () -> {
            Optional<ApiSpec> existing = specRepository.findByName(req.name(), tenantId);
            ApiSpec saved;
            OperationSyncResult diff;
            if (existing.isPresent()) {
                ApiSpec prior = existing.get();
                if (prior.specHash().equals(hash)) {
                    log.debug("Spec '{}' unchanged (hash match); skipping", req.name());
                    diff = new OperationSyncResult(0, 0, 0);
                    saved = prior;
                } else {
                    ApiSpec next = new ApiSpec(
                        prior.id(), tenantId, prior.name(),
                        req.description() != null ? req.description() : prior.description(),
                        parsed.specVersion(), parsed.apiTitle(), parsed.apiVersion(),
                        parsed.baseUrl(), parsed.servers(), parsed.securitySchemes(),
                        req.sourceType(), req.sourceUrl(),
                        raw, format, parsed.parsedSpec(),
                        hash, prior.revision() + 1, true, Instant.now());
                    specRepository.updateForReimport(next, userId);
                    diff = operationRepository.syncOperations(tenantId, prior.id(),
                        parsed.operations());
                    saved = next;
                    log.info("Re-imported spec '{}' (rev {}): +{} ~{} -{}",
                        prior.name(), next.revision(), diff.added(), diff.changed(),
                        diff.removed());
                }
            } else {
                ApiSpec next = new ApiSpec(
                    UUID.randomUUID().toString(), tenantId, req.name(), req.description(),
                    parsed.specVersion(), parsed.apiTitle(), parsed.apiVersion(),
                    parsed.baseUrl(), parsed.servers(), parsed.securitySchemes(),
                    req.sourceType(), req.sourceUrl(),
                    raw, format, parsed.parsedSpec(),
                    hash, 1, true, Instant.now());
                specRepository.insert(next, userId);
                diff = operationRepository.syncOperations(tenantId, next.id(),
                    parsed.operations());
                saved = next;
                log.info("Imported new spec '{}' with {} operations",
                    next.name(), parsed.operations().size());
            }
            return new ImportResult(saved, diff);
        });
    }

    public List<ApiSpec> listActive(String tenantId) {
        return TenantContext.callWithTenant(tenantId, () -> specRepository.listActive(tenantId));
    }

    public Optional<ApiSpec> findById(String id, String tenantId) {
        return TenantContext.callWithTenant(tenantId, () -> specRepository.findById(id, tenantId));
    }

    public void softDelete(String id, String tenantId, String userId) {
        TenantContext.runWithTenant(tenantId, () -> specRepository.softDelete(id, tenantId, userId));
    }

    // -----------------------------------------------------------------------

    private String resolveRaw(ImportRequest req) {
        if ("URL".equalsIgnoreCase(req.sourceType())) {
            if (req.sourceUrl() == null || req.sourceUrl().isBlank()) {
                throw new IllegalArgumentException(
                    "sourceUrl is required when sourceType=URL");
            }
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(FETCH_TIMEOUT).build();
                HttpResponse<String> res = client.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(req.sourceUrl()))
                        .timeout(FETCH_TIMEOUT)
                        .header("Accept", "application/json, application/yaml, text/yaml")
                        .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 != 2) {
                    throw new OpenApiParseException(
                        "Failed to fetch spec: HTTP " + res.statusCode());
                }
                return res.body();
            } catch (OpenApiParseException e) {
                throw e;
            } catch (Exception e) {
                throw new OpenApiParseException(
                    "Failed to fetch spec from " + req.sourceUrl() + ": " + e.getMessage(), e);
            }
        }
        if (req.raw() == null || req.raw().isBlank()) {
            throw new IllegalArgumentException(
                "raw spec body is required when sourceType is INLINE_JSON or INLINE_YAML");
        }
        return req.raw();
    }

    private String detectFormat(String raw, String hint) {
        if (hint != null && !hint.isBlank()) return hint.toLowerCase();
        String trimmed = raw.stripLeading();
        if (trimmed.startsWith("{")) return "json";
        return "yaml";
    }

    // -----------------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------------

    /**
     * Body shape expected by the import / re-import endpoints. Exactly one of
     * {@code raw} or {@code sourceUrl} should be supplied (matching
     * {@code sourceType}).
     */
    public record ImportRequest(
        String name,
        String description,
        String sourceType,
        String sourceUrl,
        String raw,
        String rawFormat
    ) {
    }

    public record ImportResult(ApiSpec spec, OperationSyncResult diff) {
    }
}
