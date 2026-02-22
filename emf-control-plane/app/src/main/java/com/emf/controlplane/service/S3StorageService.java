package com.emf.controlplane.service;

import com.emf.controlplane.config.S3ConfigProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Service for interacting with S3-compatible object storage (e.g., Garage).
 *
 * <p>Only activated when {@code emf.storage.s3.enabled=true}. Provides
 * upload, download (presigned URL), and delete operations.
 *
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(name = "emf.storage.s3.enabled", havingValue = "true")
@EnableConfigurationProperties(S3ConfigProperties.class)
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3ConfigProperties config;
    private S3Client s3Client;
    private S3Presigner s3Presigner;

    public S3StorageService(S3ConfigProperties config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
        var builder = S3Client.builder()
                .credentialsProvider(credentials)
                .region(Region.of(config.getRegion()));
        var presignerBuilder = S3Presigner.builder()
                .credentialsProvider(credentials)
                .region(Region.of(config.getRegion()));

        // S3Client uses the (internal) endpoint for uploads/deletes
        if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            URI endpoint = URI.create(config.getEndpoint());
            builder.endpointOverride(endpoint).forcePathStyle(true);
        }

        // S3Presigner uses the public endpoint for browser-accessible download URLs,
        // falling back to the internal endpoint if not configured
        String presignerEndpoint = config.getPublicEndpoint() != null && !config.getPublicEndpoint().isBlank()
                ? config.getPublicEndpoint()
                : config.getEndpoint();
        if (presignerEndpoint != null && !presignerEndpoint.isBlank()) {
            presignerBuilder.endpointOverride(URI.create(presignerEndpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }

        this.s3Client = builder.build();
        this.s3Presigner = presignerBuilder.build();
        log.info("S3 storage initialized: bucket={}, endpoint={}, publicEndpoint={}",
                config.getBucket(), config.getEndpoint(), config.getPublicEndpoint());
    }

    @PreDestroy
    public void shutdown() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
    }

    /**
     * Upload a file to S3.
     *
     * @param key         the S3 object key
     * @param data        the file input stream
     * @param size        the file size in bytes
     * @param contentType the MIME content type
     */
    public void upload(String key, InputStream data, long size, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(config.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();
        s3Client.putObject(putRequest, RequestBody.fromInputStream(data, size));
        log.info("Uploaded file to S3: key={}, size={}", key, size);
    }

    /**
     * Generate a presigned download URL for an S3 object.
     *
     * @param key    the S3 object key
     * @param expiry the duration the URL is valid
     * @return the presigned URL string
     */
    public String getPresignedDownloadUrl(String key, Duration expiry) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(r -> r.bucket(config.getBucket()).key(key))
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Delete a file from S3.
     *
     * @param key the S3 object key
     */
    public void delete(String key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(config.getBucket())
                .key(key)
                .build();
        s3Client.deleteObject(deleteRequest);
        log.info("Deleted file from S3: key={}", key);
    }

    /**
     * Get the configured maximum file size in bytes.
     *
     * @return maximum file size
     */
    public long getMaxFileSize() {
        return config.getMaxFileSize();
    }

    // Package-private setters for testing
    void setS3Client(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    void setS3Presigner(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }
}
