package io.kelta.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the native-image reflection gap that broke telehealth event
 * delivery: {@code kelta-worker} is compiled to a GraalVM native image, so a
 * {@code *Payload} class Jackson serializes must be listed in
 * {@code reflect-config.json} — otherwise it serializes to {@code {}} at runtime
 * (a JVM build hides this). Every event payload the worker publishes must be
 * registered; this test fails CI when a new payload is added without its entry.
 */
@DisplayName("event payload native reflection registration")
class EventPayloadReflectConfigTest {

    private static final String EVENT_PACKAGE = "io.kelta.runtime.event";
    private static final String REFLECT_CONFIG =
            "META-INF/native-image/io.kelta/kelta-worker/reflect-config.json";

    @Test
    @DisplayName("every io.kelta.runtime.event.*Payload is in reflect-config.json")
    void everyPayloadIsRegistered() throws Exception {
        // Discover all top-level *Payload classes in the shared event module.
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Payload")));
        Set<String> payloads = scanner.findCandidateComponents(EVENT_PACKAGE).stream()
                .map(bd -> bd.getBeanClassName())
                .filter(name -> name != null && !name.contains("$")) // top-level only
                .collect(Collectors.toSet());

        // Sanity: the scanner actually found the known payloads.
        assertThat(payloads)
                .contains(EVENT_PACKAGE + ".VideoSessionPayload",
                        EVENT_PACKAGE + ".ChatMessagePayload",
                        EVENT_PACKAGE + ".RecordChangedPayload");

        // Collect the class names registered for reflection.
        Set<String> registered = new HashSet<>();
        JsonNode config = new ObjectMapper()
                .readTree(new ClassPathResource(REFLECT_CONFIG).getInputStream());
        config.forEach(node -> registered.add(node.get("name").asText()));

        List<String> missing = payloads.stream()
                .filter(p -> !registered.contains(p))
                .sorted()
                .toList();

        assertThat(missing)
                .as("event payloads absent from %s — the native worker will serialize "
                        + "these to {} at runtime; add an allDeclared* entry", REFLECT_CONFIG)
                .isEmpty();
    }
}
