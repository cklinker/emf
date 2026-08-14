package io.kelta.worker.service;

import com.sun.net.httpserver.HttpServer;
import io.kelta.worker.config.S3ConfigProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the S3 request shape against an S3-COMPATIBLE store. This deployment runs Garage, not AWS.
 *
 * <p>AWS SDK 2.30 changed {@code requestChecksumCalculation} to default {@code WHEN_SUPPORTED},
 * which sends the literal header {@code x-amz-content-sha256: STREAMING-UNSIGNED-PAYLOAD-TRAILER}
 * plus a trailing checksum instead of a hex digest in the header. Garage parses that value as a
 * hash and rejects the upload with
 * {@code "Invalid content sha256 hash: Invalid character 'S' at position 0"} — the {@code S} being
 * the start of {@code STREAMING}.
 *
 * <p>Asserted against a real {@code S3Client} talking to a loopback HTTP server, so this exercises
 * the SDK's actual wire behaviour rather than a mock's idea of it. A plain assertion on the builder
 * would not have caught the regression, because the default lives in the SDK and changed under us.
 */
@DisplayName("S3StorageService request checksums")
class S3StorageServiceChecksumTest {

    private HttpServer server;
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    lastHeaders.put(name.toLowerCase(), values.get(0));
                }
            });
            try (InputStream body = exchange.getRequestBody()) {
                body.readAllBytes();
            }
            exchange.getResponseHeaders().add("ETag", "\"d41d8cd98f00b204e9800998ecf8427e\"");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private S3StorageService service() {
        S3ConfigProperties config = new S3ConfigProperties();
        config.setEnabled(true);
        config.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        config.setPublicEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        config.setRegion("garage");
        config.setBucket("test-bucket");
        config.setAccessKey("test-access-key");
        config.setSecretKey("test-secret-key");
        return new S3StorageService(config);
    }

    @Test
    @DisplayName("sends a hex digest, never the STREAMING trailer sentinel Garage rejects")
    void uploadSendsHexContentSha256() {
        service().uploadObject("modules/test.jar", "jar bytes".getBytes(StandardCharsets.UTF_8),
                "application/java-archive");

        String contentSha256 = lastHeaders.get("x-amz-content-sha256");
        assertThat(contentSha256)
                .as("Garage parses this header as a hash; the SDK default sends "
                        + "STREAMING-UNSIGNED-PAYLOAD-TRAILER, which fails with "
                        + "\"Invalid character 'S' at position 0\"")
                .isNotNull()
                .doesNotStartWith("STREAMING")
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("does not add a flexible-checksum trailer the store cannot parse")
    void uploadDoesNotSendChecksumTrailer() {
        service().uploadObject("modules/test.jar", "jar bytes".getBytes(StandardCharsets.UTF_8),
                "application/java-archive");

        // WHEN_SUPPORTED advertises the trailing checksum via these headers and switches the body
        // to aws-chunked framing. WHEN_REQUIRED leaves both off for a plain PutObject.
        assertThat(lastHeaders).doesNotContainKey("x-amz-trailer");
        // Absent entirely is the expected outcome, so this must tolerate null rather than
        // assert on it — chunked framing is what would set it.
        assertThat(lastHeaders.getOrDefault("content-encoding", "")).doesNotContain("aws-chunked");
    }

    @Test
    @DisplayName("the body is the object itself, not aws-chunked framing around it")
    void uploadSendsRawBodyLength() {
        byte[] data = "jar bytes".getBytes(StandardCharsets.UTF_8);
        service().uploadObject("modules/test.jar", data, "application/java-archive");

        // Chunked framing inflates content-length past the object size; a store that stored it
        // verbatim would hand back a corrupt JAR whose checksum no longer matches.
        assertThat(lastHeaders.get("content-length")).isEqualTo(String.valueOf(data.length));
    }
}
