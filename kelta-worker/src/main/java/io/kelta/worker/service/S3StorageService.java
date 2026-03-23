package io.kelta.worker.service;

import io.kelta.worker.config.S3ConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Service for S3 file operations: presigned URL generation and direct streaming.
 *
 * <p>Uses a separate public endpoint for presigned URLs (browser access) and
 * an internal endpoint for server-side streaming (FileController).
 *
 * <p>This service is only activated when {@code kelta.storage.s3.enabled=true}.
 *
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(name = "kelta.storage.s3.enabled", havingValue = "true")
public class S3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);

    private final S3ConfigProperties config;
    private S3Presigner s3Presigner;
    private S3Client s3Client;

    public S3StorageService(S3ConfigProperties config) {
        this.config = config;
        this.s3Presigner = buildPresigner(config);
        this.s3Client = buildS3Client(config);
        logger.info("S3StorageService initialized with bucket '{}', publicEndpoint '{}'",
                config.getBucket(), config.getPublicEndpoint());
    }

    /**
     * Holds a streamed S3 object with metadata. Must be closed after use.
     */
    public record StorageObject(
            InputStream content,
            String contentType,
            long contentLength,
            String fileName
    ) implements Closeable {
        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
            }
        }
    }

    /**
     * Generates a presigned download URL for the given S3 object key.
     *
     * @param storageKey the S3 object key (e.g., "tenant-1/col-1/rec-1/uuid/file.pdf")
     * @param expiry the URL expiry duration
     * @return the presigned download URL
     */
    public String getPresignedDownloadUrl(String storageKey, Duration expiry) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(storageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Returns the configured presigned URL expiry duration.
     *
     * @return the expiry duration
     */
    public Duration getDefaultExpiry() {
        return Duration.ofMinutes(config.getPresignedUrlExpiryMinutes());
    }

    /**
     * Streams file content from S3 for direct serving.
     *
     * @param storageKey the S3 object key
     * @return a StorageObject containing the stream, content type, size, and filename
     * @throws NoSuchKeyException if the key does not exist
     */
    public StorageObject streamObject(String storageKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(storageKey)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        GetObjectResponse metadata = response.response();

        String contentType = metadata.contentType() != null
                ? metadata.contentType()
                : "application/octet-stream";
        long contentLength = metadata.contentLength() != null
                ? metadata.contentLength()
                : -1;
        String fileName = extractFileName(storageKey);

        return new StorageObject(response, contentType, contentLength, fileName);
    }

    /**
     * Streams a byte range from an S3 object for partial content (206) responses.
     *
     * @param storageKey the S3 object key
     * @param rangeHeader the HTTP Range header value (e.g., "bytes=0-1023")
     * @return a StorageObject containing the partial stream
     */
    public StorageObject streamObjectRange(String storageKey, String rangeHeader) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(storageKey)
                .range(rangeHeader)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        GetObjectResponse metadata = response.response();

        String contentType = metadata.contentType() != null
                ? metadata.contentType()
                : "application/octet-stream";
        long contentLength = metadata.contentLength() != null
                ? metadata.contentLength()
                : -1;

        return new StorageObject(response, contentType, contentLength, extractFileName(storageKey));
    }

    /**
     * Checks if an S3 object exists without downloading it.
     */
    public boolean objectExists(String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(storageKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String extractFileName(String storageKey) {
        int lastSlash = storageKey.lastIndexOf('/');
        return lastSlash >= 0 ? storageKey.substring(lastSlash + 1) : storageKey;
    }

    @PreDestroy
    public void close() {
        if (s3Presigner != null) {
            s3Presigner.close();
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    private static S3Presigner buildPresigner(S3ConfigProperties config) {
        String endpoint = config.getPublicEndpoint() != null
                ? config.getPublicEndpoint()
                : config.getEndpoint();

        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static S3Client buildS3Client(S3ConfigProperties config) {
        // Use internal endpoint for server-to-server operations
        return S3Client.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    // Package-private setters for testing
    void setS3Presigner(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    void setS3Client(S3Client s3Client) {
        this.s3Client = s3Client;
    }
}
