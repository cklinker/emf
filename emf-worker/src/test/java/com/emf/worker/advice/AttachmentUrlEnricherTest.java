package com.emf.worker.advice;

import com.emf.worker.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentUrlEnricherTest {

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    private AttachmentUrlEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new AttachmentUrlEnricher(s3StorageService);
    }

    @Test
    void supports_returnsTrue() {
        assertTrue(enricher.supports(null, null));
    }

    @Test
    void beforeBodyWrite_enrichesSingleAttachmentResource() {
        Duration expiry = Duration.ofMinutes(15);
        when(s3StorageService.getDefaultExpiry()).thenReturn(expiry);
        when(s3StorageService.getPresignedDownloadUrl("tenant/col/rec/file.jpg", expiry))
                .thenReturn("https://s3.example.com/presigned-url");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("storageKey", "tenant/col/rec/file.jpg");
        attributes.put("fileName", "file.jpg");

        Map<String, Object> resource = new HashMap<>();
        resource.put("type", "attachments");
        resource.put("id", "att-1");
        resource.put("attributes", attributes);

        Map<String, Object> body = new HashMap<>();
        body.put("data", resource);

        Object result = enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) result;
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resultBody.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");

        assertEquals("https://s3.example.com/presigned-url", attrs.get("downloadUrl"));
    }

    @Test
    void beforeBodyWrite_enrichesListOfAttachmentResources() {
        Duration expiry = Duration.ofMinutes(15);
        when(s3StorageService.getDefaultExpiry()).thenReturn(expiry);
        when(s3StorageService.getPresignedDownloadUrl("key1", expiry)).thenReturn("url1");
        when(s3StorageService.getPresignedDownloadUrl("key2", expiry)).thenReturn("url2");

        List<Map<String, Object>> dataList = new ArrayList<>();
        dataList.add(createAttachmentResource("att-1", "key1"));
        dataList.add(createAttachmentResource("att-2", "key2"));

        Map<String, Object> body = new HashMap<>();
        body.put("data", dataList);

        enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultList = (List<Map<String, Object>>) body.get("data");

        assertEquals("url1", getAttributes(resultList.get(0)).get("downloadUrl"));
        assertEquals("url2", getAttributes(resultList.get(1)).get("downloadUrl"));
    }

    @Test
    void beforeBodyWrite_skipsNonAttachmentResources() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", "test");

        Map<String, Object> resource = new HashMap<>();
        resource.put("type", "products");
        resource.put("id", "p-1");
        resource.put("attributes", attributes);

        Map<String, Object> body = new HashMap<>();
        body.put("data", resource);

        enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);

        assertFalse(attributes.containsKey("downloadUrl"));
        verify(s3StorageService, never()).getPresignedDownloadUrl(any(), any());
    }

    @Test
    void beforeBodyWrite_skipsAttachmentsWithNullStorageKey() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("storageKey", null);
        attributes.put("fileName", "file.jpg");

        Map<String, Object> resource = new HashMap<>();
        resource.put("type", "attachments");
        resource.put("id", "att-1");
        resource.put("attributes", attributes);

        Map<String, Object> body = new HashMap<>();
        body.put("data", resource);

        enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);

        assertNull(attributes.get("downloadUrl"));
        verify(s3StorageService, never()).getPresignedDownloadUrl(any(), any());
    }

    @Test
    void beforeBodyWrite_skipsAttachmentsWithBlankStorageKey() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("storageKey", "   ");
        attributes.put("fileName", "file.jpg");

        Map<String, Object> resource = new HashMap<>();
        resource.put("type", "attachments");
        resource.put("id", "att-1");
        resource.put("attributes", attributes);

        Map<String, Object> body = new HashMap<>();
        body.put("data", resource);

        enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);

        assertNull(attributes.get("downloadUrl"));
        verify(s3StorageService, never()).getPresignedDownloadUrl(any(), any());
    }

    @Test
    void beforeBodyWrite_enrichesIncludedAttachments() {
        Duration expiry = Duration.ofMinutes(15);
        when(s3StorageService.getDefaultExpiry()).thenReturn(expiry);
        when(s3StorageService.getPresignedDownloadUrl("key1", expiry)).thenReturn("url1");

        Map<String, Object> primaryResource = new HashMap<>();
        primaryResource.put("type", "products");
        primaryResource.put("id", "p-1");
        primaryResource.put("attributes", new HashMap<>());

        List<Map<String, Object>> included = new ArrayList<>();
        included.add(createAttachmentResource("att-1", "key1"));

        Map<String, Object> body = new HashMap<>();
        body.put("data", primaryResource);
        body.put("included", included);

        enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);

        assertEquals("url1", getAttributes(included.get(0)).get("downloadUrl"));
    }

    @Test
    void beforeBodyWrite_handlesNonMapBody() {
        String body = "plain text";
        Object result = enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);
        assertSame(body, result);
    }

    @Test
    void beforeBodyWrite_handlesPresignedUrlGenerationFailure() {
        Duration expiry = Duration.ofMinutes(15);
        when(s3StorageService.getDefaultExpiry()).thenReturn(expiry);
        when(s3StorageService.getPresignedDownloadUrl("bad-key", expiry))
                .thenThrow(new RuntimeException("S3 error"));

        Map<String, Object> body = new HashMap<>();
        body.put("data", createAttachmentResource("att-1", "bad-key"));

        // Should not throw — the error is caught and logged
        enricher.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                null, request, response);

        assertNull(getAttributes((Map<String, Object>) body.get("data")).get("downloadUrl"));
    }

    private Map<String, Object> createAttachmentResource(String id, String storageKey) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("storageKey", storageKey);
        attributes.put("fileName", "test.jpg");

        Map<String, Object> resource = new HashMap<>();
        resource.put("type", "attachments");
        resource.put("id", id);
        resource.put("attributes", attributes);
        return resource;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttributes(Map<String, Object> resource) {
        return (Map<String, Object>) resource.get("attributes");
    }
}
