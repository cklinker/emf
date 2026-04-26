package io.kelta.worker.service.api;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.module.integration.api.ApiOperation;
import io.kelta.runtime.module.integration.api.ApiSpec;
import io.kelta.runtime.module.integration.spi.ApiSpecStore;
import io.kelta.worker.repository.ApiOperationRepository;
import io.kelta.worker.repository.ApiSpecRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Worker-side implementation of {@link ApiSpecStore}. The
 * {@code IntegrationModule} (PR 4 onwards) consumes this through the
 * {@code ModuleContext} extension API so the {@code CALL_API} handler can
 * resolve {@code (specId, operationId)} into the bits it needs to execute a
 * request.
 */
@Service
public class JdbcApiSpecStore implements ApiSpecStore {

    private final ApiSpecRepository specRepository;
    private final ApiOperationRepository operationRepository;

    public JdbcApiSpecStore(ApiSpecRepository specRepository,
                             ApiOperationRepository operationRepository) {
        this.specRepository = specRepository;
        this.operationRepository = operationRepository;
    }

    @Override
    public Optional<ApiSpec> findSpec(String tenantId, String specId) {
        return TenantContext.callWithTenant(tenantId,
            () -> specRepository.findById(specId, tenantId));
    }

    @Override
    public Optional<ApiOperation> findOperation(String tenantId, String specId,
                                                 String syntheticOpId) {
        return TenantContext.callWithTenant(tenantId,
            () -> operationRepository.findOperation(tenantId, specId, syntheticOpId));
    }
}
