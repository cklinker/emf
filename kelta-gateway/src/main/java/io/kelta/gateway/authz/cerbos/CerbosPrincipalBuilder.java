package io.kelta.gateway.authz.cerbos;

import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import io.kelta.gateway.auth.GatewayPrincipal;

import java.util.List;
import java.util.stream.Collectors;

public class CerbosPrincipalBuilder {

    private CerbosPrincipalBuilder() {}

    public static Principal build(GatewayPrincipal principal) {
        Principal builder = Principal.newInstance(principal.getUsername(), "user")
                .withAttribute("profileId", stringAttr(principal.getProfileId()))
                .withAttribute("tenantId", stringAttr(principal.getTenantId()))
                .withAttribute("profileName", stringAttr(principal.getProfileName()));

        List<String> groups = principal.getGroups();
        if (groups != null && !groups.isEmpty()) {
            builder = builder.withAttribute("groups",
                    AttributeValue.listValue(
                            groups.stream()
                                    .map(AttributeValue::stringValue)
                                    .collect(Collectors.toList())));
        }

        return builder;
    }

    private static AttributeValue stringAttr(String value) {
        return AttributeValue.stringValue(value != null ? value : "");
    }
}
