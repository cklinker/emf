package io.kelta.worker;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.worker.scim.model.ScimUser;

/**
 * Pre-built domain objects for tests. Use these instead of constructing
 * objects manually so tests stay concise and don't break when constructors change.
 *
 * For CollectionDefinition and FieldDefinition, use the builders from runtime-core:
 * {@code CollectionDefinition.builder()} and {@code new FieldDefinitionBuilder()}.
 */
public final class TestFixtures {

    public static final String TENANT_ID = "tenant-1";
    public static final String USER_ID = "user-1";

    private TestFixtures() {}

    public static CollectionDefinition collectionDefinition() {
        return CollectionDefinition.builder()
                .name("customers")
                .displayName("Customers")
                .description("Customer records")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.requiredString("email"))
                .build();
    }

    public static CollectionDefinition collectionDefinition(String name) {
        return CollectionDefinition.builder()
                .name(name)
                .displayName(name.substring(0, 1).toUpperCase() + name.substring(1))
                .description(name + " records")
                .addField(FieldDefinition.requiredString("name"))
                .build();
    }

    public static FieldDefinition stringField(String name) {
        return FieldDefinition.requiredString(name);
    }

    public static FieldDefinition optionalField(String name, FieldType type) {
        return new io.kelta.runtime.model.FieldDefinitionBuilder()
                .name(name)
                .type(type)
                .nullable(true)
                .build();
    }

    public static ScimUser scimUser() {
        ScimUser user = new ScimUser();
        user.setUserName("john@example.com");
        return user;
    }

    public static ScimUser scimUser(String email) {
        ScimUser user = new ScimUser();
        user.setUserName(email);
        return user;
    }
}
