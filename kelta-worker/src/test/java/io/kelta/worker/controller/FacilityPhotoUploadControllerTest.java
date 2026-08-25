package io.kelta.worker.controller;

import io.kelta.worker.service.SpotopenedMediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacilityPhotoUploadControllerTest {

    @Mock
    private SpotopenedMediaStorageService storageService;

    private FacilityPhotoUploadController controller;

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        controller = new FacilityPhotoUploadController(storageService);
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", "campsite.jpg");
        body.put("contentType", "image/jpeg");
        body.put("fileSize", 1024L);
        return body;
    }

    @Test
    void requestUploadUrl_disabledStorage_returns503() {
        when(storageService.isEnabled()).thenReturn(false);

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, validBody());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void requestUploadUrl_missingFields_returns400() {
        when(storageService.isEnabled()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, Map.of("fileName", "x.jpg"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void requestUploadUrl_nonImageContentType_returns400() {
        when(storageService.isEnabled()).thenReturn(true);
        Map<String, Object> body = validBody();
        body.put("contentType", "application/pdf");

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void requestUploadUrl_blockedContentType_returns400() {
        when(storageService.isEnabled()).thenReturn(true);
        Map<String, Object> body = validBody();
        body.put("contentType", "application/x-executable");

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void requestUploadUrl_fileTooLarge_returns400() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getMaxFileSize()).thenReturn(1000L);
        Map<String, Object> body = validBody();
        body.put("fileSize", 2000L);

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void requestUploadUrl_zeroFileSize_returns400() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getMaxFileSize()).thenReturn(1000L);
        Map<String, Object> body = validBody();
        body.put("fileSize", 0L);

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void requestUploadUrl_success_returnsUploadUrlAndStorageKey() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getMaxFileSize()).thenReturn(15_728_640L);
        when(storageService.getPresignedUploadUrl(anyString(), eq("image/jpeg")))
                .thenReturn("https://s3.rzware.com/spotopened-media/signed-url");
        when(storageService.getDefaultExpiry()).thenReturn(Duration.ofMinutes(15));

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, validBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> attributes = extractAttributes(response.getBody());
        assertEquals("https://s3.rzware.com/spotopened-media/signed-url", attributes.get("uploadUrl"));
        assertTrue(((String) attributes.get("storageKey")).startsWith(TENANT_ID + "/facility-photos/"));
        assertTrue(((String) attributes.get("storageKey")).endsWith("campsite.jpg"));
        assertEquals("PUT", attributes.get("method"));
    }

    @Test
    void requestUploadUrl_sanitizesFileNameSlashesSoNoExtraKeySegmentsAppear() {
        // Same sanitizer as AttachmentUploadController: strips everything but
        // [a-zA-Z0-9._-]. Dots survive (S3 keys aren't filesystem paths -- ".."
        // has no special meaning to a flat object key), but '/' must not, or a
        // crafted fileName could inject extra path-like segments into the key.
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getMaxFileSize()).thenReturn(15_728_640L);
        when(storageService.getPresignedUploadUrl(anyString(), anyString()))
                .thenReturn("https://s3.rzware.com/spotopened-media/signed-url");
        when(storageService.getDefaultExpiry()).thenReturn(Duration.ofMinutes(15));

        Map<String, Object> body = validBody();
        body.put("fileName", "../../etc/passwd.jpg");

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, body);

        Map<String, Object> attributes = extractAttributes(response.getBody());
        String storageKey = (String) attributes.get("storageKey");
        String[] segments = storageKey.split("/");
        assertEquals(4, segments.length, storageKey); // tenant / "facility-photos" / uuid / filename
        assertFalse(segments[3].contains("/"));
    }

    @Test
    void requestUploadUrl_storageError_returns500() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getMaxFileSize()).thenReturn(15_728_640L);
        when(storageService.getPresignedUploadUrl(anyString(), anyString()))
                .thenThrow(new RuntimeException("presigner failure"));

        ResponseEntity<Map<String, Object>> response =
                controller.requestUploadUrl(TENANT_ID, USER_EMAIL, validBody());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAttributes(Map<String, Object> responseBody) {
        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        return (Map<String, Object>) data.get("attributes");
    }
}
