package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateConnectedAppRequest;
import com.emf.controlplane.entity.ConnectedApp;
import com.emf.controlplane.entity.ConnectedAppToken;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ConnectedAppRepository;
import com.emf.controlplane.repository.ConnectedAppTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectedAppService {

    private static final Logger log = LoggerFactory.getLogger(ConnectedAppService.class);

    private final ConnectedAppRepository appRepository;
    private final ConnectedAppTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConnectedAppService(ConnectedAppRepository appRepository,
                               ConnectedAppTokenRepository tokenRepository) {
        this.appRepository = appRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional(readOnly = true)
    public List<ConnectedApp> listApps(String tenantId) {
        return appRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public ConnectedApp getApp(String id) {
        return appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConnectedApp", id));
    }

    /**
     * Creates a new connected app with a generated clientId and clientSecret.
     * Returns a two-element String array: [0] = saved entity id, [1] = plaintext secret.
     * The caller should use the entity id to reload the entity and build the response.
     */
    @Transactional
    @SetupAudited(section = "ConnectedApps", entityType = "ConnectedApp")
    public ConnectedAppCreateResult createApp(String tenantId, String userId, CreateConnectedAppRequest request) {
        log.info("Creating connected app '{}' for tenant: {}", request.getName(), tenantId);

        String clientId = generateClientId();
        String clientSecret = generateClientSecret();
        String secretHash = sha256Hex(clientSecret);

        ConnectedApp app = new ConnectedApp();
        app.setTenantId(tenantId);
        app.setName(request.getName());
        app.setDescription(request.getDescription());
        app.setClientId(clientId);
        app.setClientSecretHash(secretHash);
        app.setRedirectUris(request.getRedirectUris());
        app.setScopes(request.getScopes());
        app.setIpRestrictions(request.getIpRestrictions());
        app.setRateLimitPerHour(request.getRateLimitPerHour() != null ? request.getRateLimitPerHour() : 10000);
        app.setActive(request.getActive() != null ? request.getActive() : true);
        app.setCreatedBy(userId);

        ConnectedApp saved = appRepository.save(app);
        return new ConnectedAppCreateResult(saved, clientSecret);
    }

    @Transactional
    @SetupAudited(section = "ConnectedApps", entityType = "ConnectedApp")
    public ConnectedApp updateApp(String id, CreateConnectedAppRequest request) {
        log.info("Updating connected app: {}", id);
        ConnectedApp app = getApp(id);

        if (request.getName() != null) app.setName(request.getName());
        if (request.getDescription() != null) app.setDescription(request.getDescription());
        if (request.getRedirectUris() != null) app.setRedirectUris(request.getRedirectUris());
        if (request.getScopes() != null) app.setScopes(request.getScopes());
        if (request.getIpRestrictions() != null) app.setIpRestrictions(request.getIpRestrictions());
        if (request.getRateLimitPerHour() != null) app.setRateLimitPerHour(request.getRateLimitPerHour());
        if (request.getActive() != null) app.setActive(request.getActive());

        return appRepository.save(app);
    }

    @Transactional
    @SetupAudited(section = "ConnectedApps", entityType = "ConnectedApp")
    public void deleteApp(String id) {
        log.info("Deleting connected app: {}", id);
        ConnectedApp app = getApp(id);
        appRepository.delete(app);
    }

    /**
     * Rotates the client secret for a connected app.
     * Returns the new plaintext secret (shown once).
     */
    @Transactional
    @SetupAudited(section = "ConnectedApps", entityType = "ConnectedApp", action = "UPDATED")
    public ConnectedAppCreateResult rotateSecret(String id) {
        log.info("Rotating secret for connected app: {}", id);
        ConnectedApp app = getApp(id);

        String newSecret = generateClientSecret();
        String newHash = sha256Hex(newSecret);
        app.setClientSecretHash(newHash);

        ConnectedApp saved = appRepository.save(app);
        return new ConnectedAppCreateResult(saved, newSecret);
    }

    // --- Tokens ---

    @Transactional(readOnly = true)
    public List<ConnectedAppToken> listTokens(String connectedAppId) {
        return tokenRepository.findByConnectedAppIdAndRevokedFalseOrderByIssuedAtDesc(connectedAppId);
    }

    // --- Helpers ---

    private String generateClientId() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return "emf_" + hex;
    }

    private String generateClientSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Result holder for create/rotate operations that need to return the plaintext secret.
     */
    public static class ConnectedAppCreateResult {
        private final ConnectedApp app;
        private final String plaintextSecret;

        public ConnectedAppCreateResult(ConnectedApp app, String plaintextSecret) {
            this.app = app;
            this.plaintextSecret = plaintextSecret;
        }

        public ConnectedApp getApp() { return app; }
        public String getPlaintextSecret() { return plaintextSecret; }
    }
}
