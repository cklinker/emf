package com.emf.controlplane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for S3-compatible object storage.
 *
 * <p>Properties are bound from the {@code emf.storage.s3} prefix.
 * When {@code enabled} is false (the default), the S3StorageService
 * bean will not be created.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "emf.storage.s3")
public class S3ConfigProperties {

    private boolean enabled = false;
    private String endpoint = "";
    private String region = "garage";
    private String bucket = "emf-attachments";
    private String accessKey = "";
    private String secretKey = "";
    private long maxFileSize = 52428800L; // 50MB

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
}
