package io.kelta.worker.module;

import io.kelta.worker.service.S3StorageService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages module JAR storage in S3 and local caching for ClassLoader loading.
 * <p>
 * JAR files are stored in S3 under the key pattern:
 * {@code modules/{tenantId}/{moduleId}/{version}/{checksum}.jar}
 * <p>
 * When a module is loaded, the JAR is downloaded to a local temp directory
 * so it can be referenced by a {@link SandboxedModuleClassLoader}.
 *
 * @since 1.0.0
 */
public class ModuleJarService {

    private static final Logger log = LoggerFactory.getLogger(ModuleJarService.class);

    private static final String JAR_CONTENT_TYPE = "application/java-archive";

    private final S3StorageService s3StorageService;
    private final Path localCacheDir;

    /** Tracks locally cached JARs: s3Key -> local path */
    private final Map<String, Path> cachedJars = new ConcurrentHashMap<>();

    public ModuleJarService(S3StorageService s3StorageService) {
        this(s3StorageService, createDefaultCacheDir());
    }

    ModuleJarService(S3StorageService s3StorageService, Path localCacheDir) {
        this.s3StorageService = Objects.requireNonNull(s3StorageService);
        this.localCacheDir = Objects.requireNonNull(localCacheDir);
        log.info("ModuleJarService initialized with cache dir: {}", localCacheDir);
    }

    /**
     * Uploads a module JAR to S3.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier
     * @param version  the module version
     * @param jarBytes the JAR file bytes
     * @return the S3 key where the JAR was stored
     */
    public String uploadJar(String tenantId, String moduleId, String version, byte[] jarBytes) {
        String checksum = sha256(jarBytes);
        String s3Key = buildS3Key(tenantId, moduleId, version, checksum);

        s3StorageService.uploadObject(s3Key, jarBytes, JAR_CONTENT_TYPE);
        log.info("Uploaded module JAR to S3: {} ({} bytes, checksum={})", s3Key, jarBytes.length, checksum);

        return s3Key;
    }

    /**
     * Downloads a module JAR from S3 to local cache and returns a URL suitable
     * for {@link SandboxedModuleClassLoader}.
     *
     * @param s3Key the S3 key of the JAR
     * @return the local file URL for the cached JAR
     * @throws IOException if download or cache write fails
     */
    public URL downloadJarToCache(String s3Key) throws IOException {
        Path cached = cachedJars.get(s3Key);
        if (cached != null && Files.exists(cached)) {
            log.debug("Module JAR already cached locally: {}", cached);
            return cached.toUri().toURL();
        }

        Path localPath = localCacheDir.resolve(s3Key.replace("/", "_"));
        try (S3StorageService.StorageObject obj = s3StorageService.streamObject(s3Key);
             InputStream is = obj.content()) {
            Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
        }

        cachedJars.put(s3Key, localPath);
        log.info("Downloaded module JAR to local cache: {} -> {}", s3Key, localPath);
        return localPath.toUri().toURL();
    }

    /**
     * Removes a locally cached JAR file.
     *
     * @param s3Key the S3 key of the JAR
     */
    public void evictFromCache(String s3Key) {
        Path cached = cachedJars.remove(s3Key);
        if (cached != null) {
            try {
                Files.deleteIfExists(cached);
                log.debug("Evicted cached module JAR: {}", cached);
            } catch (IOException e) {
                log.warn("Failed to delete cached JAR {}: {}", cached, e.getMessage());
            }
        }
    }

    /**
     * Deletes a module JAR from S3 and evicts the local cache.
     *
     * @param s3Key the S3 key of the JAR
     */
    public void deleteJar(String s3Key) {
        s3StorageService.deleteObject(s3Key);
        evictFromCache(s3Key);
        log.info("Deleted module JAR from S3: {}", s3Key);
    }

    /**
     * Computes the SHA-256 checksum of the given bytes.
     *
     * @param data the byte array
     * @return the hex-encoded SHA-256 checksum
     */
    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    static String buildS3Key(String tenantId, String moduleId, String version, String checksum) {
        return String.format("modules/%s/%s/%s/%s.jar", tenantId, moduleId, version, checksum);
    }

    /**
     * Deletes the temporary cache directory on application shutdown.
     * Only runs when the cache dir was auto-created (i.e., it lives under the system temp dir).
     * Skipped silently if deletion fails — the OS will clean up on reboot.
     */
    @PreDestroy
    void cleanupCacheDir() {
        try {
            if (localCacheDir.startsWith(Path.of(System.getProperty("java.io.tmpdir")))) {
                try (Stream<Path> walk = Files.walk(localCacheDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {}
                            });
                }
                log.info("Cleaned up module cache dir: {}", localCacheDir);
            }
        } catch (Exception e) {
            log.warn("Failed to clean up module cache dir {}: {}", localCacheDir, e.getMessage());
        }
    }

    private static Path createDefaultCacheDir() {
        try {
            return Files.createTempDirectory("kelta-module-cache");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create module cache directory", e);
        }
    }
}
