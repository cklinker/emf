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

import java.util.Map;

@Service
public class CerbosAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CerbosAuthorizationService.class);

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
        }).subscribeOn(Schedulers.boundedElastic());
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
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
