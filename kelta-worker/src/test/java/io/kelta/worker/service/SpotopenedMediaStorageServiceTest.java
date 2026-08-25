package io.kelta.worker.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SpotopenedMediaStorageServiceTest {

    @Test
    void disabledByDefault_reportsNotEnabledAndThrowsOnPresign() {
        SpotopenedMediaStorageService service = new SpotopenedMediaStorageService(
                false, "", "", "garage", "spotopened-media", "", "", 15_728_640L, 15);

        assertFalse(service.isEnabled());
        assertThrows(IllegalStateException.class,
                () -> service.getPresignedUploadUrl("key", "image/jpeg"));
    }

    @Test
    void enabled_presignsLocallyWithNoNetworkCall() {
        // Presigning is a local HMAC computation -- no network access needed, so a
        // well-formed fake endpoint/credentials is enough to exercise this for real.
        SpotopenedMediaStorageService service = new SpotopenedMediaStorageService(
                true, "http://garage.garage.svc.cluster.local:3900", "https://s3.rzware.com",
                "garage", "spotopened-media", "test-access-key", "test-secret-key",
                15_728_640L, 15);

        assertTrue(service.isEnabled());
        assertEquals(15_728_640L, service.getMaxFileSize());
        assertEquals(Duration.ofMinutes(15), service.getDefaultExpiry());

        String url = service.getPresignedUploadUrl("tenant-1/facility-photos/abc/photo.jpg", "image/jpeg");

        assertTrue(url.startsWith("https://s3.rzware.com/"), url);
        assertTrue(url.contains("spotopened-media"), url);
        assertTrue(url.contains("tenant-1%2Ffacility-photos%2Fabc%2Fphoto.jpg")
                || url.contains("tenant-1/facility-photos/abc/photo.jpg"), url);
    }

    @Test
    void enabled_usesInternalEndpointWhenPublicEndpointIsBlank() {
        SpotopenedMediaStorageService service = new SpotopenedMediaStorageService(
                true, "http://garage.garage.svc.cluster.local:3900", "",
                "garage", "spotopened-media", "test-access-key", "test-secret-key",
                15_728_640L, 15);

        String url = service.getPresignedUploadUrl("k", "image/png");
        assertTrue(url.startsWith("http://garage.garage.svc.cluster.local:3900/"), url);
    }
}
