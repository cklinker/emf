package com.emf.controlplane.config;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.runtime.model.CollectionDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SystemCollectionSeeder.
 * Uses mocked repositories to verify seeding behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemCollectionSeeder")
class SystemCollectionSeederTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private FieldRepository fieldRepository;

    @Captor
    private ArgumentCaptor<Collection> collectionCaptor;

    private SystemCollectionSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new SystemCollectionSeeder(collectionRepository, fieldRepository);
    }

    @Nested
    @DisplayName("New collection creation")
    class NewCollectionTests {

        @Test
        @DisplayName("should create collection when it does not exist")
        void shouldCreateCollectionWhenNotExists() {
            // Given: no collections exist in DB
            when(collectionRepository.findByName(any())).thenReturn(Optional.empty());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then: should have called save for each system collection
            int expectedCount = SystemCollectionDefinitions.all().size();
            verify(collectionRepository, times(expectedCount)).save(any(Collection.class));
        }

        @Test
        @DisplayName("created collection should have correct properties")
        void createdCollectionShouldHaveCorrectProperties() {
            // Given: users collection does not exist
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            // All other collections exist as empty stubs
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then: capture the saved users collection
            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection usersCollection = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("users collection should have been saved"));

            assertEquals("users", usersCollection.getName());
            assertEquals("Users", usersCollection.getDisplayName());
            assertTrue(usersCollection.isSystemCollection());
            assertTrue(usersCollection.isActive());
            assertEquals(1, usersCollection.getCurrentVersion());
            assertEquals("PHYSICAL_TABLES", usersCollection.getStorageMode());
        }

        @Test
        @DisplayName("created collection should have all fields")
        void createdCollectionShouldHaveAllFields() {
            // Given: users collection does not exist
            CollectionDefinition usersDef = SystemCollectionDefinitions.users();
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then
            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection usersCollection = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(usersDef.fields().size(), usersCollection.getFields().size());
        }

        @Test
        @DisplayName("created fields should have correct types")
        void createdFieldsShouldHaveCorrectTypes() {
            // Given
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then
            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection usersCollection = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            // Check email field
            Field emailField = usersCollection.getFields().stream()
                    .filter(f -> "email".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("string", emailField.getType());
            assertTrue(emailField.isRequired());

            // Check loginCount field
            Field loginCountField = usersCollection.getFields().stream()
                    .filter(f -> "loginCount".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("number", loginCountField.getType());

            // Check mfaEnabled field
            Field mfaField = usersCollection.getFields().stream()
                    .filter(f -> "mfaEnabled".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("boolean", mfaField.getType());

            // Check lastLoginAt field
            Field lastLoginField = usersCollection.getFields().stream()
                    .filter(f -> "lastLoginAt".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("datetime", lastLoginField.getType());

            // Check settings field (JSON -> "object")
            Field settingsField = usersCollection.getFields().stream()
                    .filter(f -> "settings".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("object", settingsField.getType());
        }

        @Test
        @DisplayName("created fields should have correct field ordering")
        void createdFieldsShouldHaveCorrectOrdering() {
            // Given
            when(collectionRepository.findByName("profiles")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"profiles".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then
            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection profilesCollection = collectionCaptor.getAllValues().stream()
                    .filter(c -> "profiles".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            List<Field> fields = profilesCollection.getFields();
            for (int i = 0; i < fields.size(); i++) {
                assertEquals(i, fields.get(i).getOrder(),
                        "Field '" + fields.get(i).getName() + "' should have order " + i);
            }
        }

        @Test
        @DisplayName("should set API path on collection")
        void shouldSetApiPathOnCollection() {
            // Given
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then
            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection usersCollection = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("/api/users", usersCollection.getPath());
        }
    }

    @Nested
    @DisplayName("Existing collection sync")
    class ExistingCollectionTests {

        @Test
        @DisplayName("should not recreate existing collection")
        void shouldNotRecreateExistingCollection() {
            // Given: all collections already exist
            when(collectionRepository.findByName(any()))
                    .thenReturn(Optional.of(createStubCollection("existing")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then: no new collections created, only potential field sync saves
            verify(collectionRepository, never()).save(argThat(c ->
                    c.getId() == null && !c.isSystemCollection()));
        }

        @Test
        @DisplayName("should mark existing non-system collection as system")
        void shouldMarkExistingAsSystemCollection() {
            // Given: collection exists but not marked as system
            Collection nonSystemCollection = createStubCollection("users");
            nonSystemCollection.setSystemCollection(false);
            when(collectionRepository.findByName("users")).thenReturn(Optional.of(nonSystemCollection));
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("other")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then
            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(saved.isSystemCollection());
        }

        @Test
        @DisplayName("should add missing fields to existing collection")
        void shouldAddMissingFieldsToExistingCollection() {
            // Given: users collection exists but missing some fields
            Collection existingCollection = createStubCollection("users");
            existingCollection.setSystemCollection(true);

            Field emailField = new Field();
            emailField.setName("email");
            emailField.setType("string");

            when(collectionRepository.findByName("users"))
                    .thenReturn(Optional.of(existingCollection));
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("other")));
            when(fieldRepository.findByCollectionId(existingCollection.getId()))
                    .thenReturn(List.of(emailField));
            when(fieldRepository.findByCollectionId(argThat(id ->
                    !existingCollection.getId().equals(id))))
                    .thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then: should save the updated collection with new fields
            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            // Should have added fields beyond just "email"
            CollectionDefinition usersDef = SystemCollectionDefinitions.users();
            int expectedNewFields = usersDef.fields().size() - 1; // minus "email" which already exists
            assertTrue(saved.getFields().size() >= expectedNewFields,
                    "Should have added missing fields");
        }

        @Test
        @DisplayName("should not modify collection when all fields exist")
        void shouldNotModifyWhenAllFieldsExist() {
            // Given: collection exists with all expected fields
            Collection existingCollection = createStubCollection("profiles");
            existingCollection.setSystemCollection(true);

            List<Field> allFields = new ArrayList<>();
            for (var fieldDef : SystemCollectionDefinitions.profiles().fields()) {
                Field f = new Field();
                f.setName(fieldDef.name());
                f.setType("string");
                allFields.add(f);
            }

            when(collectionRepository.findByName("profiles"))
                    .thenReturn(Optional.of(existingCollection));
            when(collectionRepository.findByName(argThat(name -> !"profiles".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("other")));
            when(fieldRepository.findByCollectionId(existingCollection.getId()))
                    .thenReturn(allFields);
            when(fieldRepository.findByCollectionId(argThat(id ->
                    !existingCollection.getId().equals(id))))
                    .thenReturn(List.of());

            // When
            seeder.run(new DefaultApplicationArguments());

            // Then: the profiles collection should not have been saved again for field sync
            // (it may be saved for other stub collections, but not for profiles)
            verify(collectionRepository, never()).save(existingCollection);
        }
    }

    @Nested
    @DisplayName("Field type mapping")
    class FieldTypeMappingTests {

        @Test
        @DisplayName("STRING should map to 'string'")
        void stringFieldTypeShouldMapCorrectly() {
            assertFieldTypeMapping("profiles", "name", "string");
        }

        @Test
        @DisplayName("INTEGER should map to 'number'")
        void integerFieldTypeShouldMapCorrectly() {
            assertFieldTypeMapping("users", "loginCount", "number");
        }

        @Test
        @DisplayName("BOOLEAN should map to 'boolean'")
        void booleanFieldTypeShouldMapCorrectly() {
            assertFieldTypeMapping("users", "mfaEnabled", "boolean");
        }

        @Test
        @DisplayName("DATETIME should map to 'datetime'")
        void datetimeFieldTypeShouldMapCorrectly() {
            assertFieldTypeMapping("users", "lastLoginAt", "datetime");
        }

        @Test
        @DisplayName("JSON should map to 'object'")
        void jsonFieldTypeShouldMapCorrectly() {
            assertFieldTypeMapping("tenants", "settings", "object");
        }

        @Test
        @DisplayName("LONG should map to 'number'")
        void longFieldTypeShouldMapCorrectly() {
            assertFieldTypeMapping("attachments", "fileSize", "number");
        }

        private void assertFieldTypeMapping(String collectionName, String fieldName, String expectedType) {
            when(collectionRepository.findByName(collectionName)).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !collectionName.equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            seeder.run(new DefaultApplicationArguments());

            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> collectionName.equals(c.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Collection '" + collectionName + "' should have been saved"));

            Field field = saved.getFields().stream()
                    .filter(f -> fieldName.equals(f.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Field '" + fieldName + "' should exist in collection '" + collectionName + "'"));

            assertEquals(expectedType, field.getType(),
                    "Field '" + fieldName + "' should have type '" + expectedType + "'");
        }
    }

    @Nested
    @DisplayName("Tenant ID and metadata persistence")
    class MetadataPersistenceTests {

        @Test
        @DisplayName("should use default tenant UUID, not 'SYSTEM'")
        void shouldUseDefaultTenantUuid() {
            assertEquals("00000000-0000-0000-0000-000000000001",
                    SystemCollectionSeeder.SYSTEM_TENANT_ID,
                    "SYSTEM_TENANT_ID should be the default tenant UUID from V9 migration");
        }

        @Test
        @DisplayName("created collection should use default tenant ID")
        void createdCollectionShouldUseDefaultTenantId() {
            when(collectionRepository.findByName("tenants")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"tenants".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            seeder.run(new DefaultApplicationArguments());

            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "tenants".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("00000000-0000-0000-0000-000000000001", saved.getTenantId());
        }

        @Test
        @DisplayName("should persist columnName on fields that define it")
        void shouldPersistColumnName() {
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            seeder.run(new DefaultApplicationArguments());

            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            Field firstNameField = saved.getFields().stream()
                    .filter(f -> "firstName".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("first_name", firstNameField.getColumnName(),
                    "firstName field should have column_name = 'first_name'");
        }

        @Test
        @DisplayName("should persist defaultValue on fields that define it")
        void shouldPersistDefaultValue() {
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            seeder.run(new DefaultApplicationArguments());

            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            Field loginCountField = saved.getFields().stream()
                    .filter(f -> "loginCount".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(loginCountField.getDefaultValue(),
                    "loginCount field should have a default value set");
        }

        @Test
        @DisplayName("should persist constraints with enum values")
        void shouldPersistConstraintsWithEnumValues() {
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            seeder.run(new DefaultApplicationArguments());

            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            Field statusField = saved.getFields().stream()
                    .filter(f -> "status".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(statusField.getConstraints(),
                    "status field should have constraints with enum values");
            assertTrue(statusField.getConstraints().contains("ACTIVE"),
                    "status constraints should contain 'ACTIVE' enum value");
        }

        @Test
        @DisplayName("should persist immutable flag on fields")
        void shouldPersistImmutableFlag() {
            when(collectionRepository.findByName("connected-apps")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"connected-apps".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            seeder.run(new DefaultApplicationArguments());

            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "connected-apps".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            Field clientSecretField = saved.getFields().stream()
                    .filter(f -> "clientSecretHash".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(clientSecretField.isImmutable(),
                    "clientSecretHash field should be marked as immutable");
        }

        @Test
        @DisplayName("should persist reference config on lookup fields")
        void shouldPersistReferenceConfig() {
            when(collectionRepository.findByName("users")).thenReturn(Optional.empty());
            when(collectionRepository.findByName(argThat(name -> !"users".equals(name))))
                    .thenReturn(Optional.of(createStubCollection("stub")));
            when(fieldRepository.findByCollectionId(any())).thenReturn(List.of());
            when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            seeder.run(new DefaultApplicationArguments());

            verify(collectionRepository, atLeast(1)).save(collectionCaptor.capture());
            Collection saved = collectionCaptor.getAllValues().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            Field profileIdField = saved.getFields().stream()
                    .filter(f -> "profileId".equals(f.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("profiles", profileIdField.getReferenceTarget(),
                    "profileId field should reference 'profiles' collection");
            assertEquals("LOOKUP", profileIdField.getRelationshipType(),
                    "profileId field should be a LOOKUP relationship");
        }
    }

    @Nested
    @DisplayName("System collection definitions completeness")
    class DefinitionsCompletenessTests {

        @Test
        @DisplayName("should have at least 74 system collection definitions")
        void shouldHaveExpectedNumberOfDefinitions() {
            assertTrue(SystemCollectionDefinitions.all().size() >= 74,
                    "Expected at least 74 system collection definitions, got: "
                    + SystemCollectionDefinitions.all().size());
        }

        @Test
        @DisplayName("all definitions should have unique names")
        void allDefinitionsShouldHaveUniqueNames() {
            List<String> names = SystemCollectionDefinitions.all().stream()
                    .map(CollectionDefinition::name)
                    .toList();
            assertEquals(names.size(), names.stream().distinct().count(),
                    "All collection definition names should be unique");
        }

        @Test
        @DisplayName("byName map should contain all definitions")
        void byNameMapShouldContainAllDefinitions() {
            assertEquals(SystemCollectionDefinitions.all().size(),
                    SystemCollectionDefinitions.byName().size());
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Collection createStubCollection(String name) {
        Collection collection = new Collection();
        collection.setName(name);
        collection.setDisplayName(name);
        collection.setSystemCollection(true);
        collection.setActive(true);
        collection.setCurrentVersion(1);
        collection.setStorageMode("PHYSICAL_TABLES");
        return collection;
    }
}
