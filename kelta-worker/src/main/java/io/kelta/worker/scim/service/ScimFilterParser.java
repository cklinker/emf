package io.kelta.worker.scim.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SCIM filter expressions (RFC 7644 §3.4.2.2) into SQL WHERE clauses.
 *
 * <p>Supports a subset of the SCIM filter syntax:
 * <ul>
 *   <li>{@code eq} — equals</li>
 *   <li>{@code co} — contains</li>
 *   <li>{@code sw} — starts with</li>
 *   <li>{@code pr} — present (not null)</li>
 *   <li>{@code and} / {@code or} logical operators</li>
 * </ul>
 */
public class ScimFilterParser {

    private static final Pattern ATTR_OP_VALUE =
            Pattern.compile("(\\w+(?:\\.\\w+)?)\\s+(eq|ne|co|sw|ew|gt|ge|lt|le)\\s+\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTR_PR =
            Pattern.compile("(\\w+(?:\\.\\w+)?)\\s+pr", Pattern.CASE_INSENSITIVE);

    private final Map<String, String> attributeMap;

    public ScimFilterParser(Map<String, String> attributeMap) {
        this.attributeMap = attributeMap;
    }

    public ParsedFilter parse(String filter) {
        if (filter == null || filter.isBlank()) {
            return new ParsedFilter("1=1", List.of());
        }

        List<Object> params = new ArrayList<>();
        String sql = parseExpression(filter.trim(), params);
        return new ParsedFilter(sql, params);
    }

    private String parseExpression(String filter, List<Object> params) {
        // Split on " and " / " or " (case-insensitive) but not inside quotes
        String[] orParts = splitOutsideQuotes(filter, " or ");
        if (orParts.length > 1) {
            List<String> clauses = new ArrayList<>();
            for (String part : orParts) {
                clauses.add("(" + parseExpression(part.trim(), params) + ")");
            }
            return String.join(" OR ", clauses);
        }

        String[] andParts = splitOutsideQuotes(filter, " and ");
        if (andParts.length > 1) {
            List<String> clauses = new ArrayList<>();
            for (String part : andParts) {
                clauses.add("(" + parseExpression(part.trim(), params) + ")");
            }
            return String.join(" AND ", clauses);
        }

        // Try "attr pr" pattern
        Matcher prMatcher = ATTR_PR.matcher(filter);
        if (prMatcher.matches()) {
            String attr = prMatcher.group(1);
            String col = mapAttribute(attr);
            if (col == null) {
                return "1=0";
            }
            return col + " IS NOT NULL";
        }

        // Try "attr op value" pattern
        Matcher matcher = ATTR_OP_VALUE.matcher(filter);
        if (matcher.matches()) {
            String attr = matcher.group(1);
            String op = matcher.group(2).toLowerCase();
            String value = matcher.group(3);

            String col = mapAttribute(attr);
            if (col == null) {
                return "1=0";
            }

            return switch (op) {
                case "eq" -> {
                    params.add(value);
                    yield col + " = ?";
                }
                case "ne" -> {
                    params.add(value);
                    yield col + " != ?";
                }
                case "co" -> {
                    params.add("%" + escapeLike(value) + "%");
                    yield col + " ILIKE ?";
                }
                case "sw" -> {
                    params.add(escapeLike(value) + "%");
                    yield col + " ILIKE ?";
                }
                case "ew" -> {
                    params.add("%" + escapeLike(value));
                    yield col + " ILIKE ?";
                }
                default -> "1=0";
            };
        }

        return "1=0";
    }

    private String mapAttribute(String scimAttr) {
        String col = attributeMap.get(scimAttr.toLowerCase());
        if (col != null) {
            return col;
        }
        // Try dotted path (e.g., "name.familyName")
        return attributeMap.get(scimAttr.toLowerCase().replace(".", "_"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String[] splitOutsideQuotes(String input, String delimiter) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        String lowerInput = input.toLowerCase();
        String lowerDelim = delimiter.toLowerCase();

        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '"') {
                depth = depth == 0 ? 1 : 0;
            }
            if (depth == 0 && i + lowerDelim.length() <= input.length()
                    && lowerInput.substring(i, i + lowerDelim.length()).equals(lowerDelim)) {
                parts.add(input.substring(start, i));
                start = i + delimiter.length();
                i += delimiter.length() - 1;
            }
        }
        parts.add(input.substring(start));
        return parts.toArray(new String[0]);
    }

    public record ParsedFilter(String sql, List<Object> params) {}
}
