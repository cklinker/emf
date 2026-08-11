package io.kelta.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Percent-encodes literal {@code [} / {@code ]} in the query string so Spring Cloud
 * Gateway forwards the rest of the query untouched.
 *
 * <p>Spring Cloud Gateway's {@link RouteToRequestUrlFilter} (order
 * {@link RouteToRequestUrlFilter#ROUTE_TO_URL_FILTER_ORDER 10000}) rebuilds the backend URI
 * and asks {@link ServerWebExchangeUtils#containsEncodedParts(URI)} whether the incoming URI
 * is already encoded. That check runs {@code UriComponentsBuilder.fromUri(uri).build(true)},
 * which rejects a raw {@code [} or {@code ]} — RFC 3986 reserves those gen-delims for IPv6
 * literals in the authority. So a query carrying an unencoded bracket is classified as
 * <em>not</em> encoded and the whole query gets re-encoded on the way out, turning every
 * legitimate {@code %XX} escape into {@code %25XX}.
 *
 * <p>JSON:API puts brackets in nearly every query Kelta serves ({@code page[size]},
 * {@code fields[type]}, {@code filter[field][op]}), so any request that also carries a
 * correctly-encoded value silently reached the worker double-encoded:
 *
 * <pre>
 * GET /api/things?sort=a%2Cb                 → worker sees "a,b"    ✓
 * GET /api/things?sort=a%2Cb&amp;page[size]=1    → worker sees "a%2Cb"  ✗
 * </pre>
 *
 * <p>That corrupts any encoded value, not just comma-separated {@code sort}/{@code fields}
 * lists — a filter value containing a space, {@code &amp;}, {@code +}, {@code #} or {@code /}
 * arrives with its escapes mangled and silently fails to match.
 *
 * <p>Rewriting the brackets to {@code %5B} / {@code %5D} makes the URI strictly encoded, so
 * Spring Cloud Gateway passes the query through verbatim. The worker's servlet container
 * decodes {@code %5B} back to {@code [} before parameter binding, so {@code page[size]} still
 * binds exactly as before.
 *
 * <p>Runs at order 9000 — after the tenant/auth/authz chain (which reads decoded query
 * params and is unaffected either way) and before {@code RouteToRequestUrlFilter}.
 */
@Component
public class QueryBracketEncodingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(QueryBracketEncodingFilter.class);

    static final int ORDER = 9000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI uri = exchange.getRequest().getURI();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || (rawQuery.indexOf('[') < 0 && rawQuery.indexOf(']') < 0)) {
            return chain.filter(exchange);
        }

        String encodedQuery = rawQuery.replace("[", "%5B").replace("]", "%5D");
        URI newUri;
        try {
            newUri = UriComponentsBuilder.fromUri(uri)
                    .replaceQuery(encodedQuery)
                    .build(true)
                    .toUri();
        } catch (IllegalArgumentException e) {
            // Some other part of the URI is not strictly encoded. Leave the request alone
            // rather than fail it — Spring Cloud Gateway's own re-encoding still applies.
            log.debug("Could not normalize bracket encoding for query '{}', forwarding as-is", rawQuery, e);
            return chain.filter(exchange);
        }

        ServerHttpRequest normalized = exchange.getRequest().mutate().uri(newUri).build();
        return chain.filter(exchange.mutate().request(normalized).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
