package com.emf.runtime.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TableRef")
class TableRefTest {

    @Nested
    @DisplayName("publicSchema factory")
    class PublicSchema {

        @Test
        @DisplayName("Should create a reference in public schema")
        void shouldCreatePublicRef() {
            TableRef ref = TableRef.publicSchema("tbl_orders");
            assertEquals("public", ref.schema());
            assertEquals("tbl_orders", ref.tableName());
            assertTrue(ref.isPublicSchema());
        }

        @Test
        @DisplayName("Should render bare table name for public schema")
        void shouldRenderBareTableName() {
            TableRef ref = TableRef.publicSchema("collection");
            assertEquals("collection", ref.toSql());
        }
    }

    @Nested
    @DisplayName("tenantSchema factory")
    class TenantSchema {

        @Test
        @DisplayName("Should create a reference in tenant schema")
        void shouldCreateTenantRef() {
            TableRef ref = TableRef.tenantSchema("acme-corp", "tbl_orders");
            assertEquals("acme-corp", ref.schema());
            assertEquals("tbl_orders", ref.tableName());
            assertFalse(ref.isPublicSchema());
        }

        @Test
        @DisplayName("Should render double-quoted schema-qualified name")
        void shouldRenderQualifiedName() {
            TableRef ref = TableRef.tenantSchema("acme-corp", "tbl_orders");
            assertEquals("\"acme-corp\".\"tbl_orders\"", ref.toSql());
        }

        @Test
        @DisplayName("Should render qualified name for slugs without hyphens")
        void shouldRenderQualifiedNameWithoutHyphens() {
            TableRef ref = TableRef.tenantSchema("acme", "tbl_items");
            assertEquals("\"acme\".\"tbl_items\"", ref.toSql());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("Should reject null schema")
        void shouldRejectNullSchema() {
            assertThrows(NullPointerException.class,
                    () -> new TableRef(null, "tbl_test"));
        }

        @Test
        @DisplayName("Should reject null table name")
        void shouldRejectNullTableName() {
            assertThrows(NullPointerException.class,
                    () -> new TableRef("public", null));
        }

        @Test
        @DisplayName("Should reject blank schema")
        void shouldRejectBlankSchema() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TableRef("  ", "tbl_test"));
        }

        @Test
        @DisplayName("Should reject blank table name")
        void shouldRejectBlankTableName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TableRef("public", "  "));
        }

        @Test
        @DisplayName("Should reject schema with special characters")
        void shouldRejectSchemaWithSpecialChars() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TableRef("acme;DROP TABLE", "tbl_test"));
        }

        @Test
        @DisplayName("Should reject table name starting with number")
        void shouldRejectTableNameStartingWithNumber() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TableRef("public", "123table"));
        }

        @Test
        @DisplayName("Should allow hyphens in schema for tenant slugs")
        void shouldAllowHyphensInSchema() {
            TableRef ref = new TableRef("my-tenant", "tbl_orders");
            assertEquals("my-tenant", ref.schema());
        }

        @Test
        @DisplayName("Should allow underscores in table names")
        void shouldAllowUnderscoresInTableName() {
            TableRef ref = new TableRef("public", "tbl_my_table");
            assertEquals("tbl_my_table", ref.tableName());
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("Should be equal for same schema and table")
        void shouldBeEqual() {
            TableRef a = TableRef.publicSchema("collection");
            TableRef b = TableRef.publicSchema("collection");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Should not be equal for different schemas")
        void shouldNotBeEqualDifferentSchemas() {
            TableRef a = TableRef.publicSchema("tbl_orders");
            TableRef b = TableRef.tenantSchema("acme", "tbl_orders");
            assertNotEquals(a, b);
        }
    }
}
