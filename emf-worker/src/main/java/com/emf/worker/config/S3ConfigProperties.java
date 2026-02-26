package com.emf.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for S3-compatible object storage.
 *
 * <p>Supports separate internal and public endpoints, which is common for
 * S3-compatible storage running behind a reverse proxy (e.g., Garage, MinIO).
 * The internal endpoint is used by the server for operations, while the
 * public endpoint is used for generating browser-accessible presigned URLs.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "emf.storage.s3")
public class S3ConfigProperties {

    /** Whether S3 storage integration is enabled. */
    private boolean enabled = false;

    /** Internal S3 endpoint URL (used for server-to-server operations). */
    private String endpoint;

    /** Public S3 endpoint URL (used for browser-accessible presigned URLs). */
    private String publicEndpoint;

    /** S3 region (default: "garage" for Garage S3-compatible storage). */
    private String region = "garage";

    /** S3 bucket name. */
    private String bucket = "emf-attachments";

    /** S3 access key. */
    private String accessKey;

    /** S3 secret key. */
    private String secretKey;

    /** Maximum file upload size in bytes (default: 50 MB). */
    private long maxFileSize = 52_428_800L;

    /** Presigned URL expiry in minutes (default: 15). */
    private int presignedUrlExpiryMinutes = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getPresignedUrlExpiryMinutes() {
        return presignedUrlExpiryMinutes;
    }

    public void setPresignedUrlExpiryMinutes(int presignedUrlExpiryMinutes) {
        this.presignedUrlExpiryMinutes = presignedUrlExpiryMinutes;
    }
}
