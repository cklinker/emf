package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for system collection fields added to CollectionDefinition and FieldDefinition.
 */
@DisplayName("System Collection Fields Tests")
class SystemCollectionFieldsTest {

    private static final Instant NOW = Instant.now();

    @Nested
    @DisplayName("CollectionDefinition System Fields")
    class CollectionDefinitionSystemFieldsTests {

        @Test
        @DisplayName("Backward-compatible constructor should default system fields")
        void backwardCompatibleConstructorShouldDefaultSystemFields() {
            CollectionDefinition collection = new CollectionDefinition(
                "products", "Products", "Product catalog",
                List.of(FieldDefinition.requiredString("name")),
                StorageConfig.physicalTable("tbl_products"),
                ApiConfig.allEnabled("/api/products"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW
            );

            assertFalse(collection.systemCollection());
            assertTrue(collection.tenantScoped());
            assertFalse(collection.readOnly());
            assertTrue(collection.immutableFields().isEmpty());
            assertTrue(collection.columnMapping().isEmpty());
        }

        @Test
        @DisplayName("Full constructor should set system fields")
        void fullConstructorShouldSetSystemFields() {
            Set<String> immutableFields = Set.of("tenantId", "email");
            Map<String, String> columnMapping = Map.of("firstName", "first_name");

            CollectionDefinition collection = new CollectionDefinition(
                "users", "Users", "Platform users",
                List.of(FieldDefinition.requiredString("email")),
                StorageConfig.physicalTable("platform_user"),
                ApiConfig.allEnabled("/api/users"),
                AuthzConfig.disabled(), EventsConfig.allEvents("emf.collections"),
                1L, NOW, NOW,
                true, true, false, immutableFields, columnMapping
            );

            assertTrue(collection.systemCollection());
            assertTrue(collection.tenantScoped());
            assertFalse(collection.readOnly());
            assertEquals(immutableFields, collection.immutableFields());
            assertEquals(columnMapping, collection.columnMapping());
        }

        @Test
        @DisplayName("Read-only system collection should be configured correctly")
        void readOnlySystemCollectionShouldBeConfiguredCorrectly() {
            CollectionDefinition collection = new CollectionDefinition(
                "audit-logs", "Audit Logs", "Security audit logs",
                List.of(FieldDefinition.string("action")),
                StorageConfig.physicalTable("security_audit_log"),
                ApiConfig.readOnly("/api/audit-logs"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, true, true, Set.of(), Map.of()
            );

            assertTrue(collection.systemCollection());
            assertTrue(collection.readOnly());
        }

        @Test
        @DisplayName("Non-tenant-scoped collection should be configured correctly")
        void nonTenantScopedCollectionShouldBeConfiguredCorrectly() {
            CollectionDefinition collection = new CollectionDefinition(
                "tenants", "Tenants", "Platform tenants",
                List.of(FieldDefinition.requiredString("slug")),
                StorageConfig.physicalTable("tenant"),
                ApiConfig.allEnabled("/api/tenants"),
                AuthzConfig.disabled(), EventsConfig.allEvents("emf.collections"),
                1L, NOW, NOW,
                true, false, false, Set.of(), Map.of()
            );

            assertTrue(collection.systemCollection());
            assertFalse(collection.tenantScoped());
        }

        @Test
        @DisplayName("immutableFields should be defensively copied")
        void immutableFieldsShouldBeDefensivelyCopied() {
            Set<String> mutableSet = new java.util.HashSet<>();
            mutableSet.add("tenantId");

            CollectionDefinition collection = new CollectionDefinition(
                "test", "Test", null,
                List.of(FieldDefinition.string("field")),
                StorageConfig.physicalTable("tbl"), ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, true, false, mutableSet, Map.of()
            );

            mutableSet.add("extra");
            assertFalse(collection.immutableFields().contains("extra"));
        }

        @Test
        @DisplayName("columnMapping should be defensively copied")
        void columnMappingShouldBeDefensivelyCopied() {
            Map<String, String> mutableMap = new java.util.HashMap<>();
            mutableMap.put("firstName", "first_name");

            CollectionDefinition collection = new CollectionDefinition(
                "test", "Test", null,
                List.of(FieldDefinition.string("field")),
                StorageConfig.physicalTable("tbl"), ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, true, false, Set.of(), mutableMap
            );

            mutableMap.put("extra", "extra_col");
            assertFalse(collection.columnMapping().containsKey("extra"));
        }

        @Test
        @DisplayName("null immutableFields should default to empty set")
        void nullImmutableFieldsShouldDefaultToEmptySet() {
            CollectionDefinition collection = new CollectionDefinition(
                "test", "Test", null,
                List.of(FieldDefinition.string("field")),
                StorageConfig.physicalTable("tbl"), ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                false, true, false, null, null
            );

            assertNotNull(collection.immutableFields());
            assertTrue(collection.immutableFields().isEmpty());
            assertNotNull(collection.columnMapping());
            assertTrue(collection.columnMapping().isEmpty());
        }

        @Test
        @DisplayName("withIncrementedVersion should preserve system fields")
        void withIncrementedVersionShouldPreserveSystemFields() {
            CollectionDefinition original = new CollectionDefinition(
                "users", "Users", null,
                List.of(FieldDefinition.string("email")),
                StorageConfig.physicalTable("platform_user"),
                ApiConfig.allEnabled("/api/users"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, true, false,
                Set.of("tenantId"), Map.of("firstName", "first_name")
            );

            CollectionDefinition updated = original.withIncrementedVersion();

            assertTrue(updated.systemCollection());
            assertTrue(updated.tenantScoped());
            assertEquals(Set.of("tenantId"), updated.immutableFields());
            assertEquals(Map.of("firstName", "first_name"), updated.columnMapping());
            assertEquals(2L, updated.version());
        }

        @Test
        @DisplayName("withFields should preserve system fields")
        void withFieldsShouldPreserveSystemFields() {
            CollectionDefinition original = new CollectionDefinition(
                "users", "Users", null,
                List.of(FieldDefinition.string("email")),
                StorageConfig.physicalTable("platform_user"),
                ApiConfig.allEnabled("/api/users"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, false, true,
                Set.of("tenantId"), Map.of("firstName", "first_name")
            );

            CollectionDefinition updated = original.withFields(
                List.of(FieldDefinition.string("email"), FieldDefinition.string("name"))
            );

            assertTrue(updated.systemCollection());
            assertFalse(updated.tenantScoped());
            assertTrue(updated.readOnly());
            assertEquals(Set.of("tenantId"), updated.immutableFields());
            assertEquals(2, updated.fields().size());
        }

        @Test
        @DisplayName("getEffectiveColumnName should use field-level columnName first")
        void getEffectiveColumnNameShouldUseFieldLevelFirst() {
            CollectionDefinition collection = new CollectionDefinition(
                "users", "Users", null,
                List.of(FieldDefinition.string("firstName").withColumnName("first_name")),
                StorageConfig.physicalTable("platform_user"),
                ApiConfig.allEnabled("/api/users"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, true, false, Set.of(),
                Map.of("firstName", "should_not_use_this")
            );

            assertEquals("first_name", collection.getEffectiveColumnName("firstName"));
        }

        @Test
        @DisplayName("getEffectiveColumnName should fall back to collection-level mapping")
        void getEffectiveColumnNameShouldFallBackToCollectionMapping() {
            CollectionDefinition collection = new CollectionDefinition(
                "users", "Users", null,
                List.of(FieldDefinition.string("email")),
                StorageConfig.physicalTable("platform_user"),
                ApiConfig.allEnabled("/api/users"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, true, false, Set.of(),
                Map.of("unmappedField", "unmapped_column")
            );

            assertEquals("unmapped_column", collection.getEffectiveColumnName("unmappedField"));
        }

        @Test
        @DisplayName("getEffectiveColumnName should use field name as default")
        void getEffectiveColumnNameShouldUseFieldNameAsDefault() {
            CollectionDefinition collection = new CollectionDefinition(
                "users", "Users", null,
                List.of(FieldDefinition.string("email")),
                StorageConfig.physicalTable("platform_user"),
                ApiConfig.allEnabled("/api/users"),
                AuthzConfig.disabled(), EventsConfig.disabled(),
                1L, NOW, NOW,
                true, true, false, Set.of(), Map.of()
            );

            assertEquals("email", collection.getEffectiveColumnName("email"));
        }
    }

    @Nested
    @DisplayName("CollectionDefinitionBuilder System Fields")
    class BuilderSystemFieldsTests {

        @Test
        @DisplayName("Builder should default system fields for non-system collections")
        void builderShouldDefaultSystemFields() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("products")
                .addField(FieldDefinition.string("name"))
                .build();

            assertFalse(collection.systemCollection());
            assertTrue(collection.tenantScoped());
            assertFalse(collection.readOnly());
            assertTrue(collection.immutableFields().isEmpty());
            assertTrue(collection.columnMapping().isEmpty());
        }

        @Test
        @DisplayName("Builder should support system collection configuration")
        void builderShouldSupportSystemCollectionConfig() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("users")
                .displayName("Users")
                .addField(FieldDefinition.requiredString("email"))
                .storageConfig(StorageConfig.physicalTable("platform_user"))
                .systemCollection(true)
                .tenantScoped(true)
                .readOnly(false)
                .addImmutableField("tenantId")
                .addColumnMapping("firstName", "first_name")
                .build();

            assertTrue(collection.systemCollection());
            assertTrue(collection.tenantScoped());
            assertFalse(collection.readOnly());
            assertTrue(collection.immutableFields().contains("tenantId"));
            assertEquals("first_name", collection.columnMapping().get("firstName"));
        }

        @Test
        @DisplayName("Builder should support read-only system collections")
        void builderShouldSupportReadOnlyCollections() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("audit-logs")
                .addField(FieldDefinition.string("action"))
                .systemCollection(true)
                .readOnly(true)
                .build();

            assertTrue(collection.systemCollection());
            assertTrue(collection.readOnly());
        }

        @Test
        @DisplayName("Builder should support bulk immutable fields")
        void builderShouldSupportBulkImmutableFields() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .immutableFields(Set.of("tenantId", "email", "createdAt"))
                .build();

            assertEquals(3, collection.immutableFields().size());
            assertTrue(collection.immutableFields().contains("tenantId"));
            assertTrue(collection.immutableFields().contains("email"));
            assertTrue(collection.immutableFields().contains("createdAt"));
        }

        @Test
        @DisplayName("Builder should support bulk column mapping")
        void builderShouldSupportBulkColumnMapping() {
            Map<String, String> mapping = Map.of(
                "firstName", "first_name",
                "lastName", "last_name"
            );

            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .columnMapping(mapping)
                .build();

            assertEquals(2, collection.columnMapping().size());
            assertEquals("first_name", collection.columnMapping().get("firstName"));
            assertEquals("last_name", collection.columnMapping().get("lastName"));
        }

        @Test
        @DisplayName("Builder method chaining should work for new methods")
        void builderMethodChainingShouldWorkForNewMethods() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder();

            assertSame(builder, builder.systemCollection(true));
            assertSame(builder, builder.tenantScoped(false));
            assertSame(builder, builder.readOnly(true));
            assertSame(builder, builder.immutableFields(Set.of("a")));
            assertSame(builder, builder.addImmutableField("b"));
            assertSame(builder, builder.columnMapping(Map.of("a", "b")));
            assertSame(builder, builder.addColumnMapping("c", "d"));
        }
    }

    @Nested
    @DisplayName("FieldDefinition columnName")
    class FieldDefinitionColumnNameTests {

        @Test
        @DisplayName("Backward-compatible constructor should default columnName to null")
        void backwardCompatibleConstructorShouldDefaultColumnNameToNull() {
            FieldDefinition field = new FieldDefinition(
                "name", FieldType.STRING, true, false, false,
                null, null, null, null, null
            );

            assertNull(field.columnName());
        }

        @Test
        @DisplayName("Full constructor should set columnName")
        void fullConstructorShouldSetColumnName() {
            FieldDefinition field = new FieldDefinition(
                "firstName", FieldType.STRING, true, false, false,
                null, null, null, null, null, "first_name"
            );

            assertEquals("first_name", field.columnName());
        }

        @Test
        @DisplayName("Factory methods should default columnName to null")
        void factoryMethodsShouldDefaultColumnNameToNull() {
            assertNull(FieldDefinition.string("name").columnName());
            assertNull(FieldDefinition.requiredString("name").columnName());
            assertNull(FieldDefinition.integer("count").columnName());
            assertNull(FieldDefinition.bool("active").columnName());
            assertNull(FieldDefinition.datetime("createdAt").columnName());
            assertNull(FieldDefinition.json("metadata").columnName());
        }

        @Test
        @DisplayName("withColumnName should create new field with column name")
        void withColumnNameShouldCreateNewField() {
            FieldDefinition original = FieldDefinition.string("firstName");
            FieldDefinition withColumn = original.withColumnName("first_name");

            assertEquals("firstName", withColumn.name());
            assertEquals("first_name", withColumn.columnName());
            assertEquals(FieldType.STRING, withColumn.type());
            assertTrue(withColumn.nullable());

            // Original should be unaffected
            assertNull(original.columnName());
        }

        @Test
        @DisplayName("effectiveColumnName should return columnName when set")
        void effectiveColumnNameShouldReturnColumnName() {
            FieldDefinition field = FieldDefinition.string("firstName").withColumnName("first_name");
            assertEquals("first_name", field.effectiveColumnName());
        }

        @Test
        @DisplayName("effectiveColumnName should return field name when columnName is null")
        void effectiveColumnNameShouldReturnFieldName() {
            FieldDefinition field = FieldDefinition.string("email");
            assertEquals("email", field.effectiveColumnName());
        }

        @Test
        @DisplayName("Builder should support columnName")
        void builderShouldSupportColumnName() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("firstName")
                .type(FieldType.STRING)
                .columnName("first_name")
                .build();

            assertEquals("firstName", field.name());
            assertEquals("first_name", field.columnName());
        }

        @Test
        @DisplayName("Builder should default columnName to null")
        void builderShouldDefaultColumnNameToNull() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("email")
                .type(FieldType.STRING)
                .build();

            assertNull(field.columnName());
        }
    }
}
