package com.emf.controlplane.service;

import com.emf.controlplane.config.S3ConfigProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link S3StorageService}.
 */
@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3StorageService storageService;

    private static final String BUCKET = "test-bucket";
    private static final String REGION = "garage";
    private static final String ENDPOINT = "https://s3.rzware.com";

    @BeforeEach
    void setUp() {
        S3ConfigProperties config = new S3ConfigProperties();
        config.setEnabled(true);
        config.setBucket(BUCKET);
        config.setRegion(REGION);
        config.setEndpoint(ENDPOINT);
        config.setAccessKey("test-access-key");
        config.setSecretKey("test-secret-key");
        config.setMaxFileSize(52428800L);

        storageService = new S3StorageService(config);
        storageService.setS3Client(s3Client);
        storageService.setS3Presigner(s3Presigner);
    }

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("should upload file to S3 with correct parameters")
        void shouldUploadFile() {
            // Given
            String key = "tenant-1/col-1/rec-1/uuid/report.pdf";
            byte[] content = "file content".getBytes();
            InputStream data = new ByteArrayInputStream(content);
            String contentType = "application/pdf";

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            // When
            storageService.upload(key, data, content.length, contentType);

            // Then
            ArgumentCaptor<PutObjectRequest> requestCaptor =
                    ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

            PutObjectRequest request = requestCaptor.getValue();
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo(key);
            assertThat(request.contentType()).isEqualTo(contentType);
            assertThat(request.contentLength()).isEqualTo(content.length);
        }
    }

    @Nested
    @DisplayName("getPresignedDownloadUrl")
    class GetPresignedDownloadUrl {

        @Test
        @DisplayName("should return presigned URL for a given key")
        void shouldReturnPresignedUrl() throws Exception {
            // Given
            String key = "tenant-1/col-1/rec-1/uuid/report.pdf";
            Duration expiry = Duration.ofMinutes(15);

            PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
            when(presignedRequest.url()).thenReturn(new URL("https://s3.rzware.com/test-bucket/tenant-1/col-1/rec-1/uuid/report.pdf?signed=xyz"));
            when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                    .thenReturn(presignedRequest);

            // When
            String url = storageService.getPresignedDownloadUrl(key, expiry);

            // Then
            assertThat(url).startsWith("https://s3.rzware.com/test-bucket/");
            assertThat(url).contains("report.pdf");

            ArgumentCaptor<GetObjectPresignRequest> captor =
                    ArgumentCaptor.forClass(GetObjectPresignRequest.class);
            verify(s3Presigner).presignGetObject(captor.capture());
            assertThat(captor.getValue().signatureDuration()).isEqualTo(expiry);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete object from S3")
        void shouldDeleteObject() {
            // Given
            String key = "tenant-1/col-1/rec-1/uuid/report.pdf";
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            // When
            storageService.delete(key);

            // Then
            ArgumentCaptor<DeleteObjectRequest> captor =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());

            DeleteObjectRequest request = captor.getValue();
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo(key);
        }
    }

    @Nested
    @DisplayName("getMaxFileSize")
    class GetMaxFileSize {

        @Test
        @DisplayName("should return configured max file size")
        void shouldReturnMaxFileSize() {
            assertThat(storageService.getMaxFileSize()).isEqualTo(52428800L);
        }
    }
}
