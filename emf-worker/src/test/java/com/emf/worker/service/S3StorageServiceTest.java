package com.emf.worker.service;

import com.emf.worker.config.S3ConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private S3ConfigProperties config;
    private S3StorageService service;

    @BeforeEach
    void setUp() {
        config = new S3ConfigProperties();
        config.setEnabled(true);
        config.setEndpoint("https://s3.example.com");
        config.setPublicEndpoint("https://s3-public.example.com");
        config.setRegion("us-east-1");
        config.setBucket("test-bucket");
        config.setAccessKey("test-access-key");
        config.setSecretKey("test-secret-key");
        config.setPresignedUrlExpiryMinutes(15);

        // Create service with test config, then inject mock presigner
        service = new S3StorageService(config);
        service.setS3Presigner(s3Presigner);
    }

    @Test
    void getPresignedDownloadUrl_generatesUrlWithCorrectParameters() throws Exception {
        String storageKey = "tenant-1/col-1/rec-1/uuid/report.pdf";
        Duration expiry = Duration.ofMinutes(15);
        URL expectedUrl = URI.create("https://s3-public.example.com/test-bucket/tenant-1/col-1/rec-1/uuid/report.pdf?presigned=true").toURL();

        when(presignedGetObjectRequest.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);

        String result = service.getPresignedDownloadUrl(storageKey, expiry);

        assertNotNull(result);
        assertEquals(expectedUrl.toString(), result);

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());

        GetObjectPresignRequest capturedRequest = captor.getValue();
        assertEquals(expiry, capturedRequest.signatureDuration());
        assertEquals("test-bucket", capturedRequest.getObjectRequest().bucket());
        assertEquals(storageKey, capturedRequest.getObjectRequest().key());
    }

    @Test
    void getDefaultExpiry_returnsConfiguredDuration() {
        Duration expiry = service.getDefaultExpiry();
        assertEquals(Duration.ofMinutes(15), expiry);
    }

    @Test
    void getDefaultExpiry_reflectsConfigChange() {
        config.setPresignedUrlExpiryMinutes(30);
        assertEquals(Duration.ofMinutes(30), service.getDefaultExpiry());
    }
}
