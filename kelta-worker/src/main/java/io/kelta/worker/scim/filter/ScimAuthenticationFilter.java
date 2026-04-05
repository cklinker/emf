package io.kelta.worker.scim.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.worker.scim.ScimConstants;
import io.kelta.worker.scim.model.ScimError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Authenticates SCIM requests using bearer tokens stored in the {@code scim_client} table.
 *
 * <p>Tokens are hashed with SHA-256 before lookup to avoid storing plaintext secrets.
 * On success, the filter sets {@code X-Tenant-ID} from the matched client row so that
 * downstream controllers can use tenant context as usual.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ScimAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ScimAuthenticationFilter.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ScimAuthenticationFilter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/scim/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            sendError(response, 401, "Bearer token required");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            sendError(response, 401, "Bearer token required");
            return;
        }

        String tokenHash = hashToken(token);
        List<Map<String, Object>> clients = jdbcTemplate.queryForList(
                "SELECT tenant_id, name, active FROM scim_client WHERE token_hash = ?",
                tokenHash);

        if (clients.isEmpty()) {
            securityLog.warn("SCIM auth failed: invalid token");
            sendError(response, 401, "Invalid bearer token");
            return;
        }

        Map<String, Object> client = clients.get(0);
        if (!Boolean.TRUE.equals(client.get("active"))) {
            securityLog.warn("SCIM auth failed: client '{}' is deactivated", client.get("name"));
            sendError(response, 401, "SCIM client is deactivated");
            return;
        }

        String tenantId = (String) client.get("tenant_id");

        // Update last_used_at
        jdbcTemplate.update("UPDATE scim_client SET last_used_at = NOW() WHERE token_hash = ?", tokenHash);

        // Wrap request to inject tenant header
        var wrappedRequest = new ScimTenantRequestWrapper(request, tenantId);
        filterChain.doFilter(wrappedRequest, response);
    }

    private void sendError(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(ScimConstants.CONTENT_TYPE_SCIM);
        objectMapper.writeValue(response.getOutputStream(), new ScimError(String.valueOf(status), detail));
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static class ScimTenantRequestWrapper extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final String tenantId;

        ScimTenantRequestWrapper(HttpServletRequest request, String tenantId) {
            super(request);
            this.tenantId = tenantId;
        }

        @Override
        public String getHeader(String name) {
            if ("X-Tenant-ID".equalsIgnoreCase(name)) {
                return tenantId;
            }
            return super.getHeader(name);
        }
    }
}
