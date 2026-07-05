package io.kelta.worker.interceptor;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.FieldMaskingService;
import io.kelta.worker.service.RecordMaskingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blocks the classic dynamic-masking side channel: a user who sees a field
 * masked could otherwise binary-search its plaintext through filter probes
 * ({@code filter[ssn][like]=1*}) or leak ordering through {@code sort=ssn} —
 * the response values are masked, but which records match is not.
 *
 * <p>Any list/query request whose filter or sort references a field the
 * requester must see masked is rejected with one uniform 403 body. Uniform on
 * purpose: distinguishable "empty result vs. error" responses would themselves
 * be an oracle. Collections without masking config short-circuit before any
 * authorization work, and the masked-set lookup rides the same cached batched
 * Cerbos check the read advice uses.
 *
 * <p>Skips the same surfaces as {@link CerbosFieldSecurityAdvice}: non-API
 * paths, {@code /api/admin/**}, {@code /api/me/**}, metadata endpoints, and
 * identity-less (internal) calls.
 */
@Component
public class MaskedFieldPredicateInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MaskedFieldPredicateInterceptor.class);

    /** Matches the field segment of filter params: {@code filter[field]} / {@code filter[field][op]}. */
    private static final Pattern FILTER_PARAM = Pattern.compile("^filter\\[([^\\]\\[]+)\\]");

    /**
     * One byte-identical body for every rejection — never varies by field,
     * operator, or match state, so the error itself carries no signal.
     */
    static final String ERROR_BODY = """
            {"errors":[{"status":"403","code":"MASKED_FIELD_PREDICATE",\
            "title":"Forbidden","detail":\
            "Request references a field you do not have permission to filter or sort by."}]}""";

    private final CollectionRegistry collectionRegistry;
    private final RecordMaskingService recordMaskingService;
    private final CerbosPermissionResolver permissionResolver;
    private final boolean permissionsEnabled;

    public MaskedFieldPredicateInterceptor(
            CollectionRegistry collectionRegistry,
            RecordMaskingService recordMaskingService,
            CerbosPermissionResolver permissionResolver,
            @Value("${kelta.gateway.security.permissions-enabled:true}") boolean permissionsEnabled) {
        this.collectionRegistry = collectionRegistry;
        this.recordMaskingService = recordMaskingService;
        this.permissionResolver = permissionResolver;
        this.permissionsEnabled = permissionsEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!permissionsEnabled || !"GET".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/admin/") || path.startsWith("/api/me/")
                || isMetadataPath(path)) {
            return true;
        }
        if (!permissionResolver.hasIdentity(request)) {
            return true; // internal/unauthenticated tier — same trust as the read advice
        }

        Set<String> referenced = referencedFields(request);
        if (referenced.isEmpty()) {
            return true;
        }

        CollectionDefinition definition = collectionRegistry.get(extractCollectionName(path));
        if (definition == null) {
            return true;
        }
        Map<String, FieldMaskingService.MaskingConfig> configs =
                recordMaskingService.maskableConfigs(definition);
        if (configs.isEmpty()) {
            return true;
        }

        Set<String> maskableReferenced = new HashSet<>(referenced);
        maskableReferenced.retainAll(configs.keySet());
        if (maskableReferenced.isEmpty()) {
            return true;
        }

        Set<String> masked = recordMaskingService.maskedFieldsFor(
                permissionResolver.getEmail(request),
                permissionResolver.getProfileId(request),
                permissionResolver.getTenantId(request),
                definition.name(),
                configs.keySet());
        maskableReferenced.retainAll(masked);
        if (maskableReferenced.isEmpty()) {
            return true;
        }

        log.debug("Blocked masked-field predicate on {} (collection={})", path, definition.name());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(ERROR_BODY.getBytes(StandardCharsets.UTF_8));
        return false;
    }

    /** Field names referenced by {@code filter[...]} params and the {@code sort} param. */
    private Set<String> referencedFields(HttpServletRequest request) {
        Set<String> fields = new LinkedHashSet<>();
        for (String param : request.getParameterMap().keySet()) {
            Matcher m = FILTER_PARAM.matcher(param);
            if (m.find()) {
                fields.add(m.group(1));
            }
        }
        String[] sortValues = request.getParameterValues("sort");
        if (sortValues != null) {
            for (String sortValue : sortValues) {
                for (String part : sortValue.split(",")) {
                    String field = part.trim();
                    if (field.startsWith("-")) {
                        field = field.substring(1);
                    }
                    if (!field.isEmpty()) {
                        fields.add(field);
                    }
                }
            }
        }
        return fields;
    }

    /**
     * The collection whose fields a list request filters/sorts: the last path
     * segment for both {@code /api/{collection}} and the child of
     * {@code /api/{parent}/{parentId}/{child}}. Other shapes carry no
     * filter/sort semantics and fall through harmlessly (no masking config →
     * pass).
     */
    private String extractCollectionName(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 5) {
            return parts[4];
        }
        return parts.length >= 3 ? parts[2] : "";
    }

    private boolean isMetadataPath(String path) {
        return ApiPaths.isMetadataPath(path);
    }
}
