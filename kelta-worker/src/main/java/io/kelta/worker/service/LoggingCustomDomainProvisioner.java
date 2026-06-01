package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default {@link CustomDomainProvisioner} that just logs the events. Used when
 * no cluster-aware impl is registered (local dev, tests, environments where the
 * worker has no Kubernetes RBAC). When
 * {@code kelta.worker.k8s-provisioner.enabled=true} the
 * {@link K8sCustomDomainProvisioner} bean takes precedence and this one is
 * suppressed by {@link ConditionalOnMissingBean}.
 */
@Component
@ConditionalOnMissingBean(value = CustomDomainProvisioner.class, ignored = LoggingCustomDomainProvisioner.class)
public class LoggingCustomDomainProvisioner implements CustomDomainProvisioner {

    private static final Logger log = LoggerFactory.getLogger(LoggingCustomDomainProvisioner.class);

    @Override
    public void onVerified(String domainId, String domain, String tenantId) {
        log.info("[no-op provisioner] would create IngressRoute + Certificate for domain={} tenantId={} (id={})",
                domain, tenantId, domainId);
    }

    @Override
    public void onRemoved(String domainId, String domain, String tenantId) {
        log.info("[no-op provisioner] would delete IngressRoute + Certificate for domain={} tenantId={} (id={})",
                domain, tenantId, domainId);
    }
}
