package io.kelta.gateway.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * The single trust-aware client-IP resolver for the gateway.
 *
 * <p>When {@code kelta.gateway.ip-allowlist.trust-forwarded-for} is true (the existing
 * trust knob — same trust question, one switch), the leftmost {@code X-Forwarded-For}
 * hop is taken as the original client; otherwise only the socket remote address is
 * trusted. Note the leftmost hop is client-spoofable in topologies where the edge proxy
 * appends rather than replaces — the same caveat already documented on the allowlist
 * filter.
 *
 * <p>{@code TenantIpAllowlistFilter} deliberately does NOT use this resolver — its
 * any-hop CIDR matching is a documented trade-off for proxy-topology resilience.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(
            @Value("${kelta.gateway.ip-allowlist.trust-forwarded-for:true}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    /** Resolves the client IP for the request, or null when none is determinable. */
    public String resolve(ServerWebExchange exchange) {
        if (trustForwardedFor) {
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String first = normalizeIp(xff.split(",")[0]);
                if (first != null) {
                    return first;
                }
            }
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return normalizeIp(remote.getAddress().getHostAddress());
        }
        return null;
    }

    /**
     * Normalizes a raw IP token: trims, strips an IPv6 scope suffix ({@code %eth0}) and
     * surrounding brackets ({@code [::1]}). Returns null for blanks.
     */
    public static String normalizeIp(String raw) {
        if (raw == null) {
            return null;
        }
        String ip = raw.trim();
        if (ip.isEmpty()) {
            return null;
        }
        if (ip.startsWith("[")) {
            int close = ip.indexOf(']');
            if (close > 0) {
                ip = ip.substring(1, close);
            }
        }
        int pct = ip.indexOf('%');
        if (pct >= 0) {
            ip = ip.substring(0, pct);
        }
        return ip.isEmpty() ? null : ip;
    }
}
