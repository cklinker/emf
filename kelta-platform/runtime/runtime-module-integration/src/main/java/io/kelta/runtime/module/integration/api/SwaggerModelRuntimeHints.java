package io.kelta.runtime.module.integration.api;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

import java.util.List;

/**
 * Registers swagger-models classes for reflection in GraalVM native images.
 *
 * <p>{@link OpenApiSpecParser#convert(Object)} serializes swagger-parser POJOs
 * via Jackson; without these hints, Jackson's bean introspector throws in a
 * native image because the swagger-models classes aren't registered as
 * reflective types — every operation field then falls through to the
 * {@code _unparsable} branch and stores Java {@code toString()} dumps
 * (PathParameter, QueryParameter, RequestBody, ApiResponses, ...) instead of
 * proper OpenAPI JSON.
 *
 * <p>Wired via {@code aot.factories} so Spring's AOT processor picks it up
 * during native compilation.
 */
public class SwaggerModelRuntimeHints implements RuntimeHintsRegistrar {

    private static final List<String> SWAGGER_MODEL_CLASSES = List.of(
        "io.swagger.v3.oas.models.Components",
        "io.swagger.v3.oas.models.ExternalDocumentation",
        "io.swagger.v3.oas.models.OpenAPI",
        "io.swagger.v3.oas.models.Operation",
        "io.swagger.v3.oas.models.PathItem",
        "io.swagger.v3.oas.models.PathItem$HttpMethod",
        "io.swagger.v3.oas.models.Paths",
        "io.swagger.v3.oas.models.SpecVersion",
        "io.swagger.v3.oas.models.callbacks.Callback",
        "io.swagger.v3.oas.models.examples.Example",
        "io.swagger.v3.oas.models.headers.Header",
        "io.swagger.v3.oas.models.headers.Header$StyleEnum",
        "io.swagger.v3.oas.models.info.Contact",
        "io.swagger.v3.oas.models.info.Info",
        "io.swagger.v3.oas.models.info.License",
        "io.swagger.v3.oas.models.links.Link",
        "io.swagger.v3.oas.models.links.LinkParameter",
        "io.swagger.v3.oas.models.media.ArraySchema",
        "io.swagger.v3.oas.models.media.BinarySchema",
        "io.swagger.v3.oas.models.media.BooleanSchema",
        "io.swagger.v3.oas.models.media.ByteArraySchema",
        "io.swagger.v3.oas.models.media.ComposedSchema",
        "io.swagger.v3.oas.models.media.Content",
        "io.swagger.v3.oas.models.media.DateSchema",
        "io.swagger.v3.oas.models.media.DateTimeSchema",
        "io.swagger.v3.oas.models.media.Discriminator",
        "io.swagger.v3.oas.models.media.EmailSchema",
        "io.swagger.v3.oas.models.media.Encoding",
        "io.swagger.v3.oas.models.media.Encoding$StyleEnum",
        "io.swagger.v3.oas.models.media.EncodingProperty",
        "io.swagger.v3.oas.models.media.EncodingProperty$StyleEnum",
        "io.swagger.v3.oas.models.media.FileSchema",
        "io.swagger.v3.oas.models.media.IntegerSchema",
        "io.swagger.v3.oas.models.media.JsonSchema",
        "io.swagger.v3.oas.models.media.MapSchema",
        "io.swagger.v3.oas.models.media.MediaType",
        "io.swagger.v3.oas.models.media.NumberSchema",
        "io.swagger.v3.oas.models.media.ObjectSchema",
        "io.swagger.v3.oas.models.media.PasswordSchema",
        "io.swagger.v3.oas.models.media.Schema",
        "io.swagger.v3.oas.models.media.StringSchema",
        "io.swagger.v3.oas.models.media.UUIDSchema",
        "io.swagger.v3.oas.models.media.XML",
        "io.swagger.v3.oas.models.parameters.CookieParameter",
        "io.swagger.v3.oas.models.parameters.HeaderParameter",
        "io.swagger.v3.oas.models.parameters.Parameter",
        "io.swagger.v3.oas.models.parameters.Parameter$StyleEnum",
        "io.swagger.v3.oas.models.parameters.PathParameter",
        "io.swagger.v3.oas.models.parameters.QueryParameter",
        "io.swagger.v3.oas.models.parameters.RequestBody",
        "io.swagger.v3.oas.models.responses.ApiResponse",
        "io.swagger.v3.oas.models.responses.ApiResponses",
        "io.swagger.v3.oas.models.security.OAuthFlow",
        "io.swagger.v3.oas.models.security.OAuthFlows",
        "io.swagger.v3.oas.models.security.Scopes",
        "io.swagger.v3.oas.models.security.SecurityRequirement",
        "io.swagger.v3.oas.models.security.SecurityScheme",
        "io.swagger.v3.oas.models.security.SecurityScheme$In",
        "io.swagger.v3.oas.models.security.SecurityScheme$Type",
        "io.swagger.v3.oas.models.servers.Server",
        "io.swagger.v3.oas.models.servers.ServerVariable",
        "io.swagger.v3.oas.models.servers.ServerVariables",
        "io.swagger.v3.oas.models.tags.Tag"
    );

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        MemberCategory[] all = MemberCategory.values();
        for (String name : SWAGGER_MODEL_CLASSES) {
            hints.reflection().registerType(TypeReference.of(name), all);
        }
    }
}
