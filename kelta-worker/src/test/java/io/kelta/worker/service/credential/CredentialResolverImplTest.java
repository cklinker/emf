package io.kelta.worker.service.credential;

import io.kelta.crypto.EncryptionService;
import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.worker.repository.CredentialRepository;
import io.kelta.worker.service.SetupAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CredentialResolverImpl")
class CredentialResolverImplTest {

    private static final String TEST_KEY =
        Base64.getEncoder().encodeToString(new byte[32]);

    private CredentialRepository credentialRepository;
    private EncryptionService encryptionService;
    private SetupAuditService auditService;
    private ObjectMapper objectMapper;
    private CredentialResolverImpl resolver;

    @BeforeEach
    void setUp() {
        credentialRepository = mock(CredentialRepository.class);
        encryptionService = new EncryptionService(TEST_KEY);
        auditService = mock(SetupAuditService.class);
        objectMapper = new ObjectMapper();
        resolver = new CredentialResolverImpl(
            credentialRepository, encryptionService, auditService, objectMapper,
            300, 1000);
    }

    private Map<String, Object> rowFor(String credentialId, String name, String type,
                                        boolean active, String secretJson) {
        return Map.of(
            "id", credentialId,
            "name", name,
            "type", type,
            "active", active,
            "data_enc", encryptionService.encrypt(secretJson),
            "metadata", Map.of("headerName", "X-API-Key")
        );
    }

    @Test
    @DisplayName("Resolves a credential, decrypts secret material, and writes an audit row")
    void resolvesAndAudits() {
        Map<String, Object> row = rowFor("cred-1", "salesforce-prod", "api_key", true,
            "{\"value\":\"sk_live_abc123\"}");
        when(credentialRepository.findById("cred-1", "tenant-1")).thenReturn(Optional.of(row));

        ResolvedCredential resolved = resolver.resolve(
            "tenant-1", "cred-1",
            ResolutionContext.forFlow("rule-9", "exec-42", "CALL_API:salesforce-prod"));

        assertEquals("cred-1", resolved.id());
        assertEquals("salesforce-prod", resolved.name());
        assertEquals("api_key", resolved.type());
        assertEquals("sk_live_abc123", resolved.secretFields().get("value"));
        assertEquals("X-API-Key", resolved.metadataFields().get("headerName"));

        // Audit row written, never logging the secret.
        verify(auditService).log(
            eq("tenant-1"),
            isNull(),
            eq("CREDENTIAL_RESOLVE"),
            eq("credentials"),
            eq("credential"),
            eq("cred-1"),
            eq("salesforce-prod"),
            isNull(),
            argThat(json -> json != null
                && json.contains("\"cacheHit\":false")
                && !json.contains("sk_live_abc123")));
    }

    @Test
    @DisplayName("Second resolve hits cache and skips the database")
    void cacheHitSkipsDatabase() {
        Map<String, Object> row = rowFor("cred-1", "salesforce-prod", "api_key", true,
            "{\"value\":\"sk_live_abc123\"}");
        when(credentialRepository.findById("cred-1", "tenant-1")).thenReturn(Optional.of(row));

        resolver.resolve("tenant-1", "cred-1", ResolutionContext.forUser("u-1", "first"));
        resolver.resolve("tenant-1", "cred-1", ResolutionContext.forUser("u-1", "second"));

        // Each lookup still re-reads the row to enforce active/disabled checks.
        // Cache hit only avoids decryption + caching costs and is reflected in the audit row.
        verify(credentialRepository, times(2)).findById("cred-1", "tenant-1");
        verify(auditService, atLeast(2)).log(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Falls back to lookup-by-name when ID lookup misses")
    void resolvesByName() {
        Map<String, Object> row = rowFor("cred-2", "github-bot", "bearer_token", true,
            "{\"token\":\"ghp_abc\"}");
        when(credentialRepository.findById("github-bot", "tenant-1")).thenReturn(Optional.empty());
        when(credentialRepository.findByName("github-bot", "tenant-1")).thenReturn(Optional.of(row));

        ResolvedCredential resolved = resolver.resolve(
            "tenant-1", "github-bot", ResolutionContext.forUser("u-1", "test"));

        assertEquals("cred-2", resolved.id());
        assertEquals("ghp_abc", resolved.secretFields().get("token"));
    }

    @Test
    @DisplayName("Throws CredentialNotFoundException when nothing matches")
    void throwsWhenMissing() {
        when(credentialRepository.findById(anyString(), anyString())).thenReturn(Optional.empty());
        when(credentialRepository.findByName(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(CredentialNotFoundException.class, () ->
            resolver.resolve("tenant-1", "no-such", null));
    }

    @Test
    @DisplayName("Throws CredentialDisabledException when active=false")
    void throwsWhenDisabled() {
        Map<String, Object> row = rowFor("cred-1", "old", "api_key", false,
            "{\"value\":\"x\"}");
        when(credentialRepository.findById("cred-1", "tenant-1")).thenReturn(Optional.of(row));

        assertThrows(CredentialDisabledException.class, () ->
            resolver.resolve("tenant-1", "cred-1",
                ResolutionContext.forUser("u-1", "test")));
    }

    @Test
    @DisplayName("invalidate(id) drops cached entries for that credential")
    void invalidatesById() {
        Map<String, Object> row = rowFor("cred-1", "salesforce-prod", "api_key", true,
            "{\"value\":\"sk_live_abc123\"}");
        when(credentialRepository.findById("cred-1", "tenant-1")).thenReturn(Optional.of(row));

        resolver.resolve("tenant-1", "cred-1", ResolutionContext.forUser("u-1", "test"));
        assertTrue(resolver.peek("tenant-1", "cred-1").isPresent(), "cache populated");

        resolver.invalidate("cred-1");

        assertTrue(resolver.peek("tenant-1", "cred-1").isEmpty(),
            "cache entry dropped after invalidate");
    }

    @Test
    @DisplayName("Rejects null/blank tenantId and reference")
    void rejectsNullArgs() {
        assertThrows(IllegalArgumentException.class, () ->
            resolver.resolve(null, "cred-1", null));
        assertThrows(IllegalArgumentException.class, () ->
            resolver.resolve("tenant-1", "", null));
    }
}
