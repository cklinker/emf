package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default {@link CustomDomainProvisioner} that just logs the events. A
 * cluster-aware impl (Fabric8 IngressRoute + Certificate management) is
 * planned as a follow-up and will register a bean that wins over this one
 * via {@link ConditionalOnMissingBean}.
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
