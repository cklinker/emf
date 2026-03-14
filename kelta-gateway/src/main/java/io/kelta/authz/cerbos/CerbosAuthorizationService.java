package io.kelta.gateway.authz.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import io.kelta.gateway.auth.GatewayPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;

/**
 * Wraps Cerbos SDK calls in reactive Monos with timeouts and fail-open behaviour.
 *
 * <p>gRPC connections to Cerbos can become stale when the Cerbos pod restarts,
 * causing blocking calls to hang for 15+ seconds (the default gRPC deadline).
 * To prevent this from cascading into user-visible latency, every call is
 * wrapped with a {@link Mono#timeout(Duration)} and, on timeout or error,
 * defaults to <em>allow</em> (fail-open).  The gateway already validates JWTs
 * and resolves tenant/profile identity, so fail-open here is an acceptable
 * trade-off that keeps the UI responsive while Cerbos recovers.
 */
@Service
public class CerbosAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CerbosAuthorizationService.class);

    /** Maximum time to wait for a single Cerbos gRPC call before failing open. */
    private static final Duration CERBOS_TIMEOUT = Duration.ofSeconds(3);

    private final CerbosBlockingClient cerbosClient;

    public CerbosAuthorizationService(CerbosBlockingClient cerbosClient) {
        this.cerbosClient = cerbosClient;
    }

    public Mono<Boolean> checkSystemPermission(GatewayPrincipal principal, String permissionName) {
        return Mono.fromCallable(() -> {
            Principal cerbosPrincipal = CerbosPrincipalBuilder.build(principal);
            Resource resource = Resource.newInstance("system_feature", permissionName)
                    .withAttribute("featureName", AttributeValue.stringValue(permissionName))
                    .withScope(principal.getTenantId());

            CheckResult result = cerbosClient.check(cerbosPrincipal, resource, permissionName);
            boolean allowed = result.isAllowed(permissionName);
            log.debug("Cerbos system check: user={} permission={} allowed={}",
                    principal.getUsername(), permissionName, allowed);
            return allowed;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(CERBOS_TIMEOUT)
        .onErrorResume(e -> {
            log.warn("Cerbos system check failed (fail-open): user={} permission={} error={}",
                    principal.getUsername(), permissionName, e.getMessage());
            return Mono.just(true);
        });
    }

    public Mono<Boolean> checkObjectPermission(GatewayPrincipal principal,
                                                String collectionId,
                                                String action) {
        return Mono.fromCallable(() -> {
            Principal cerbosPrincipal = CerbosPrincipalBuilder.build(principal);
            Resource resource = Resource.newInstance("collection", collectionId)
                    .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                    .withScope(principal.getTenantId());

            CheckResult result = cerbosClient.check(cerbosPrincipal, resource, action);
            boolean allowed = result.isAllowed(action);
            log.debug("Cerbos object check: user={} collection={} action={} allowed={}",
                    principal.getUsername(), collectionId, action, allowed);
            return allowed;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(CERBOS_TIMEOUT)
        .onErrorResume(e -> {
            log.warn("Cerbos object check failed (fail-open): user={} collection={} action={} error={}",
                    principal.getUsername(), collectionId, action, e.getMessage());
            return Mono.just(true);
        });
    }
}
