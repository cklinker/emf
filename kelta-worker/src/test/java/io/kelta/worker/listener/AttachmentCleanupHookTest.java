package io.kelta.worker.listener;

import io.kelta.worker.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentCleanupHookTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private S3StorageService storageService;

    private AttachmentCleanupHook hook;

    private static final String TENANT_ID = "tenant-1";
    private static final String RECORD_ID = "rec-1";

    @BeforeEach
    void setUp() {
        hook = new AttachmentCleanupHook(jdbcTemplate, storageService);
    }

    @Test
    void isWildcardHookRunningLate() {
        assertEquals("*", hook.getCollectionName());
        assertEquals(200, hook.getOrder());
    }

    @Test
    void afterDelete_cleansAttachmentRowsAndS3Objects() {
        when(jdbcTemplate.queryForList(anyString(), eq(RECORD_ID), eq(TENANT_ID)))
                .thenReturn(List.of(
                        Map.of("id", "att-1", "storage_key", "k1"),
                        Map.of("id", "att-2", "storage_key", "k2")));
        when(storageService.isEnabled()).thenReturn(true);
        when(jdbcTemplate.update(anyString(), eq(RECORD_ID), eq(TENANT_ID))).thenReturn(2);

        hook.afterDelete("orders", RECORD_ID, TENANT_ID);

        verify(storageService).deleteObject("k1");
        verify(storageService).deleteObject("k2");
        verify(jdbcTemplate).update(contains("DELETE FROM file_attachment"), eq(RECORD_ID), eq(TENANT_ID));
    }

    @Test
    void afterDelete_skipsAttachmentsCollection() {
        hook.afterDelete("attachments", "att-1", TENANT_ID);

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(storageService);
    }

    @Test
    void afterDelete_noAttachments_doesNothing() {
        when(jdbcTemplate.queryForList(anyString(), eq(RECORD_ID), eq(TENANT_ID)))
                .thenReturn(Collections.emptyList());

        hook.afterDelete("orders", RECORD_ID, TENANT_ID);

        verify(storageService, never()).deleteObject(anyString());
        verify(jdbcTemplate, never()).update(anyString(), eq(RECORD_ID), eq(TENANT_ID));
    }

    @Test
    void afterDelete_s3Disabled_deletesRowsOnly() {
        when(jdbcTemplate.queryForList(anyString(), eq(RECORD_ID), eq(TENANT_ID)))
                .thenReturn(List.of(Map.of("id", "att-1", "storage_key", "k1")));
        when(storageService.isEnabled()).thenReturn(false);
        when(jdbcTemplate.update(anyString(), eq(RECORD_ID), eq(TENANT_ID))).thenReturn(1);

        hook.afterDelete("orders", RECORD_ID, TENANT_ID);

        verify(storageService, never()).deleteObject(anyString());
        verify(jdbcTemplate).update(contains("DELETE FROM file_attachment"), eq(RECORD_ID), eq(TENANT_ID));
    }

    @Test
    void afterDelete_nullIdentifiers_noop() {
        hook.afterDelete("orders", null, TENANT_ID);
        hook.afterDelete("orders", RECORD_ID, null);

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(storageService);
    }
}
