package io.kelta.worker.service;

import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldDefinitionBuilder;
import io.kelta.runtime.model.FieldType;
import io.kelta.worker.service.FieldMaskingService.MaskType;
import io.kelta.worker.service.FieldMaskingService.MaskingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FieldMaskingService")
class FieldMaskingServiceTest {

    private FieldMaskingService service;

    @BeforeEach
    void setUp() {
        service = new FieldMaskingService();
    }

    private static FieldDefinition field(FieldType type, Map<String, Object> config) {
        return new FieldDefinitionBuilder()
                .name("ssn")
                .type(type)
                .fieldTypeConfig(config != null ? Map.of(FieldMaskingService.CONFIG_KEY, config) : null)
                .build();
    }

    private static MaskingConfig config(MaskType type) {
        return new MaskingConfig(type, '*', null);
    }

    @Nested
    @DisplayName("configFor")
    class ConfigFor {

        @Test
        @DisplayName("Should return empty for a non-maskable type even when masking config is present")
        void nonMaskableTypeWithConfigIsEmpty() {
            FieldDefinition intField = field(FieldType.INTEGER, Map.of("type", "FULL"));

            assertThat(service.configFor(intField)).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for a maskable type without masking config")
        void maskableTypeWithoutConfigIsEmpty() {
            FieldDefinition plain = field(FieldType.STRING, null);

            assertThat(service.configFor(plain)).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for an empty masking config map")
        void emptyConfigMapIsEmpty() {
            FieldDefinition plain = field(FieldType.STRING, Map.of());

            assertThat(service.configFor(plain)).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for a null field")
        void nullFieldIsEmpty() {
            assertThat(service.configFor(null)).isEmpty();
        }

        @Test
        @DisplayName("Should parse config for every maskable type")
        void parsesConfigForEveryMaskableType() {
            for (FieldType type : FieldMaskingService.MASKABLE_TYPES) {
                Optional<MaskingConfig> cfg = service.configFor(field(type, Map.of("type", "LAST4")));
                assertThat(cfg)
                        .as("type %s should be maskable", type)
                        .hasValue(new MaskingConfig(MaskType.LAST4, '*', null));
            }
        }

        @Test
        @DisplayName("Should degrade an invalid type string to FULL (fail-closed)")
        void invalidTypeStringDegradesToFull() {
            Optional<MaskingConfig> cfg = service.configFor(
                    field(FieldType.STRING, Map.of("type", "REDACT_EVERYTHING")));

            assertThat(cfg).hasValue(new MaskingConfig(MaskType.FULL, '*', null));
        }

        @Test
        @DisplayName("Should degrade CUSTOM without a pattern to FULL")
        void customWithoutPatternDegradesToFull() {
            Optional<MaskingConfig> cfg = service.configFor(
                    field(FieldType.STRING, Map.of("type", "CUSTOM")));

            assertThat(cfg).isPresent();
            assertThat(cfg.get().type()).isEqualTo(MaskType.FULL);
        }

        @Test
        @DisplayName("Should honor a maskChar override")
        void honorsMaskCharOverride() {
            Optional<MaskingConfig> cfg = service.configFor(
                    field(FieldType.STRING, Map.of("type", "FULL", "maskChar", "#")));

            assertThat(cfg).hasValue(new MaskingConfig(MaskType.FULL, '#', null));
        }

        @Test
        @DisplayName("Should accept a lower-case type string")
        void acceptsLowerCaseType() {
            Optional<MaskingConfig> cfg = service.configFor(
                    field(FieldType.EMAIL, Map.of("type", "email")));

            assertThat(cfg).isPresent();
            assertThat(cfg.get().type()).isEqualTo(MaskType.EMAIL);
        }

        @Test
        @DisplayName("Should keep the custom pattern for CUSTOM configs")
        void keepsCustomPattern() {
            Optional<MaskingConfig> cfg = service.configFor(field(FieldType.STRING,
                    Map.of("type", "CUSTOM", "customPattern", "***-**-####")));

            assertThat(cfg).hasValue(new MaskingConfig(MaskType.CUSTOM, '*', "***-**-####"));
        }
    }

    @Nested
    @DisplayName("mask — common edges")
    class CommonEdges {

        @Test
        @DisplayName("Null input returns null for every mask type")
        void nullStaysNull() {
            for (MaskType type : MaskType.values()) {
                MaskingConfig cfg = new MaskingConfig(type, '*', "####");
                assertThat(service.mask(null, cfg)).as("type %s", type).isNull();
            }
        }

        @Test
        @DisplayName("Blank input returns the fixed full mask")
        void blankMasksFully() {
            assertThat(service.mask("   ", config(MaskType.LAST4))).isEqualTo("******");
        }

        @Test
        @DisplayName("Blank input uses the configured mask character")
        void blankUsesMaskChar() {
            assertThat(service.mask(" ", new MaskingConfig(MaskType.FULL, 'x', null)))
                    .isEqualTo("xxxxxx");
        }
    }

    @Nested
    @DisplayName("mask — FULL")
    class FullMask {

        @Test
        @DisplayName("Should always emit six mask characters regardless of value length")
        void fixedLengthMask() {
            assertThat(service.mask("a", config(MaskType.FULL))).isEqualTo("******");
            assertThat(service.mask("a very long secret value", config(MaskType.FULL)))
                    .isEqualTo("******");
        }

        @Test
        @DisplayName("Should use the configured mask character")
        void usesMaskChar() {
            assertThat(service.mask("secret", new MaskingConfig(MaskType.FULL, '#', null)))
                    .isEqualTo("######");
        }
    }

    @Nested
    @DisplayName("mask — LAST4")
    class Last4Mask {

        @Test
        @DisplayName("Should keep the last four alphanumerics and preserve separators")
        void keepsLastFourAndSeparators() {
            assertThat(service.mask("123-45-6789", config(MaskType.LAST4)))
                    .isEqualTo("***-**-6789");
        }

        @Test
        @DisplayName("Should fully mask values with four or fewer alphanumerics")
        void fullyMasksShortValues() {
            assertThat(service.mask("1234", config(MaskType.LAST4))).isEqualTo("******");
            assertThat(service.mask("12-34", config(MaskType.LAST4))).isEqualTo("******");
            assertThat(service.mask("a", config(MaskType.LAST4))).isEqualTo("******");
        }

        @Test
        @DisplayName("Should mask exactly one character for a five-alphanumeric value")
        void masksOneOfFive() {
            assertThat(service.mask("12345", config(MaskType.LAST4))).isEqualTo("*2345");
        }

        @Test
        @DisplayName("Should treat unicode letters as alphanumerics")
        void handlesUnicodeLetters() {
            assertThat(service.mask("áéíóú1", config(MaskType.LAST4))).isEqualTo("**íóú1");
        }
    }

    @Nested
    @DisplayName("mask — EMAIL")
    class EmailMask {

        @Test
        @DisplayName("Should keep the first character and the domain")
        void keepsFirstCharAndDomain() {
            assertThat(service.mask("craig@rzware.com", config(MaskType.EMAIL)))
                    .isEqualTo("c***@rzware.com");
        }

        @Test
        @DisplayName("Should keep a one-character local part")
        void oneCharLocalPart() {
            assertThat(service.mask("a@b.c", config(MaskType.EMAIL))).isEqualTo("a***@b.c");
        }

        @Test
        @DisplayName("Should fully mask a value without an @")
        void noAtSignMasksFully() {
            assertThat(service.mask("not-an-email", config(MaskType.EMAIL))).isEqualTo("******");
        }

        @Test
        @DisplayName("Should fully mask when @ is the first or last character")
        void degenerateAtPositionsMaskFully() {
            assertThat(service.mask("@domain.com", config(MaskType.EMAIL))).isEqualTo("******");
            assertThat(service.mask("user@", config(MaskType.EMAIL))).isEqualTo("******");
        }
    }

    @Nested
    @DisplayName("mask — CUSTOM")
    class CustomMask {

        @Test
        @DisplayName("Should pass characters through at '#' positions right-aligned and emit literals elsewhere")
        void appliesPatternRightAligned() {
            MaskingConfig cfg = new MaskingConfig(MaskType.CUSTOM, '*', "***-**-####");

            assertThat(service.mask("123456789", cfg)).isEqualTo("***-**-6789");
        }

        @Test
        @DisplayName("Should fill missing '#' positions with the mask char when the value is shorter than the pattern")
        void valueShorterThanPattern() {
            MaskingConfig cfg = new MaskingConfig(MaskType.CUSTOM, '*', "######");

            assertThat(service.mask("12", cfg)).isEqualTo("****12");
        }

        @Test
        @DisplayName("Should use the configured mask char for missing positions")
        void missingPositionsUseMaskChar() {
            MaskingConfig cfg = new MaskingConfig(MaskType.CUSTOM, 'x', "##-##");

            assertThat(service.mask("9", cfg)).isEqualTo("xx-x9");
        }

        @Test
        @DisplayName("Should emit only literals when the pattern has no '#'")
        void allLiteralPattern() {
            MaskingConfig cfg = new MaskingConfig(MaskType.CUSTOM, '*', "REDACTED");

            assertThat(service.mask("anything", cfg)).isEqualTo("REDACTED");
        }

        @Test
        @DisplayName("Output length always equals the pattern length — no length leak")
        void outputLengthMatchesPattern() {
            MaskingConfig cfg = new MaskingConfig(MaskType.CUSTOM, '*', "***-####");

            assertThat(service.mask("x", cfg)).hasSize(8);
            assertThat(service.mask("a much longer value than the pattern", cfg)).hasSize(8);
        }
    }
}
