package io.kelta.ai.filter;

import io.kelta.ai.service.AiRateLimitService;
import io.kelta.runtime.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pre-checks per-tenant request-rate quota for AI chat endpoints. Runs after
 * {@link TenantContextFilter} and before {@link TokenLimitFilter} so a flooding
 * tenant is rejected before the DB lookup for token-quota state.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitFilter.class);

    private final AiRateLimitService rateLimitService;

    public AiRateLimitFilter(AiRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/ai/chat");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        AiRateLimitService.Decision decision = rateLimitService.check(tenantId);
        if (!decision.allowed()) {
            log.info("AI request-rate quota exceeded for tenant {} (retry in {}s)",
                    tenantId, decision.retryAfterSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"errors":[{"status":"429","code":"AI_RATE_LIMIT_EXCEEDED","title":"Too Many Requests","detail":"AI request rate limit reached for this tenant; retry after the Retry-After interval"}]}
                """);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
