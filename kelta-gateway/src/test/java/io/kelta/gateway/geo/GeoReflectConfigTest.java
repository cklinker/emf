package io.kelta.gateway.geo;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.Metadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GeoIP native-image reflection surface. {@code kelta-gateway} is compiled
 * to a GraalVM native image, and the MMDB reader instantiates every decoded type through
 * a constructor annotated {@link MaxMindDbConstructor}. A type missing from
 * {@code reflect-config.json} has no visible constructors at runtime, so the reader throws
 * {@code ConstructorNotFoundException} — which is exactly how geo enrichment silently died
 * in production on {@code com.maxmind.db.Metadata} (the reader decodes it while opening the
 * database, so the whole database fails to load, not just one lookup).
 *
 * <p>A JVM build hides this entirely: on the JVM every constructor is reflectively visible.
 */
@DisplayName("GeoIP native reflection registration")
class GeoReflectConfigTest {

    private static final String GEO_MODEL_PACKAGE = "io.kelta.gateway.geo.model";
    private static final String REFLECT_CONFIG =
            "META-INF/native-image/io.kelta/kelta-gateway/reflect-config.json";

    @Test
    @DisplayName("com.maxmind.db.Metadata is registered — the reader decodes it on open")
    void maxMindMetadataIsRegistered() throws Exception {
        // Sanity: Metadata really is decoded through an annotated constructor. If MaxMind
        // ever changes that, this test should be revisited rather than silently passing.
        assertThat(hasMaxMindConstructor(Metadata.class))
                .as("com.maxmind.db.Metadata no longer has a @MaxMindDbConstructor — "
                        + "re-check what the reader needs registered for reflection")
                .isTrue();

        assertThat(registeredClasses())
                .as("com.maxmind.db.Metadata absent from %s — the native gateway cannot open "
                        + "the MMDB file at all and geo enrichment stays off", REFLECT_CONFIG)
                .contains("com.maxmind.db.Metadata");
    }

    @Test
    @DisplayName("every MMDB-decoded geo model is in reflect-config.json")
    void everyGeoModelIsRegistered() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        Set<String> decodedModels = scanner.findCandidateComponents(GEO_MODEL_PACKAGE).stream()
                .map(bd -> bd.getBeanClassName())
                .filter(name -> name != null && !name.contains("$")) // top-level only
                .filter(GeoReflectConfigTest::isMaxMindDecoded)
                .collect(Collectors.toSet());

        // Sanity: the scanner actually found the known models.
        assertThat(decodedModels)
                .contains(GEO_MODEL_PACKAGE + ".GeoCityData", GEO_MODEL_PACKAGE + ".GeoCountry");

        Set<String> registered = registeredClasses();
        List<String> missing = decodedModels.stream()
                .filter(model -> !registered.contains(model))
                .sorted()
                .toList();

        assertThat(missing)
                .as("MMDB-decoded models absent from %s — the native gateway throws "
                        + "ConstructorNotFoundException on lookup; add an allDeclared* entry",
                        REFLECT_CONFIG)
                .isEmpty();
    }

    private static Set<String> registeredClasses() throws Exception {
        Set<String> registered = new HashSet<>();
        JsonNode config = new ObjectMapper()
                .readTree(new ClassPathResource(REFLECT_CONFIG).getInputStream());
        config.forEach(node -> registered.add(node.get("name").asText()));
        return registered;
    }

    private static boolean isMaxMindDecoded(String className) {
        try {
            return hasMaxMindConstructor(Class.forName(className));
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean hasMaxMindConstructor(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .anyMatch(GeoReflectConfigTest::isAnnotated);
    }

    private static boolean isAnnotated(Constructor<?> constructor) {
        return constructor.isAnnotationPresent(MaxMindDbConstructor.class);
    }
}
