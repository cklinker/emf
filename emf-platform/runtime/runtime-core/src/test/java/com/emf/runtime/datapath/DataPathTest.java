package com.emf.runtime.datapath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataPath}.
 */
class DataPathTest {

    @Nested
    @DisplayName("parse Tests")
    class ParseTests {

        @Test
        @DisplayName("Should parse simple single-field expression")
        void shouldParseSingleField() {
            DataPath path = DataPath.parse("name", "products");

            assertEquals("products", path.rootCollectionName());
            assertEquals("name", path.expression());
            assertEquals(1, path.segments().size());
            assertEquals("name", path.terminal().fieldName());
            assertEquals(DataPathSegment.DataPathSegmentType.FIELD, path.terminal().type());
            assertTrue(path.isSimple());
            assertEquals(0, path.depth());
            assertTrue(path.relationships().isEmpty());
        }

        @Test
        @DisplayName("Should parse two-segment expression")
        void shouldParseTwoSegments() {
            DataPath path = DataPath.parse("customer_id.email", "orders");

            assertEquals("orders", path.rootCollectionName());
            assertEquals("customer_id.email", path.expression());
            assertEquals(2, path.segments().size());
            assertEquals("customer_id", path.segments().get(0).fieldName());
            assertEquals(DataPathSegment.DataPathSegmentType.RELATIONSHIP, path.segments().get(0).type());
            assertEquals("email", path.terminal().fieldName());
            assertEquals(DataPathSegment.DataPathSegmentType.FIELD, path.terminal().type());
            assertFalse(path.isSimple());
            assertEquals(1, path.depth());
            assertEquals(1, path.relationships().size());
        }

        @Test
        @DisplayName("Should parse multi-hop expression")
        void shouldParseMultiHop() {
            DataPath path = DataPath.parse("order_id.customer_id.email", "line_items");

            assertEquals(3, path.segments().size());
            assertEquals(2, path.depth());
            assertEquals("order_id", path.relationships().get(0).fieldName());
            assertEquals("customer_id", path.relationships().get(1).fieldName());
            assertEquals("email", path.terminal().fieldName());
        }

        @Test
        @DisplayName("Should throw on empty expression")
        void shouldThrowOnEmpty() {
            assertThrows(InvalidDataPathException.class,
                () -> DataPath.parse("", "products"));
        }

        @Test
        @DisplayName("Should throw on null expression")
        void shouldThrowOnNull() {
            assertThrows(NullPointerException.class,
                () -> DataPath.parse(null, "products"));
        }

        @Test
        @DisplayName("Should throw when depth exceeds maximum")
        void shouldThrowOnExcessiveDepth() {
            // Build a path with 11 segments (exceeds MAX_DEPTH of 10)
            String expression = "a.b.c.d.e.f.g.h.i.j.k";
            InvalidDataPathException ex = assertThrows(InvalidDataPathException.class,
                () -> DataPath.parse(expression, "root"));
            assertTrue(ex.getMessage().contains("maximum depth"));
        }

        @Test
        @DisplayName("Should throw on expression with empty segment")
        void shouldThrowOnEmptySegment() {
            assertThrows(InvalidDataPathException.class,
                () -> DataPath.parse("a..b", "products"));
        }

        @Test
        @DisplayName("Should trim whitespace in expression")
        void shouldTrimExpression() {
            DataPath path = DataPath.parse("  name  ", "products");
            assertEquals("name", path.expression());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryTests {

        @Test
        @DisplayName("Should create simple path")
        void shouldCreateSimplePath() {
            DataPath path = DataPath.simple("email", "customers");

            assertTrue(path.isSimple());
            assertEquals("email", path.terminal().fieldName());
            assertEquals("customers", path.rootCollectionName());
            assertEquals("email", path.expression());
        }
    }

    @Nested
    @DisplayName("toExpression Tests")
    class SerializationTests {

        @Test
        @DisplayName("Should serialize back to original expression")
        void shouldSerializeToExpression() {
            DataPath path = DataPath.parse("order_id.customer_id.email", "items");
            assertEquals("order_id.customer_id.email", path.toExpression());
        }
    }
}
