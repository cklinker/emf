package io.kelta.runtime.module.integration.api;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses an OpenAPI 3.x spec (JSON or YAML) into a {@link ParsedSpec} ready
 * for persistence. {@code $ref}s are resolved fully so handlers and the UI
 * never need to chase pointers across documents.
 *
 * <p>swagger-parser-v3 internally uses Jackson 2 — this class re-serializes
 * its output through the platform's Jackson 3 {@link ObjectMapper} so the
 * rest of the runtime can treat operation schemas as native {@code JsonNode}s.
 */
public class OpenApiSpecParser {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSpecParser.class);

    private final ObjectMapper objectMapper;

    public OpenApiSpecParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses {@code raw} (JSON or YAML; auto-detected) and returns the
     * normalized form. Throws {@link OpenApiParseException} when the spec is
     * unparseable or fails OpenAPI 3.x structural validation.
     */
    public ParsedSpec parse(String raw) {
        ParseOptions opts = new ParseOptions();
        opts.setResolve(true);
        opts.setResolveFully(true);
        opts.setResolveCombinators(true);
        opts.setValidateExternalRefs(false);

        SwaggerParseResult result = new OpenAPIParser().readContents(raw, null, opts);
        if (result.getOpenAPI() == null) {
            throw new OpenApiParseException(
                "Failed to parse OpenAPI spec: "
                    + (result.getMessages() == null ? "<no messages>" : result.getMessages()));
        }
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            log.debug("OpenAPI parser warnings: {}", result.getMessages());
        }
        OpenAPI openApi = result.getOpenAPI();

        String specVersion = openApi.getOpenapi() != null
            ? openApi.getOpenapi() : "3.0.0";
        String apiTitle = openApi.getInfo() != null ? openApi.getInfo().getTitle() : null;
        String apiVersion = openApi.getInfo() != null ? openApi.getInfo().getVersion() : null;
        String baseUrl = pickBaseUrl(openApi);

        JsonNode servers = serializeServers(openApi);
        JsonNode securitySchemes = openApi.getComponents() != null
            && openApi.getComponents().getSecuritySchemes() != null
            ? convert(openApi.getComponents().getSecuritySchemes())
            : null;
        JsonNode parsedSpec = convert(openApi);

        List<ParsedSpec.ParsedOperation> operations = extractOperations(openApi);

        return new ParsedSpec(
            specVersion, apiTitle, apiVersion, baseUrl,
            servers, securitySchemes, parsedSpec, operations);
    }

    /** Returns a hex-encoded sha-256 of {@code raw}. Used to detect re-imports. */
    public String hash(String raw) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // -----------------------------------------------------------------------

    private String pickBaseUrl(OpenAPI openApi) {
        List<Server> servers = openApi.getServers();
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        // Take the first server URL. Variables in the URL stay as-is — handlers
        // resolve them via the action config at execution time.
        Server first = servers.get(0);
        return first == null ? null : first.getUrl();
    }

    private JsonNode serializeServers(OpenAPI openApi) {
        if (openApi.getServers() == null || openApi.getServers().isEmpty()) {
            return null;
        }
        return convert(openApi.getServers());
    }

    private List<ParsedSpec.ParsedOperation> extractOperations(OpenAPI openApi) {
        List<ParsedSpec.ParsedOperation> ops = new ArrayList<>();
        if (openApi.getPaths() == null) {
            return ops;
        }
        for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
            String pathTemplate = entry.getKey();
            PathItem item = entry.getValue();
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry :
                    item.readOperationsMap().entrySet()) {
                ops.add(buildOperation(pathTemplate, opEntry.getKey().name(), opEntry.getValue()));
            }
        }
        return ops;
    }

    private ParsedSpec.ParsedOperation buildOperation(String pathTemplate,
                                                       String method,
                                                       Operation op) {
        String operationId = op.getOperationId();
        String syntheticOpId = operationId != null && !operationId.isBlank()
            ? operationId
            : synthesizeOpId(method, pathTemplate);

        JsonNode tags = op.getTags() != null ? convert(op.getTags()) : null;
        JsonNode parameters = op.getParameters() != null ? convert(op.getParameters()) : null;
        JsonNode requestBody = op.getRequestBody() != null ? convert(op.getRequestBody()) : null;
        JsonNode responses = op.getResponses() != null ? convert(op.getResponses()) : null;
        JsonNode security = op.getSecurity() != null ? convert(op.getSecurity()) : null;

        boolean deprecated = Boolean.TRUE.equals(op.getDeprecated());
        String searchText = buildSearchText(method, pathTemplate, op);

        return new ParsedSpec.ParsedOperation(
            operationId, syntheticOpId, method, pathTemplate,
            op.getSummary(), op.getDescription(),
            tags, parameters, requestBody, responses, security,
            deprecated, searchText);
    }

    private static String synthesizeOpId(String method, String path) {
        StringBuilder sb = new StringBuilder(method);
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else if (c == '/' || c == '{' || c == '}' || c == '-' || c == '_') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
            }
        }
        // trim trailing underscore
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String buildSearchText(String method, String path, Operation op) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(path);
        if (op.getOperationId() != null) sb.append(' ').append(op.getOperationId());
        if (op.getSummary() != null)     sb.append(' ').append(op.getSummary());
        if (op.getDescription() != null) sb.append(' ').append(op.getDescription());
        if (op.getTags() != null) {
            for (String tag : op.getTags()) {
                sb.append(' ').append(tag);
            }
        }
        return sb.toString();
    }

    /**
     * Converts a swagger-parser model object (Jackson-2 backed POJOs / Maps /
     * Lists) into a Jackson-3 {@link JsonNode}. Uses swagger-core's pre-configured
     * Jackson-2 mapper so OpenAPI-specific serializers run (clean spec-style
     * output, NON_NULL inclusion, polymorphic Schema handling); without this,
     * a bare {@code new ObjectMapper()} dumps every internal field of swagger's
     * POJOs (or fails outright on certain spec shapes).
     */
    @SuppressWarnings("unchecked")
    private JsonNode convert(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = io.swagger.v3.core.util.Json.mapper().writeValueAsString(value);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to serialize swagger object of type {}: {}",
                value.getClass().getName(), e.toString());
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("_unparsable", value.toString());
            return fallback;
        }
    }
}
