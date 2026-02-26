package com.emf.worker.service;

import com.emf.worker.config.S3ConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;

/**
 * Service for generating S3 presigned download URLs.
 *
 * <p>Uses a separate public endpoint for presigned URLs so that browser
 * clients can access the URLs directly, even when the S3 service runs
 * behind a reverse proxy or on an internal network.
 *
 * <p>This service is only activated when {@code emf.storage.s3.enabled=true}.
 *
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(name = "emf.storage.s3.enabled", havingValue = "true")
public class S3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);

    private final S3ConfigProperties config;
    private S3Presigner s3Presigner;

    public S3StorageService(S3ConfigProperties config) {
        this.config = config;
        this.s3Presigner = buildPresigner(config);
        logger.info("S3StorageService initialized with bucket '{}', publicEndpoint '{}'",
                config.getBucket(), config.getPublicEndpoint());
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

    @PreDestroy
    public void close() {
        if (s3Presigner != null) {
            s3Presigner.close();
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

    // Package-private setter for testing
    void setS3Presigner(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }
}
