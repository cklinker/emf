package io.kelta.worker.module;

import io.kelta.worker.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModuleJarServiceTest {

    @TempDir
    Path tempDir;

    private S3StorageService s3StorageService;
    private ModuleJarService jarService;

    @BeforeEach
    void setUp() {
        s3StorageService = mock(S3StorageService.class);
        jarService = new ModuleJarService(s3StorageService, tempDir);
    }

    @Test
    void shouldUploadJarToS3() {
        byte[] jarBytes = "fake-jar-content".getBytes();

        String s3Key = jarService.uploadJar("tenant-1", "test-module", "1.0.0", jarBytes);

        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("modules/tenant-1/test-module/1.0.0/"));
        assertTrue(s3Key.endsWith(".jar"));
        verify(s3StorageService).uploadObject(eq(s3Key), eq(jarBytes), eq("application/java-archive"));
    }

    @Test
    void shouldDownloadJarToCache() throws IOException {
        String s3Key = "modules/tenant-1/test-module/1.0.0/abc123.jar";
        byte[] content = "jar-content".getBytes();

        S3StorageService.StorageObject storageObject = new S3StorageService.StorageObject(
            new ByteArrayInputStream(content),
            "application/java-archive",
            content.length,
            "abc123.jar"
        );
        when(s3StorageService.streamObject(s3Key)).thenReturn(storageObject);

        URL url = jarService.downloadJarToCache(s3Key);

        assertNotNull(url);
        assertTrue(url.toString().endsWith(".jar"));
        // Verify local file was created
        Path localPath = Path.of(url.getPath());
        assertTrue(Files.exists(localPath));
        assertArrayEquals(content, Files.readAllBytes(localPath));
    }

    @Test
    void shouldReturnCachedJarOnSecondDownload() throws IOException {
        String s3Key = "modules/tenant-1/test-module/1.0.0/abc123.jar";
        byte[] content = "jar-content".getBytes();

        S3StorageService.StorageObject storageObject = new S3StorageService.StorageObject(
            new ByteArrayInputStream(content),
            "application/java-archive",
            content.length,
            "abc123.jar"
        );
        when(s3StorageService.streamObject(s3Key)).thenReturn(storageObject);

        URL url1 = jarService.downloadJarToCache(s3Key);
        URL url2 = jarService.downloadJarToCache(s3Key);

        assertEquals(url1, url2);
        // Should only download once
        verify(s3StorageService, times(1)).streamObject(s3Key);
    }

    @Test
    void shouldEvictFromCache() throws IOException {
        String s3Key = "modules/tenant-1/test-module/1.0.0/abc123.jar";
        byte[] content = "jar-content".getBytes();

        S3StorageService.StorageObject storageObject = new S3StorageService.StorageObject(
            new ByteArrayInputStream(content),
            "application/java-archive",
            content.length,
            "abc123.jar"
        );
        when(s3StorageService.streamObject(s3Key)).thenReturn(storageObject);

        URL url = jarService.downloadJarToCache(s3Key);
        Path localPath = Path.of(url.getPath());
        assertTrue(Files.exists(localPath));

        jarService.evictFromCache(s3Key);

        assertFalse(Files.exists(localPath));
    }

    @Test
    void shouldDeleteJarFromS3AndCache() throws IOException {
        String s3Key = "modules/tenant-1/test-module/1.0.0/abc123.jar";
        byte[] content = "jar-content".getBytes();

        S3StorageService.StorageObject storageObject = new S3StorageService.StorageObject(
            new ByteArrayInputStream(content),
            "application/java-archive",
            content.length,
            "abc123.jar"
        );
        when(s3StorageService.streamObject(s3Key)).thenReturn(storageObject);

        jarService.downloadJarToCache(s3Key);
        jarService.deleteJar(s3Key);

        verify(s3StorageService).deleteObject(s3Key);
    }

    @Test
    void shouldComputeConsistentSha256() {
        byte[] data = "hello world".getBytes();
        String hash1 = ModuleJarService.sha256(data);
        String hash2 = ModuleJarService.sha256(data);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 is 32 bytes = 64 hex chars
    }

    @Test
    void shouldBuildCorrectS3Key() {
        String key = ModuleJarService.buildS3Key("t1", "mod", "1.0.0", "abc");
        assertEquals("modules/t1/mod/1.0.0/abc.jar", key);
    }
}
