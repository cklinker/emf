package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EventsConfig} record.
 * 
 * Validates: Requirements 1.1 - Event configuration
 */
@DisplayName("EventsConfig Record Tests")
class EventsConfigTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create events config with all parameters")
        void shouldCreateWithAllParameters() {
            List<String> eventTypes = List.of("CREATED", "UPDATED", "DELETED");
            
            EventsConfig config = new EventsConfig(true, "emf.collections", eventTypes);
            
            assertTrue(config.enabled());
            assertEquals("emf.collections", config.topicPrefix());
            assertEquals(List.of("CREATED", "UPDATED", "DELETED"), config.eventTypes());
        }

        @Test
        @DisplayName("Should default to empty list when eventTypes is null")
        void shouldDefaultToEmptyListWhenEventTypesIsNull() {
            EventsConfig config = new EventsConfig(true, "emf.collections", null);
            
            assertNotNull(config.eventTypes());
            assertTrue(config.eventTypes().isEmpty());
        }

        @Test
        @DisplayName("Should perform defensive copy of eventTypes")
        void shouldPerformDefensiveCopyOfEventTypes() {
            List<String> mutableList = new ArrayList<>();
            mutableList.add("CREATED");
            
            EventsConfig config = new EventsConfig(true, "emf.collections", mutableList);
            
            // Modify original list
            mutableList.add("UPDATED");
            
            // Config should not be affected
            assertEquals(1, config.eventTypes().size());
            assertFalse(config.eventTypes().contains("UPDATED"));
        }

        @Test
        @DisplayName("Should return immutable eventTypes")
        void shouldReturnImmutableEventTypes() {
            EventsConfig config = new EventsConfig(true, "emf.collections", List.of("CREATED"));
            
            assertThrows(UnsupportedOperationException.class, () -> {
                config.eventTypes().add("UPDATED");
            });
        }
    }

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should have default topic prefix")
        void shouldHaveDefaultTopicPrefix() {
            assertEquals("emf.collections", EventsConfig.DEFAULT_TOPIC_PREFIX);
        }

        @Test
        @DisplayName("Should have all event types constant")
        void shouldHaveAllEventTypesConstant() {
            assertEquals(List.of("CREATED", "UPDATED", "DELETED"), EventsConfig.ALL_EVENT_TYPES);
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("disabled() should create config with events disabled")
        void disabledShouldCreateDisabledConfig() {
            EventsConfig config = EventsConfig.disabled();
            
            assertFalse(config.enabled());
            assertEquals(EventsConfig.DEFAULT_TOPIC_PREFIX, config.topicPrefix());
            assertTrue(config.eventTypes().isEmpty());
        }

        @Test
        @DisplayName("allEvents() should create config with all event types")
        void allEventsShouldCreateConfigWithAllEventTypes() {
            EventsConfig config = EventsConfig.allEvents("custom.prefix");
            
            assertTrue(config.enabled());
            assertEquals("custom.prefix", config.topicPrefix());
            assertEquals(EventsConfig.ALL_EVENT_TYPES, config.eventTypes());
        }

        @Test
        @DisplayName("allEvents() should throw on null topicPrefix")
        void allEventsShouldThrowOnNullTopicPrefix() {
            assertThrows(NullPointerException.class, () -> {
                EventsConfig.allEvents(null);
            });
        }

        @Test
        @DisplayName("withEvents() should create config with specified event types")
        void withEventsShouldCreateConfigWithSpecifiedEventTypes() {
            EventsConfig config = EventsConfig.withEvents("custom.prefix", List.of("CREATED", "DELETED"));
            
            assertTrue(config.enabled());
            assertEquals("custom.prefix", config.topicPrefix());
            assertEquals(List.of("CREATED", "DELETED"), config.eventTypes());
        }

        @Test
        @DisplayName("withEvents() should throw on null topicPrefix")
        void withEventsShouldThrowOnNullTopicPrefix() {
            assertThrows(NullPointerException.class, () -> {
                EventsConfig.withEvents(null, List.of("CREATED"));
            });
        }

        @Test
        @DisplayName("withEvents() should throw on null eventTypes")
        void withEventsShouldThrowOnNullEventTypes() {
            assertThrows(NullPointerException.class, () -> {
                EventsConfig.withEvents("prefix", null);
            });
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            EventsConfig config1 = new EventsConfig(true, "prefix", List.of("CREATED"));
            EventsConfig config2 = new EventsConfig(true, "prefix", List.of("CREATED"));
            
            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            EventsConfig config1 = new EventsConfig(true, "prefix", List.of("CREATED"));
            EventsConfig config2 = new EventsConfig(false, "prefix", List.of("CREATED"));
            
            assertNotEquals(config1, config2);
        }
    }
}
