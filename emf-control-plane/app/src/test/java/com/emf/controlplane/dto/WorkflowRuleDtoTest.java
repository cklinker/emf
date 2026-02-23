package com.emf.controlplane.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRuleDtoTest {

    @Nested
    @DisplayName("parseTriggerFields")
    class ParseTriggerFieldsTests {

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNull() {
            assertNull(WorkflowRuleDto.parseTriggerFields(null));
        }

        @Test
        @DisplayName("Should return null for empty string")
        void shouldReturnNullForEmpty() {
            assertNull(WorkflowRuleDto.parseTriggerFields(""));
        }

        @Test
        @DisplayName("Should return null for blank string")
        void shouldReturnNullForBlank() {
            assertNull(WorkflowRuleDto.parseTriggerFields("   "));
        }

        @Test
        @DisplayName("Should parse valid JSON array")
        void shouldParseValidArray() {
            List<String> result = WorkflowRuleDto.parseTriggerFields("[\"status\",\"priority\"]");
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("status", result.get(0));
            assertEquals("priority", result.get(1));
        }

        @Test
        @DisplayName("Should parse empty JSON array")
        void shouldParseEmptyArray() {
            List<String> result = WorkflowRuleDto.parseTriggerFields("[]");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should parse single-element array")
        void shouldParseSingleElement() {
            List<String> result = WorkflowRuleDto.parseTriggerFields("[\"status\"]");
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("status", result.get(0));
        }

        @Test
        @DisplayName("Should return null for invalid JSON")
        void shouldReturnNullForInvalidJson() {
            assertNull(WorkflowRuleDto.parseTriggerFields("not-valid-json"));
        }

        @Test
        @DisplayName("Should return null for non-array JSON")
        void shouldReturnNullForNonArrayJson() {
            assertNull(WorkflowRuleDto.parseTriggerFields("{\"key\":\"value\"}"));
        }
    }

    @Nested
    @DisplayName("serializeTriggerFields")
    class SerializeTriggerFieldsTests {

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNull() {
            assertNull(WorkflowRuleDto.serializeTriggerFields(null));
        }

        @Test
        @DisplayName("Should return null for empty list")
        void shouldReturnNullForEmptyList() {
            assertNull(WorkflowRuleDto.serializeTriggerFields(List.of()));
        }

        @Test
        @DisplayName("Should serialize list to JSON array")
        void shouldSerializeToJson() {
            String result = WorkflowRuleDto.serializeTriggerFields(List.of("status", "priority"));
            assertNotNull(result);
            assertEquals("[\"status\",\"priority\"]", result);
        }

        @Test
        @DisplayName("Should serialize single element list")
        void shouldSerializeSingleElement() {
            String result = WorkflowRuleDto.serializeTriggerFields(List.of("status"));
            assertNotNull(result);
            assertEquals("[\"status\"]", result);
        }

        @Test
        @DisplayName("Roundtrip: serialize then parse should return original")
        void shouldRoundtrip() {
            List<String> original = List.of("status", "priority", "assignee");
            String json = WorkflowRuleDto.serializeTriggerFields(original);
            List<String> parsed = WorkflowRuleDto.parseTriggerFields(json);
            assertEquals(original, parsed);
        }
    }
}
