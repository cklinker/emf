package io.kelta.worker.service;

import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.runtime.storage.CredentialProvider;
import io.kelta.worker.repository.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes a metadata package to a <b>remote cluster's</b> package import API —
 * the promotion executor for environments that live on a different Kelta
 * installation (multi-cluster topologies where the source database does not
 * know the target's topology).
 *
 * <p>Security posture: the target base URL is admin-supplied
 * ({@code MANAGE_SANDBOXES}-gated), so this client stays deliberately inert —
 * scheme allow-list (http/https, no userinfo), redirects disabled, bounded
 * timeouts and response size, and the response is parsed strictly as an
 * import-report JSON document, never interpreted. The PAT comes from the
 * credential vault ({@link CredentialProvider}); it is never stored on the
 * environment row or accepted from a request body.
 */
@Service
public class RemotePromotionClient {

    private static final Logger log = LoggerFactory.getLogger(RemotePromotionClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_RESPONSE_CHARS = 2_000_000;
    private static final List<String> TOKEN_SECRET_KEYS =
            List.of("token", "accessToken", "apiKey", "value", "password");

    private final EnvironmentRepository environmentRepository;
    private final CredentialProvider credentialProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RemotePromotionClient(EnvironmentRepository environmentRepository,
                                 CredentialProvider credentialProvider,
                                 ObjectMapper objectMapper) {
        this.environmentRepository = environmentRepository;
        this.credentialProvider = credentialProvider;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Validates an admin-supplied remote base URL. Throws on anything unsafe. */
    public static void validateRemoteBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("remoteBaseUrl is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("remoteBaseUrl must use http or https");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("remoteBaseUrl must not embed credentials");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("remoteBaseUrl must include a host");
        }
    }

    /** Verifies connectivity + credentials by listing the remote package history. */
    public Map<String, Object> testConnection(String envId, String tenantId) {
        Map<String, Object> env = requireRemoteEnvironment(envId, tenantId);
        String url = remoteApiUrl(env, "/api/packages/history");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveToken(env))
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> { /* no-throw; inspected below */ })
                    .toEntity(String.class);
            boolean ok = response.getStatusCode().is2xxSuccessful();
            result.put("ok", ok);
            result.put("status", response.getStatusCode().value());
            if (!ok) {
                result.put("error", "Remote responded with HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", "Connection failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Pushes the package to the remote import endpoint and returns the remote
     * import report (legacy shape: success/created/updated/skipped/failed/items).
     *
     * @throws IllegalStateException when the remote rejects the import or the
     *         response is not a valid import report
     */
    public Map<String, Object> pushPackage(Map<String, Object> env, Map<String, Object> pkg,
                                           String conflictMode, boolean dryRun) {
        String url = remoteApiUrl(env, "/api/packages/import")
                + "?conflictMode=" + (conflictMode == null ? "skip" : conflictMode.toLowerCase())
                + "&dryRun=" + dryRun;

        byte[] packageBytes;
        try {
            packageBytes = objectMapper.writeValueAsBytes(pkg);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize package for remote push", e);
        }

        ByteArrayResource fileResource = new ByteArrayResource(packageBytes) {
            @Override
            public String getFilename() {
                return "promotion-package.json";
            }
        };
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", fileResource);

        var response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveToken(env))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { /* no-throw; inspected below */ })
                .toEntity(String.class);

        String body = response.getBody() == null ? "" : response.getBody();
        if (body.length() > MAX_RESPONSE_CHARS) {
            throw new IllegalStateException("Remote import response exceeds the size limit");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Remote import failed with HTTP "
                    + response.getStatusCode().value() + ": " + truncate(body, 500));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> report = objectMapper.readValue(body, Map.class);
            if (!report.containsKey("created") && !report.containsKey("success")) {
                throw new IllegalStateException("Remote response is not an import report");
            }
            return report;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Remote import response is not valid JSON: "
                    + truncate(body, 200), e);
        }
    }

    Map<String, Object> requireRemoteEnvironment(String envId, String tenantId) {
        Map<String, Object> env = environmentRepository.findByIdAndTenant(envId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));
        if (env.get("remote_base_url") == null) {
            throw new IllegalArgumentException("Environment is not a remote target: " + envId);
        }
        return env;
    }

    private String remoteApiUrl(Map<String, Object> env, String path) {
        String base = String.valueOf(env.get("remote_base_url"));
        validateRemoteBaseUrl(base);
        String slug = String.valueOf(env.get("remote_tenant_slug"));
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + slug + path;
    }

    private String resolveToken(Map<String, Object> env) {
        String credentialRef = (String) env.get("credential_ref");
        if (credentialRef == null || credentialRef.isBlank()) {
            throw new IllegalArgumentException("Remote environment has no credentialRef configured");
        }
        ResolvedCredential credential = credentialProvider.resolve(credentialRef)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Credential not found in vault: " + credentialRef));
        for (String key : TOKEN_SECRET_KEYS) {
            Object value = credential.secret(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        throw new IllegalArgumentException(
                "Credential '" + credentialRef + "' has no usable token secret (type=" + credential.type() + ")");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
