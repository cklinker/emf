package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.BootstrapRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a published custom page into a versioned {@link PageRenderContract}. Looks up the
 * {@code ui-pages} record by {@code slug} that is {@code published} and {@code active} in the
 * current tenant (resolved by tenant context + RLS), and returns its component tree. Unpublished,
 * inactive, or unknown slugs resolve to empty (the controller maps that to 404), so draft pages are
 * never served to end users.
 *
 * <p>Optional per-page authorization (slice 1h): a page whose {@code config.access.requiredPermission}
 * is set is gated on that system permission, checked via the same {@code profile_system_permission}
 * lookup the {@code /api/admin/**} controllers use ({@link BootstrapRepository#findProfileSystemPermissions})
 * — NOT a Cerbos PDP call. A denial resolves to {@link Optional#empty()} so the controller returns
 * <b>404, not 403</b>, matching the unknown-slug privacy posture (a restricted page's existence is not
 * leaked). A page with no {@code access} key behaves exactly as before (no permission lookup at all).
 */
@Service
public class PageRenderService {

    private static final Logger log = LoggerFactory.getLogger(PageRenderService.class);

    /** Render-contract version — bumped when the component-tree node schema changes incompatibly. */
    static final String CONTRACT_VERSION = "2.0";
    private static final String UI_PAGES = "ui-pages";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final ObjectMapper objectMapper;
    private final BootstrapRepository bootstrapRepository;

    public PageRenderService(QueryEngine queryEngine, CollectionRegistry collectionRegistry,
                             ObjectMapper objectMapper, BootstrapRepository bootstrapRepository) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.objectMapper = objectMapper;
        this.bootstrapRepository = bootstrapRepository;
    }

    public Optional<PageRenderContract> render(String slug, String profileId) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        CollectionDefinition definition = collectionRegistry.get(UI_PAGES);
        if (definition == null) {
            return Optional.empty();
        }

        List<FilterCondition> filters = List.of(
                FilterCondition.eq("slug", slug),
                FilterCondition.eq("published", true),
                FilterCondition.eq("active", true));
        QueryRequest request = new QueryRequest(new Pagination(1, 1), List.of(), List.of(), filters);
        QueryResult result = queryEngine.executeQuery(definition, request);
        if (result.data().isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> page = result.data().getFirst();
        Map<String, Object> config = parseConfig(page.get("config"));   // never null

        // Optional per-page authorization gate (1h). Only restricted pages incur the lookup; a denial
        // resolves to empty → 404 (deliberately not 403, so page existence is not leaked).
        String requiredPermission = extractRequiredPermission(config);
        if (requiredPermission != null && !hasSystemPermission(profileId, requiredPermission)) {
            return Optional.empty();
        }

        return Optional.of(new PageRenderContract(
                CONTRACT_VERSION,
                asString(page.get("slug")),
                asString(page.get("title")),
                asString(page.get("path")),
                extractObjectList(config.get("variables")),
                extractObjectList(config.get("dataSources")),
                config));                                                // tree = whole config, verbatim
    }

    /** {@code config.access.requiredPermission}, or null when the page declares no restriction. */
    private static String extractRequiredPermission(Map<String, Object> config) {
        if (config.get("access") instanceof Map<?, ?> access
                && access.get("requiredPermission") instanceof String perm && !perm.isBlank()) {
            return perm;
        }
        return null;
    }

    /**
     * True iff {@code profileId} is granted {@code permission} in {@code profile_system_permission}.
     * Same lookup the admin controllers use ({@code TenantOtlpTargetController.requireSetupPermission})
     * — a DB grant read, not a Cerbos PDP call. A missing profile id denies (→ 404).
     */
    private boolean hasSystemPermission(String profileId, String permission) {
        if (profileId == null || profileId.isBlank()) {
            return false;
        }
        return bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> permission.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Parse the page's {@code config} JSON (delivered as a Map or a serialized String) once. The
     * whole map is the render contract's {@code tree} — there is no {@code config.tree} wrapper; the
     * component tree lives at {@code config.components}, so the FE reads {@code tree.components}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(Object config) {
        if (config instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (config instanceof String str && !str.isBlank()) {
            try {
                return objectMapper.readValue(str, MAP_TYPE);
            } catch (RuntimeException e) {
                log.warn("Failed to parse ui-page config JSON: {}", e.getMessage());
            }
        }
        return Map.of();
    }

    /**
     * Page-level {@code variables}/{@code dataSources} from {@code config} — opaque pass-through JSON,
     * default empty. The server never inspects their internals or resolves bindings.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractObjectList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return List.of();
    }
}
