package com.emf.controlplane.service;

import com.emf.controlplane.dto.AddOidcProviderRequest;
import com.emf.controlplane.dto.UpdateOidcProviderRequest;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Service for managing OIDC provider configurations.
 * Handles CRUD operations with validation, event publishing, and JWKS cache management.
 * 
 * Requirements satisfied:
 * - 4.1: Return list of configured OIDC providers
 * - 4.2: Add OIDC provider with valid configuration and return created provider
 * - 4.4: Update OIDC provider and persist changes
 * - 4.5: Delete OIDC provider by marking as inactive
 */
@Service
public class OidcProviderService {

    private static final Logger log = LoggerFactory.getLogger(OidcProviderService.class);

    private final OidcProviderRepository providerRepository;
    private final ConfigEventPublisher eventPublisher;
    private final JwksCache jwksCache;
    private final ObjectMapper objectMapper;
    private final SecurityAuditService securityAuditService;

    public OidcProviderService(
            OidcProviderRepository providerRepository,
            @Nullable ConfigEventPublisher eventPublisher,
            @Nullable JwksCache jwksCache,
            @Nullable SecurityAuditService securityAuditService) {
        this.providerRepository = providerRepository;
        this.eventPublisher = eventPublisher;
        this.jwksCache = jwksCache;
        this.objectMapper = new ObjectMapper();
        this.securityAuditService = securityAuditService;
    }

    /**
     * Lists all active OIDC providers.
     * Returns providers ordered by name for consistent display.
     * 
     * @return List of active OIDC providers
     * 
     * Validates: Requirement 4.1
     */
    @Transactional(readOnly = true)
    public List<OidcProvider> listProviders() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing all active OIDC providers for tenant: {}", tenantId);
        if (tenantId != null) {
            return providerRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
        }
        return providerRepository.findByActiveTrueOrderByNameAsc();
    }

    /**
     * Adds a new OIDC provider with the given configuration.
     * Validates the issuer URL and JWKS URI format before persisting.
     * 
     * @param request The provider creation request
     * @return The created OIDC provider with generated ID
     * @throws DuplicateResourceException if a provider with the same name or issuer already exists
     * @throws ValidationException if the issuer URL or JWKS URI format is invalid
     * 
     * Validates: Requirement 4.2
     */
    @Transactional
    public OidcProvider addProvider(AddOidcProviderRequest request) {
        log.info("Adding OIDC provider with name: {}", request.getName());

        // Validate URL formats
        validateUrl(request.getIssuer(), "issuer");
        validateUrl(request.getJwksUri(), "jwksUri");

        // Validate claim paths
        validateClaimPath(request.getRolesClaim(), "rolesClaim");
        validateClaimPath(request.getEmailClaim(), "emailClaim");
        validateClaimPath(request.getUsernameClaim(), "usernameClaim");
        validateClaimPath(request.getNameClaim(), "nameClaim");
        
        // Validate roles mapping JSON
        validateRolesMapping(request.getRolesMapping());

        String tenantId = TenantContextHolder.getTenantId();

        // Check for duplicate name
        if (tenantId != null) {
            if (providerRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new DuplicateResourceException("OidcProvider", "name", request.getName());
            }
        } else {
            if (providerRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("OidcProvider", "name", request.getName());
            }
        }

        // Check for duplicate issuer
        if (providerRepository.existsByIssuer(request.getIssuer())) {
            throw new DuplicateResourceException("OidcProvider", "issuer", request.getIssuer());
        }

        // Create the provider entity
        OidcProvider provider = new OidcProvider(
                request.getName(),
                request.getIssuer(),
                request.getJwksUri()
        );
        provider.setClientId(request.getClientId());
        provider.setAudience(request.getAudience());
        provider.setRolesClaim(request.getRolesClaim());
        provider.setRolesMapping(request.getRolesMapping());
        provider.setEmailClaim(request.getEmailClaim());
        provider.setUsernameClaim(request.getUsernameClaim());
        provider.setNameClaim(request.getNameClaim());
        provider.setActive(true);
        if (tenantId != null) {
            provider.setTenantId(tenantId);
        }

        // Save the provider
        provider = providerRepository.save(provider);

        // Publish event (stubbed for now - will be implemented in task 11)
        publishOidcChangedEvent();

        // Audit log
        if (securityAuditService != null) {
            securityAuditService.log("OIDC_PROVIDER_CREATED", "CONFIG",
                    "OIDC_PROVIDER", provider.getId(), provider.getName(), null);
        }

        log.info("Created OIDC provider with id: {}", provider.getId());
        return provider;
    }

    /**
     * Updates an existing OIDC provider.
     * Only provided fields will be updated.
     * 
     * @param id The provider ID to update
     * @param request The update request with new values
     * @return The updated OIDC provider
     * @throws ResourceNotFoundException if the provider does not exist or is inactive
     * @throws DuplicateResourceException if updating name or issuer to one that already exists
     * @throws ValidationException if the issuer URL or JWKS URI format is invalid
     * 
     * Validates: Requirement 4.4
     */
    @Transactional
    public OidcProvider updateProvider(String id, UpdateOidcProviderRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Updating OIDC provider with id: {} for tenant: {}", id, tenantId);

        OidcProvider provider;
        if (tenantId != null) {
            provider = providerRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("OidcProvider", id));
        } else {
            provider = providerRepository.findByIdAndActiveTrue(id)
                    .orElseThrow(() -> new ResourceNotFoundException("OidcProvider", id));
        }

        // Update name if provided
        if (request.getName() != null && !request.getName().equals(provider.getName())) {
            if (providerRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("OidcProvider", "name", request.getName());
            }
            provider.setName(request.getName());
        }

        // Update issuer if provided
        if (request.getIssuer() != null && !request.getIssuer().equals(provider.getIssuer())) {
            validateUrl(request.getIssuer(), "issuer");
            if (providerRepository.existsByIssuer(request.getIssuer())) {
                throw new DuplicateResourceException("OidcProvider", "issuer", request.getIssuer());
            }
            provider.setIssuer(request.getIssuer());
            // Invalidate JWKS cache when issuer changes
            invalidateJwksCache(id);
        }

        // Update JWKS URI if provided
        if (request.getJwksUri() != null && !request.getJwksUri().equals(provider.getJwksUri())) {
            validateUrl(request.getJwksUri(), "jwksUri");
            provider.setJwksUri(request.getJwksUri());
            // Invalidate JWKS cache when JWKS URI changes
            invalidateJwksCache(id);
        }

        // Update client ID if provided
        if (request.getClientId() != null) {
            provider.setClientId(request.getClientId());
        }

        // Update audience if provided
        if (request.getAudience() != null) {
            provider.setAudience(request.getAudience());
        }

        // Update claim fields if provided
        if (request.getRolesClaim() != null) {
            validateClaimPath(request.getRolesClaim(), "rolesClaim");
            provider.setRolesClaim(request.getRolesClaim());
        }

        if (request.getRolesMapping() != null) {
            validateRolesMapping(request.getRolesMapping());
            provider.setRolesMapping(request.getRolesMapping());
        }

        if (request.getEmailClaim() != null) {
            validateClaimPath(request.getEmailClaim(), "emailClaim");
            provider.setEmailClaim(request.getEmailClaim());
        }

        if (request.getUsernameClaim() != null) {
            validateClaimPath(request.getUsernameClaim(), "usernameClaim");
            provider.setUsernameClaim(request.getUsernameClaim());
        }

        if (request.getNameClaim() != null) {
            validateClaimPath(request.getNameClaim(), "nameClaim");
            provider.setNameClaim(request.getNameClaim());
        }

        // Update active status if provided
        if (request.getActive() != null) {
            provider.setActive(request.getActive());
        }

        // Save the updated provider
        provider = providerRepository.save(provider);

        // Publish event
        publishOidcChangedEvent();

        // Audit log
        if (securityAuditService != null) {
            securityAuditService.log("OIDC_PROVIDER_UPDATED", "CONFIG",
                    "OIDC_PROVIDER", provider.getId(), provider.getName(), null);
        }

        log.info("Updated OIDC provider with id: {}", id);
        return provider;
    }

    /**
     * Soft-deletes an OIDC provider by marking it as inactive.
     * The provider is preserved in the database for audit purposes.
     * 
     * @param id The provider ID to delete
     * @throws ResourceNotFoundException if the provider does not exist or is already inactive
     * 
     * Validates: Requirement 4.5
     */
    @Transactional
    public void deleteProvider(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Deleting OIDC provider with id: {} for tenant: {}", id, tenantId);

        OidcProvider provider;
        if (tenantId != null) {
            provider = providerRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("OidcProvider", id));
        } else {
            provider = providerRepository.findByIdAndActiveTrue(id)
                    .orElseThrow(() -> new ResourceNotFoundException("OidcProvider", id));
        }

        // Soft delete - mark as inactive
        provider.setActive(false);
        providerRepository.save(provider);

        // Invalidate JWKS cache for this provider
        invalidateJwksCache(id);

        // Publish event
        publishOidcChangedEvent();

        // Audit log
        if (securityAuditService != null) {
            securityAuditService.log("OIDC_PROVIDER_DELETED", "CONFIG",
                    "OIDC_PROVIDER", provider.getId(), provider.getName(), null);
        }

        log.info("Soft-deleted OIDC provider with id: {}", id);
    }

    /**
     * Validates that a string is a valid URL format.
     * 
     * @param url The URL string to validate
     * @param fieldName The field name for error messages
     * @throws ValidationException if the URL format is invalid
     */
    private void validateUrl(String url, String fieldName) {
        if (url == null || url.isBlank()) {
            throw new ValidationException(fieldName, "URL is required");
        }

        try {
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol();
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                throw new ValidationException(fieldName, "URL must use http or https protocol");
            }
        } catch (MalformedURLException e) {
            throw new ValidationException(fieldName, "Invalid URL format: " + e.getMessage());
        }
    }

    /**
     * Validates claim path format.
     * Claim paths can be simple (e.g., "roles") or nested using dot notation (e.g., "realm_access.roles").
     * Only alphanumeric characters, dots, and underscores are allowed.
     * 
     * @param claimPath The claim path to validate
     * @param fieldName The field name for error messages
     * @throws ValidationException if the claim path is invalid
     * 
     * Validates: Requirements 4.1, 4.5
     */
    private void validateClaimPath(String claimPath, String fieldName) {
        if (claimPath == null || claimPath.isBlank()) {
            return; // null or empty is valid (will use defaults)
        }
        
        if (claimPath.length() > 200) {
            throw new ValidationException(fieldName, 
                "Claim path must not exceed 200 characters");
        }
        
        // Validate claim path format (alphanumeric, dots, underscores)
        if (!claimPath.matches("^[a-zA-Z0-9_.]+$")) {
            throw new ValidationException(fieldName, 
                "Claim path must contain only letters, numbers, dots, and underscores");
        }
    }

    /**
     * Validates roles mapping JSON format.
     * The roles mapping should be a valid JSON object that maps external role names to internal role names.
     * 
     * @param rolesMapping The roles mapping JSON string
     * @throws ValidationException if the JSON is invalid
     * 
     * Validates: Requirements 4.2, 4.3
     */
    private void validateRolesMapping(String rolesMapping) {
        if (rolesMapping == null || rolesMapping.isBlank()) {
            return; // null or empty is valid
        }
        
        try {
            // Attempt to parse as JSON to validate format
            objectMapper.readTree(rolesMapping);
        } catch (Exception e) {
            throw new ValidationException("rolesMapping", 
                "Invalid JSON format: " + e.getMessage());
        }
    }

    /**
     * Publishes an OIDC configuration changed event to Kafka.
     * Only publishes if the event publisher is available (Kafka is enabled).
     * 
     * Validates: Requirement 4.6
     */
    private void publishOidcChangedEvent() {
        if (eventPublisher != null) {
            List<OidcProvider> providers = providerRepository.findByActiveTrueOrderByNameAsc();
            eventPublisher.publishOidcChanged(providers);
        } else {
            log.debug("Event publishing disabled - OIDC configuration changed");
        }
    }

    /**
     * Invalidates the JWKS cache entry for a provider.
     * Uses the JwksCache component to evict the cached JWKS.
     * 
     * @param providerId The provider ID to invalidate cache for
     * 
     * Validates: Requirement 4.7
     */
    private void invalidateJwksCache(String providerId) {
        if (jwksCache != null) {
            jwksCache.invalidate(providerId);
            log.debug("Invalidated JWKS cache for provider: {}", providerId);
        } else {
            log.debug("JWKS cache not available - skipping invalidation for provider: {}", providerId);
        }
    }

    /**
     * Retrieves an OIDC provider by its ID.
     * Only returns active providers.
     * 
     * @param id The provider ID
     * @return The provider if found
     * @throws ResourceNotFoundException if the provider does not exist or is inactive
     */
    @Transactional(readOnly = true)
    public OidcProvider getProvider(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Getting OIDC provider with id: {} for tenant: {}", id, tenantId);
        if (tenantId != null) {
            return providerRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("OidcProvider", id));
        }
        return providerRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("OidcProvider", id));
    }

    /**
     * Retrieves an OIDC provider by its issuer URL.
     * Only returns active providers.
     * 
     * @param issuer The issuer URL
     * @return The provider if found
     * @throws ResourceNotFoundException if no provider with the issuer exists or is inactive
     */
    @Transactional(readOnly = true)
    public OidcProvider getProviderByIssuer(String issuer) {
        log.debug("Getting OIDC provider with issuer: {}", issuer);
        return providerRepository.findByIssuerAndActiveTrue(issuer)
                .orElseThrow(() -> new ResourceNotFoundException("OidcProvider with issuer: " + issuer));
    }
}
