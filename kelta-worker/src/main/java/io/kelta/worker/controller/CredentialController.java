package io.kelta.worker.controller;

import io.kelta.crypto.EncryptionService;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.credential.CredentialTemplate;
import io.kelta.runtime.credential.CredentialTemplateRegistry;
import io.kelta.runtime.credential.CredentialTestResult;
import io.kelta.runtime.credential.CredentialType;
import io.kelta.runtime.credential.CredentialTypeRegistry;
import io.kelta.runtime.credential.OAuthRefreshOutcome;
import io.kelta.runtime.credential.types.OAuth2AuthorizationCodeCredentialType;
import io.kelta.worker.repository.CredentialOAuthTokenRepository;
import io.kelta.worker.repository.CredentialRepository;
import io.kelta.worker.service.credential.CredentialTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Credential metadata, test, and OAuth-dance endpoints.
 *
 * <p>Standard CRUD on the {@code credentials} system collection is handled by
 * the dynamic collection router; this controller adds:
 * <ul>
 *   <li>Listing of registered credential types and provider templates</li>
 *   <li>Live connection tests for saved or in-flight credentials</li>
 *   <li>OAuth 2.0 Authorization Code dance (authorize URL + completion)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/credentials")
@ConditionalOnProperty(name = "kelta.encryption.key")
public class CredentialController {

    private static final Logger log = LoggerFactory.getLogger(CredentialController.class);
    private static final String OAUTH_STATE_KEY_PREFIX = "credential:oauth:state:";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CredentialTypeRegistry typeRegistry;
    private final CredentialTemplateRegistry templateRegistry;
    private final CredentialTestService testService;
    private final CredentialRepository credentialRepository;
    private final CredentialOAuthTokenRepository oauthTokenRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final String oauthRedirectUri;

    public CredentialController(CredentialTypeRegistry typeRegistry,
                                 CredentialTemplateRegistry templateRegistry,
                                 CredentialTestService testService,
                                 CredentialRepository credentialRepository,
                                 CredentialOAuthTokenRepository oauthTokenRepository,
                                 EncryptionService encryptionService,
                                 ObjectMapper objectMapper,
                                 StringRedisTemplate redis,
                                 @Value("${kelta.credentials.oauth-redirect-uri:}") String oauthRedirectUri) {
        this.typeRegistry = typeRegistry;
        this.templateRegistry = templateRegistry;
        this.testService = testService;
        this.credentialRepository = credentialRepository;
        this.oauthTokenRepository = oauthTokenRepository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.oauthRedirectUri = oauthRedirectUri;
    }

    // ---------------------------------------------------------------------
    // Type and template catalogs
    // ---------------------------------------------------------------------

    @GetMapping("/types")
    public ResponseEntity<?> listTypes() {
        List<Map<String, Object>> types = typeRegistry.all().stream()
            .map(t -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("key", t.getKey());
                entry.put("displayName", t.getDisplayName());
                entry.put("description", t.getDescription());
                entry.put("inputSchema", t.getInputSchema());
                entry.put("supportsOAuthRefresh", t.supportsOAuthRefresh());
                return entry;
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("data", types));
    }

    @GetMapping("/templates")
    public ResponseEntity<?> listTemplates() {
        List<Map<String, Object>> templates = templateRegistry.all().stream()
            .map(t -> Map.<String, Object>of(
                "key", t.key(),
                "name", t.name(),
                "type", t.type(),
                "iconUrl", t.iconUrl() == null ? "" : t.iconUrl(),
                "defaults", t.defaults()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("data", templates));
    }

    // ---------------------------------------------------------------------
    // Live connection tests
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testSaved(@PathVariable String id) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }
        CredentialTestResult result = testService.test(id, tenantId);
        return ResponseEntity.ok(toMap(result));
    }

    /**
     * Tests a credential payload before it has been saved. Body shape:
     * <pre>{ "type": "smtp", "data": { "host": "...", "port": 587, ... } }</pre>
     * The {@code data} object should match the type's input schema.
     */
    @PostMapping("/test")
    public ResponseEntity<?> testInline(@RequestBody Map<String, Object> body) {
        if (TenantContext.get() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }
        String type = (String) body.get("type");
        Object data = body.get("data");
        if (type == null || !(data instanceof Map<?, ?> dataMap)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Body must contain 'type' and 'data' object"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> plaintext = (Map<String, Object>) dataMap;
        CredentialTestResult result = testService.testInline(type, plaintext);
        return ResponseEntity.ok(toMap(result));
    }

    // ---------------------------------------------------------------------
    // OAuth 2.0 Authorization Code dance
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/oauth/authorize-url")
    public ResponseEntity<?> beginOAuth(@PathVariable String id) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }
        Optional<Map<String, Object>> row = credentialRepository.findById(id, tenantId);
        if (row.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> record = row.get();
        String typeKey = (String) record.get("type");
        if (!"oauth2_authorization_code".equals(typeKey)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Credential type does not support authorization-code flow"));
        }
        ObjectNode metadata = parseMetadata(record.get("metadata"));
        ObjectNode plaintext = decryptMaterial((String) record.get("data_enc"));

        String authUrl = textOrNull(metadata, "authorizationUrl");
        String clientId = textOrNull(plaintext, "clientId");
        if (authUrl == null || clientId == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "authorizationUrl and clientId are required"));
        }

        String state = randomState();
        // Persist (state -> credentialId,tenantId) in Redis with TTL so callback can validate.
        String stateKey = OAUTH_STATE_KEY_PREFIX + state;
        redis.opsForValue().set(stateKey, id + "|" + tenantId, OAUTH_STATE_TTL);

        StringBuilder url = new StringBuilder(authUrl);
        url.append(authUrl.contains("?") ? '&' : '?');
        url.append("response_type=code");
        url.append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        String redirectUri = oauthRedirectUri.isBlank()
            ? textOrNull(metadata, "redirectUri")
            : oauthRedirectUri;
        if (redirectUri != null) {
            url.append("&redirect_uri=")
               .append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        }
        JsonNode scopesNode = metadata.get("scopes");
        if (scopesNode != null && scopesNode.isArray() && !scopesNode.isEmpty()) {
            String scopes = String.join(" ", asTextList(scopesNode));
            url.append("&scope=")
               .append(URLEncoder.encode(scopes, StandardCharsets.UTF_8));
        }

        return ResponseEntity.ok(Map.of(
            "authUrl", url.toString(),
            "state", state,
            "expiresInSeconds", OAUTH_STATE_TTL.toSeconds()));
    }

    /**
     * Completes the OAuth dance after the user authorizes via the popup.
     * Body: <pre>{ "state": "...", "code": "...", "redirectUri": "..." }</pre>
     * The redirectUri must match the one used in the authorize-url step.
     */
    @PostMapping("/oauth/complete")
    public ResponseEntity<?> completeOAuth(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }
        String state = (String) body.get("state");
        String code = (String) body.get("code");
        String redirectUri = (String) body.get("redirectUri");
        if (state == null || code == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "state and code are required"));
        }

        String stateKey = OAUTH_STATE_KEY_PREFIX + state;
        String stored = redis.opsForValue().getAndDelete(stateKey);
        if (stored == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid or expired state"));
        }
        String[] parts = stored.split("\\|", 2);
        String credentialId = parts[0];
        String storedTenantId = parts.length > 1 ? parts[1] : null;
        if (!tenantId.equals(storedTenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "State does not belong to current tenant"));
        }

        Optional<Map<String, Object>> row = credentialRepository.findById(credentialId, tenantId);
        if (row.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> record = row.get();

        CredentialType type = typeRegistry.find((String) record.get("type")).orElse(null);
        if (!(type instanceof OAuth2AuthorizationCodeCredentialType authCodeType)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Credential is not an authorization-code OAuth credential"));
        }

        ObjectNode metadata = parseMetadata(record.get("metadata"));
        ObjectNode plaintext = decryptMaterial((String) record.get("data_enc"));
        String effectiveRedirectUri = redirectUri != null ? redirectUri
            : (oauthRedirectUri.isBlank() ? textOrNull(metadata, "redirectUri") : oauthRedirectUri);
        if (effectiveRedirectUri == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "redirectUri is required"));
        }
        try {
            OAuthRefreshOutcome outcome = authCodeType.exchangeCode(
                plaintext, metadata, code, effectiveRedirectUri);
            if (outcome.expiresAt() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Token endpoint did not return expires_in"));
            }
            oauthTokenRepository.upsert(
                credentialId, tenantId,
                encryptionService.encrypt(outcome.accessToken()),
                outcome.refreshToken() == null ? null
                    : encryptionService.encrypt(outcome.refreshToken()),
                outcome.tokenType(),
                outcome.expiresAt(),
                outcome.scope());
            credentialRepository.updateTestStatus(credentialId, tenantId, "OK", null);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "expiresAt", outcome.expiresAt().toString(),
                "scope", outcome.scope() == null ? "" : outcome.scope()));
        } catch (Exception e) {
            log.warn("OAuth completion failed for credential {}: {}", credentialId, e.getMessage());
            credentialRepository.updateTestStatus(credentialId, tenantId, "FAILED", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Map<String, Object> toMap(CredentialTestResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", result.ok());
        out.put("message", result.message());
        out.put("details", result.details() == null ? Map.of() : result.details());
        return out;
    }

    private ObjectNode parseMetadata(Object metadata) {
        if (metadata == null) {
            return objectMapper.createObjectNode();
        }
        if (metadata instanceof Map<?, ?> map) {
            return objectMapper.valueToTree(new HashMap<>(map));
        }
        try {
            JsonNode node = objectMapper.readTree(metadata.toString());
            return node instanceof ObjectNode obj ? obj : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private ObjectNode decryptMaterial(String dataEnc) {
        if (dataEnc == null || dataEnc.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String json = encryptionService.decrypt(dataEnc);
        try {
            JsonNode node = objectMapper.readTree(json);
            return node instanceof ObjectNode obj ? obj : objectMapper.createObjectNode();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse decrypted credential material", e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isTextual()) {
            String s = v.stringValue();
            return s.isBlank() ? null : s;
        }
        return v.toString();
    }

    private static List<String> asTextList(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            if (n.isTextual()) {
                out.add(n.stringValue());
            }
        }
        return out;
    }

    private static String randomState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
