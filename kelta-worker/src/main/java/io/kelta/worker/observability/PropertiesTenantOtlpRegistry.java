package io.kelta.worker.observability;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@link TenantOtlpRegistry} backed by {@link TenantOtlpProperties} (operator config). */
@Component
public class PropertiesTenantOtlpRegistry implements TenantOtlpRegistry {

    private final TenantOtlpProperties properties;

    public PropertiesTenantOtlpRegistry(TenantOtlpProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<OtlpTarget> targetFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        TenantOtlpProperties.Target target = properties.getTargets().get(tenantId);
        if (target == null || !target.isEnabled() || target.getEndpoint() == null || target.getEndpoint().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new OtlpTarget(target.getEndpoint(), target.getHeaders()));
    }
}
