package io.kelta.jsonapi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the JSON:API {@code links} block for a paginated collection response.
 *
 * <p>The block always carries {@code self}, {@code prev}, and {@code next}
 * keys. Per the JSON:API spec, a link whose target page does not exist
 * (e.g. {@code prev} on page 1, {@code next} on the last page) is emitted
 * with a {@code null} value rather than being omitted, so clients can branch
 * on key presence alone.
 *
 * <p>Generated URLs are relative paths anchored on the current request URI.
 * Relative URLs are valid in the JSON:API spec and keep the response
 * host-independent so cached system-collection responses remain reusable
 * across hosts and behind load balancers.
 *
 * @since 1.0.0
 */
public final class PaginationLinks {

    private PaginationLinks() {}

    /**
     * Builds the {@code self} / {@code prev} / {@code next} link map.
     *
     * @param basePath    the request path without query string (e.g. {@code "/api/customers"})
     * @param params      the original query parameters as received from the client (nullable)
     * @param currentPage the current page number (1-indexed)
     * @param pageSize    the current page size
     * @param totalPages  the total number of pages in the result set
     * @return a map with keys {@code self}, {@code prev}, {@code next};
     *         unavailable links have a {@code null} value
     */
    public static Map<String, Object> build(
            String basePath,
            Map<String, String> params,
            int currentPage,
            int pageSize,
            int totalPages) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", buildUrl(basePath, params, currentPage, pageSize));
        links.put("prev", currentPage > 1
                ? buildUrl(basePath, params, currentPage - 1, pageSize)
                : null);
        links.put("next", currentPage < totalPages
                ? buildUrl(basePath, params, currentPage + 1, pageSize)
                : null);
        return links;
    }

    private static String buildUrl(String basePath, Map<String, String> params,
                                     int pageNumber, int pageSize) {
        StringBuilder url = new StringBuilder(basePath != null ? basePath : "");
        boolean first = true;
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String key = e.getKey();
                if (key == null || "page[number]".equals(key) || "page[size]".equals(key)) {
                    continue;
                }
                url.append(first ? '?' : '&');
                url.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                url.append('=');
                String value = e.getValue();
                if (value != null) {
                    url.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
                first = false;
            }
        }
        url.append(first ? '?' : '&');
        url.append("page[number]=").append(pageNumber);
        url.append("&page[size]=").append(pageSize);
        return url.toString();
    }
}
