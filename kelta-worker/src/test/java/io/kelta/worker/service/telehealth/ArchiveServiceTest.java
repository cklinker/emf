package io.kelta.worker.service.telehealth;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.ParticipantShareSupport;
import io.kelta.worker.service.S3StorageService;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ArchiveService")
class ArchiveServiceTest {

    private static final String TENANT = "t1";
    private static final ChatService.ChatActor STAFF =
            new ChatService.ChatActor("u-staff", "staff@example.com", "INTERNAL");

    private JdbcTemplate jdbcTemplate;
    private QueryEngine queryEngine;
    private S3StorageService storageService;
    private ParticipantShareSupport participantShareSupport;
    private TenantQuotaResolver quotaResolver;
    private ArchiveService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        queryEngine = mock(QueryEngine.class);
        CollectionRegistry registry = mock(CollectionRegistry.class);
        when(registry.get(anyString())).thenReturn(mock(CollectionDefinition.class));
        storageService = mock(S3StorageService.class);
        when(storageService.isEnabled()).thenReturn(true);
        participantShareSupport = mock(ParticipantShareSupport.class);
        quotaResolver = mock(TenantQuotaResolver.class);
        Map<String, Object> quotas = new LinkedHashMap<>();
        quotas.put(TenantTierQuotas.KEY_RETENTION_YEARS, 7);
        when(quotaResolver.resolve(TENANT)).thenReturn(quotas);
        service = new ArchiveService(jdbcTemplate, queryEngine, registry, storageService,
                participantShareSupport, quotaResolver);
    }

    // ------------------------------------------------------------- Determinism

    @Test
    @DisplayName("canonical JSON sorts object keys so equal content hashes identically regardless of insertion order")
    void canonicalJsonIsDeterministic() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("b", "two");
        a.put("a", "one");
        a.put("nested", Map.of("z", 1, "y", 2));

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("nested", Map.of("y", 2, "z", 1));
        b.put("a", "one");
        b.put("b", "two");

        String jsonA = ArchiveService.toCanonicalJson(a);
        String jsonB = ArchiveService.toCanonicalJson(b);

        assertThat(jsonA).isEqualTo(jsonB);
        assertThat(jsonA).isEqualTo("{\"a\":\"one\",\"b\":\"two\",\"nested\":{\"y\":2,\"z\":1}}");
        assertThat(ArchiveService.sha256Hex(jsonA.getBytes()))
                .isEqualTo(ArchiveService.sha256Hex(jsonB.getBytes()));
    }

    @Test
    @DisplayName("sha256 is a stable 64-hex digest for identical bytes and differs on any change")
    void sha256IsStable() {
        byte[] bytes = "{\"schemaVersion\":1}".getBytes();
        String first = ArchiveService.sha256Hex(bytes);
        String second = ArchiveService.sha256Hex(bytes);
        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(ArchiveService.sha256Hex("{\"schemaVersion\":2}".getBytes())).isNotEqualTo(first);
    }

    @Test
    @DisplayName("the same set of messages always serializes to the same bytes and hash")
    void identicalMessagesHashIdentically() {
        // Two independent canonical assemblies of the same ordered messages must
        // produce byte-identical JSON (the transcript, minus the archive-time
        // timestamp, is the reproducible part the sha256 pins).
        List<Object> a = ArchiveService.canonicalMessages(List.of(message("m1", "hi"), message("m2", "bye")));
        List<Object> b = ArchiveService.canonicalMessages(List.of(message("m1", "hi"), message("m2", "bye")));
        String jsonA = ArchiveService.toCanonicalJson(Map.of("messages", a));
        String jsonB = ArchiveService.toCanonicalJson(Map.of("messages", b));
        assertThat(jsonA).isEqualTo(jsonB);
        assertThat(ArchiveService.sha256Hex(jsonA.getBytes()))
                .isEqualTo(ArchiveService.sha256Hex(jsonB.getBytes()));
    }

    // ------------------------------------------------------------- Idempotency

    @Test
    @DisplayName("returns the existing archive without re-archiving when one already exists")
    void idempotentReturnsExisting() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("id", "arch-existing");
        existing.put("sha256", "deadbeef");
        when(jdbcTemplate.queryForList(contains("FROM telehealth_archive"),
                eq(TENANT), eq("CONVERSATION"), eq("conv-1")))
                .thenReturn(List.of(existing));

        Map<String, Object> result = service.archiveConversation(TENANT, STAFF, "conv-1");

        assertThat(result.get("id")).isEqualTo("arch-existing");
        // No new artifact, no status change, no share when short-circuited.
        verify(storageService, never()).uploadObject(anyString(), any(), anyString());
        verify(queryEngine, never()).update(any(), anyString(), any());
        verify(participantShareSupport, never()).grant(anyString(), anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------- Retention math

    @Test
    @DisplayName("retentionUntil = archivedAt + retentionYears (from the tenant setting)")
    void retentionMath() {
        stubConversation(List.of(message("m1", "hi")));
        when(jdbcTemplate.queryForList(contains("FROM telehealth_archive"),
                eq(TENANT), eq("CONVERSATION"), eq("conv-1")))
                .thenReturn(List.of());
        ArgumentCaptor<Map<String, Object>> created = captureCreates();

        Instant before = Instant.now();
        service.archiveConversation(TENANT, STAFF, "conv-1");
        Instant after = Instant.now();

        Map<String, Object> archiveData = firstArchiveCreate(created.getAllValues());
        Instant archivedAt = Instant.parse((String) archiveData.get("archivedAt"));
        Instant retentionUntil = Instant.parse((String) archiveData.get("retentionUntil"));
        assertThat(archivedAt).isBetween(before, after);
        Instant expected = archivedAt.atZone(ZoneOffset.UTC).plusYears(7).toInstant();
        assertThat(retentionUntil).isEqualTo(expected);
        assertThat(archiveData.get("legalHold")).isEqualTo(false);
    }

    // ------------------------------------------------------------- Side effects

    @Test
    @DisplayName("first archive uploads two artifacts, re-parents message attachments, flips status, grants portal share")
    void firstArchiveSideEffects() {
        stubConversation(List.of(message("m1", "hi")));
        when(jdbcTemplate.queryForList(contains("FROM telehealth_archive"),
                eq(TENANT), eq("CONVERSATION"), eq("conv-1")))
                .thenReturn(List.of());
        captureCreates();

        service.archiveConversation(TENANT, STAFF, "conv-1");

        // JSON + PDF uploaded.
        verify(storageService).uploadObject(contains(".json"), any(), eq("application/json"));
        verify(storageService).uploadObject(contains(".pdf"), any(), eq("application/pdf"));
        // Message attachments re-parented onto the archive row (collection +
        // record_id → archive, tenant-scoped, message id in the IN list).
        verify(jdbcTemplate).update(contains("UPDATE file_attachment SET collection_id"),
                eq("telehealth-archives"), anyString(), eq(TENANT), eq("m1"));
        // Conversation flipped to ARCHIVED.
        verify(queryEngine).update(any(), eq("conv-1"), eq(Map.of("status", "ARCHIVED")));
        // Portal participant got a share on the archive.
        verify(participantShareSupport).grant(eq("telehealth-archives"), anyString(),
                eq("u-portal"), eq("READ"));
    }

    // ------------------------------------------------------------- Helpers

    private void stubConversation(List<Map<String, Object>> messages) {
        Map<String, Object> conversation = new LinkedHashMap<>();
        conversation.put("id", "conv-1");
        conversation.put("subject", "Knee pain");
        conversation.put("status", "CLOSED");
        conversation.put("closed_at", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        when(jdbcTemplate.queryForList(contains("FROM chat_conversation"), eq("conv-1"), eq(TENANT)))
                .thenReturn(List.of(conversation));

        Map<String, Object> portal = new LinkedHashMap<>();
        portal.put("user_id", "u-portal");
        portal.put("role", "PORTAL");
        portal.put("joined_at", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        when(jdbcTemplate.queryForList(contains("FROM chat_participant"), eq(TENANT), eq("conv-1")))
                .thenReturn(List.of(portal));

        when(jdbcTemplate.queryForList(contains("FROM chat_message"), eq(TENANT), eq("conv-1")))
                .thenReturn(messages);
        // The attachment manifest lookup returns Mockito's default empty list.
    }

    private static Map<String, Object> message(String id, String body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("sender_id", "u-portal");
        m.put("sender_type", "PORTAL");
        m.put("kind", "TEXT");
        m.put("body", body);
        m.put("sent_at", Timestamp.from(Instant.parse("2026-01-01T00:00:01Z")));
        return m;
    }

    /** Echo QueryEngine.create back so the returned archive row carries the sha/retention data. */
    private ArgumentCaptor<Map<String, Object>> captureCreates() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        when(queryEngine.create(any(), captor.capture())).thenAnswer(inv -> {
            Map<String, Object> data = inv.getArgument(1);
            Map<String, Object> created = new LinkedHashMap<>(data);
            created.putIfAbsent("id", "arch-new");
            return created;
        });
        return captor;
    }

    private static Map<String, Object> firstArchiveCreate(List<Map<String, Object>> creates) {
        return creates.stream()
                .filter(m -> "CONVERSATION".equals(m.get("sourceType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no archive create captured"));
    }
}
