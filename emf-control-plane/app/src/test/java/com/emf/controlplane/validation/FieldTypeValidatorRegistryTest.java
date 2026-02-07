package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldTypeValidatorRegistryTest {

    private FieldTypeValidatorRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = new FieldTypeValidatorRegistry(List.of(
            new PicklistFieldValidator(),
            new MultiPicklistFieldValidator(),
            new AutoNumberFieldValidator(),
            new CurrencyFieldValidator(),
            new FormulaFieldValidator(),
            new RollupSummaryFieldValidator(),
            new EmailFieldValidator(),
            new PhoneFieldValidator(),
            new UrlFieldValidator(),
            new EncryptedFieldValidator(),
            new GeolocationFieldValidator(),
            new PercentFieldValidator(),
            new ExternalIdFieldValidator()
        ));
    }

    @Test
    void findsRegisteredValidator() {
        assertTrue(registry.getValidator("PICKLIST").isPresent());
        assertTrue(registry.getValidator("FORMULA").isPresent());
        assertTrue(registry.getValidator("EMAIL").isPresent());
    }

    @Test
    void returnsEmptyForUnregisteredType() {
        assertTrue(registry.getValidator("STRING").isEmpty());
        assertTrue(registry.getValidator("UNKNOWN").isEmpty());
    }

    @Nested
    class AutoNumberValidation {
        @Test
        void validConfig() throws Exception {
            JsonNode config = mapper.readTree("{\"prefix\":\"TICKET-\",\"padding\":4}");
            assertDoesNotThrow(() ->
                registry.getValidator("AUTO_NUMBER").get().validateConfig(config));
        }

        @Test
        void missingPrefixThrows() {
            assertThrows(ValidationException.class, () ->
                registry.getValidator("AUTO_NUMBER").get().validateConfig(null));
        }

        @Test
        void invalidPaddingThrows() throws Exception {
            JsonNode config = mapper.readTree("{\"prefix\":\"X\",\"padding\":0}");
            assertThrows(ValidationException.class, () ->
                registry.getValidator("AUTO_NUMBER").get().validateConfig(config));
        }
    }

    @Nested
    class FormulaValidation {
        @Test
        void validConfig() throws Exception {
            JsonNode config = mapper.readTree("{\"expression\":\"A + B\",\"returnType\":\"DOUBLE\"}");
            assertDoesNotThrow(() ->
                registry.getValidator("FORMULA").get().validateConfig(config));
        }

        @Test
        void missingExpressionThrows() throws Exception {
            JsonNode config = mapper.readTree("{\"returnType\":\"DOUBLE\"}");
            assertThrows(ValidationException.class, () ->
                registry.getValidator("FORMULA").get().validateConfig(config));
        }

        @Test
        void invalidReturnTypeThrows() throws Exception {
            JsonNode config = mapper.readTree("{\"expression\":\"A + B\",\"returnType\":\"INVALID\"}");
            assertThrows(ValidationException.class, () ->
                registry.getValidator("FORMULA").get().validateConfig(config));
        }

        @Test
        void rejectsValueOnFormulaField() {
            assertThrows(ValidationException.class, () ->
                registry.getValidator("FORMULA").get().validateValue("some value", null));
        }
    }

    @Nested
    class EmailValidation {
        @Test
        void validEmail() {
            assertDoesNotThrow(() ->
                registry.getValidator("EMAIL").get().validateValue("test@example.com", null));
        }

        @Test
        void invalidEmailThrows() {
            assertThrows(ValidationException.class, () ->
                registry.getValidator("EMAIL").get().validateValue("not-an-email", null));
        }

        @Test
        void nullEmailAllowed() {
            assertDoesNotThrow(() ->
                registry.getValidator("EMAIL").get().validateValue(null, null));
        }
    }

    @Nested
    class CurrencyValidation {
        @Test
        void validConfig() throws Exception {
            JsonNode config = mapper.readTree("{\"precision\":2,\"defaultCurrencyCode\":\"USD\"}");
            assertDoesNotThrow(() ->
                registry.getValidator("CURRENCY").get().validateConfig(config));
        }

        @Test
        void invalidPrecisionThrows() throws Exception {
            JsonNode config = mapper.readTree("{\"precision\":10}");
            assertThrows(ValidationException.class, () ->
                registry.getValidator("CURRENCY").get().validateConfig(config));
        }

        @Test
        void invalidCurrencyCodeThrows() throws Exception {
            JsonNode config = mapper.readTree("{\"defaultCurrencyCode\":\"US\"}");
            assertThrows(ValidationException.class, () ->
                registry.getValidator("CURRENCY").get().validateConfig(config));
        }
    }

    @Nested
    class RollupSummaryValidation {
        @Test
        void validSumConfig() throws Exception {
            JsonNode config = mapper.readTree(
                "{\"childCollection\":\"items\",\"aggregateFunction\":\"SUM\",\"aggregateField\":\"amount\"}");
            assertDoesNotThrow(() ->
                registry.getValidator("ROLLUP_SUMMARY").get().validateConfig(config));
        }

        @Test
        void validCountConfig() throws Exception {
            JsonNode config = mapper.readTree(
                "{\"childCollection\":\"items\",\"aggregateFunction\":\"COUNT\"}");
            assertDoesNotThrow(() ->
                registry.getValidator("ROLLUP_SUMMARY").get().validateConfig(config));
        }

        @Test
        void missingChildCollectionThrows() throws Exception {
            JsonNode config = mapper.readTree("{\"aggregateFunction\":\"SUM\",\"aggregateField\":\"amount\"}");
            assertThrows(ValidationException.class, () ->
                registry.getValidator("ROLLUP_SUMMARY").get().validateConfig(config));
        }

        @Test
        void sumWithoutFieldThrows() throws Exception {
            JsonNode config = mapper.readTree(
                "{\"childCollection\":\"items\",\"aggregateFunction\":\"SUM\"}");
            assertThrows(ValidationException.class, () ->
                registry.getValidator("ROLLUP_SUMMARY").get().validateConfig(config));
        }
    }
}
