package io.kelta.worker.listener;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner guard for spotopened's {@code facility-photos} tenant collection.
 * Same shape as {@link FieldReportGuardHook} -- see that class's javadoc for
 * the full reasoning (createdBy over a hand-declared owner field, QueryEngine
 * for beforeDelete, fail-closed on an unresolvable identity, internal tier
 * admitted).
 *
 * <p>The one thing worth calling out here specifically: this collection is
 * also granted {@code create} (and {@code read}) to spotopened's Guest
 * profile (emf#1368), so a create can legitimately arrive with
 * {@code createdBy = "guest"} -- the literal, shared string every anonymous
 * caller stamps (see {@code DynamicCollectionRouter.resolveUserId}). Without
 * a special case that value would fail the same "unresolvable identity"
 * check a genuinely broken caller hits, rejecting every Guest create
 * outright -- see {@link #GUEST_IDENTITY}. It is Cerbos, not this hook, that
 * keeps a guest from ever reaching {@link #beforeUpdate}/{@link #beforeDelete}
 * at all: Guest has no edit/delete grant on this collection, so those
 * requests never get past {@code RouteAuthorizationFilter}.
 */
public class PhotoGuardHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(PhotoGuardHook.class);

    static final String COLLECTION = "facility-photos";
    private static final String OWNER_FIELD = "createdBy";
    private static final String USER_ID_HEADER = "X-User-Id";

    /** Mirrors {@code JwtAuthenticationFilter.GUEST_USERNAME} (kelta-gateway) -- the literal,
     *  shared, deliberately-unresolvable caller identity every anonymous request stamps. Without
     *  this special case, the fail-closed "unresolvable identity" check below would reject every
     *  Guest create outright, even though createdBy is correctly stamped "guest" too and Cerbos
     *  has already granted Guest create on this collection. Guest never reaches beforeUpdate/
     *  beforeDelete in practice -- Cerbos has no edit/delete grant for it -- so this only ever
     *  changes beforeCreate's outcome. */
    private static final String GUEST_IDENTITY = "guest";

    private final UserIdResolver userIdResolver;
    private final CollectionRegistry collectionRegistry;
    private final QueryEngine queryEngine;

    public PhotoGuardHook(UserIdResolver userIdResolver, CollectionRegistry collectionRegistry,
                           QueryEngine queryEngine) {
        this.userIdResolver = userIdResolver;
        this.collectionRegistry = collectionRegistry;
        this.queryEngine = queryEngine;
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        String caller = callerUuid(tenantId);
        if (caller == null) {
            return BeforeSaveResult.ok(); // internal tier (no HTTP identity)
        }
        if (CALLER_REJECTED.equals(caller)) {
            return reject("create", "unresolvable identity");
        }
        Object owner = record.get(OWNER_FIELD);
        if (!caller.equals(owner)) {
            return reject("create", "owner " + owner + " != caller");
        }
        return BeforeSaveResult.ok();
    }

    @Override
    public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                          Map<String, Object> previous, String tenantId) {
        String caller = callerUuid(tenantId);
        if (caller == null) {
            return BeforeSaveResult.ok();
        }
        if (CALLER_REJECTED.equals(caller)) {
            return reject("update", "unresolvable identity");
        }
        Object currentOwner = previous != null ? previous.get(OWNER_FIELD) : null;
        if (currentOwner != null && !caller.equals(currentOwner)) {
            return reject("update", "row belongs to " + currentOwner);
        }
        Object newOwner = record.get(OWNER_FIELD);
        if (newOwner != null && !caller.equals(newOwner)) {
            return reject("update", "cannot re-own to " + newOwner);
        }
        return BeforeSaveResult.ok();
    }

    @Override
    public BeforeSaveResult beforeDelete(String id, String tenantId) {
        String caller = callerUuid(tenantId);
        if (caller == null) {
            return BeforeSaveResult.ok();
        }
        if (CALLER_REJECTED.equals(caller)) {
            return reject("delete", "unresolvable identity");
        }
        CollectionDefinition definition = collectionRegistry.get(COLLECTION);
        if (definition == null) {
            log.warn("facility-photos collection not found in registry -- allowing delete of {}", id);
            return BeforeSaveResult.ok();
        }
        Optional<Map<String, Object>> row = queryEngine.getById(definition, id);
        Object owner = row.map(r -> r.get(OWNER_FIELD)).orElse(null);
        if (owner != null && !caller.equals(owner)) {
            return reject("delete", "row belongs to " + owner);
        }
        return BeforeSaveResult.ok();
    }

    private static final String CALLER_REJECTED = " rejected";

    private String callerUuid(String tenantId) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String identifier = request.getHeader(USER_ID_HEADER);
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        if (GUEST_IDENTITY.equals(identifier)) {
            return GUEST_IDENTITY;
        }
        String resolved = userIdResolver.resolve(identifier, tenantId);
        try {
            UUID.fromString(resolved);
            return resolved;
        } catch (IllegalArgumentException e) {
            return CALLER_REJECTED;
        }
    }

    private BeforeSaveResult reject(String action, String reason) {
        log.warn("Blocked {} on {}: {}", action, COLLECTION, reason);
        return BeforeSaveResult.error(OWNER_FIELD, "Photos can only be modified by their uploader");
    }
}
