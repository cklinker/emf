package com.emf.controlplane.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Tenant entity class.
 */
class TenantTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should generate UUID on default construction")
        void shouldGenerateUuidOnDefaultConstruction() {
            Tenant tenant = new Tenant();
            assertThat(tenant.getId()).isNotNull();
            assertThat(tenant.getId()).hasSize(36);
        }

        @Test
        @DisplayName("should set slug and name via parameterized constructor")
        void shouldSetSlugAndNameViaParameterizedConstructor() {
            Tenant tenant = new Tenant("acme-corp", "Acme Corporation");
            assertThat(tenant.getSlug()).isEqualTo("acme-corp");
            assertThat(tenant.getName()).isEqualTo("Acme Corporation");
            assertThat(tenant.getId()).isNotNull();
        }

        @Test
        @DisplayName("should generate unique IDs for different instances")
        void shouldGenerateUniqueIds() {
            Tenant t1 = new Tenant("tenant-a", "Tenant A");
            Tenant t2 = new Tenant("tenant-b", "Tenant B");
            assertThat(t1.getId()).isNotEqualTo(t2.getId());
        }
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValueTests {

        @Test
        @DisplayName("should default edition to PROFESSIONAL")
        void shouldDefaultEditionToProfessional() {
            Tenant tenant = new Tenant();
            assertThat(tenant.getEdition()).isEqualTo("PROFESSIONAL");
        }

        @Test
        @DisplayName("should default status to PROVISIONING")
        void shouldDefaultStatusToProvisioning() {
            Tenant tenant = new Tenant();
            assertThat(tenant.getStatus()).isEqualTo("PROVISIONING");
        }

        @Test
        @DisplayName("should default settings to empty JSON object")
        void shouldDefaultSettingsToEmptyJson() {
            Tenant tenant = new Tenant();
            assertThat(tenant.getSettings()).isEqualTo("{}");
        }

        @Test
        @DisplayName("should default limits to empty JSON object")
        void shouldDefaultLimitsToEmptyJson() {
            Tenant tenant = new Tenant();
            assertThat(tenant.getLimits()).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("Getters and setters")
    class GetterSetterTests {

        @Test
        @DisplayName("should get and set slug")
        void shouldGetAndSetSlug() {
            Tenant tenant = new Tenant();
            tenant.setSlug("new-slug");
            assertThat(tenant.getSlug()).isEqualTo("new-slug");
        }

        @Test
        @DisplayName("should get and set name")
        void shouldGetAndSetName() {
            Tenant tenant = new Tenant();
            tenant.setName("New Name");
            assertThat(tenant.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("should get and set edition")
        void shouldGetAndSetEdition() {
            Tenant tenant = new Tenant();
            tenant.setEdition("ENTERPRISE");
            assertThat(tenant.getEdition()).isEqualTo("ENTERPRISE");
        }

        @Test
        @DisplayName("should get and set status")
        void shouldGetAndSetStatus() {
            Tenant tenant = new Tenant();
            tenant.setStatus("ACTIVE");
            assertThat(tenant.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should get and set settings")
        void shouldGetAndSetSettings() {
            Tenant tenant = new Tenant();
            String settings = "{\"timezone\": \"UTC\"}";
            tenant.setSettings(settings);
            assertThat(tenant.getSettings()).isEqualTo(settings);
        }

        @Test
        @DisplayName("should get and set limits")
        void shouldGetAndSetLimits() {
            Tenant tenant = new Tenant();
            String limits = "{\"max_users\": 100}";
            tenant.setLimits(limits);
            assertThat(tenant.getLimits()).isEqualTo(limits);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWhenIdsMatch() {
            Tenant t1 = new Tenant("slug-a", "Tenant A");
            Tenant t2 = new Tenant("slug-b", "Tenant B");
            t2.setId(t1.getId());
            assertThat(t1).isEqualTo(t2);
            assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when IDs differ")
        void shouldNotBeEqualWhenIdsDiffer() {
            Tenant t1 = new Tenant("slug-a", "Tenant A");
            Tenant t2 = new Tenant("slug-a", "Tenant A");
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            Tenant tenant = new Tenant("slug", "Name");
            assertThat(tenant).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should include key fields in toString")
        void shouldIncludeKeyFieldsInToString() {
            Tenant tenant = new Tenant("acme", "Acme Corp");
            tenant.setEdition("ENTERPRISE");
            tenant.setStatus("ACTIVE");

            String result = tenant.toString();
            assertThat(result).contains("acme");
            assertThat(result).contains("Acme Corp");
            assertThat(result).contains("ENTERPRISE");
            assertThat(result).contains("ACTIVE");
            assertThat(result).contains(tenant.getId());
        }
    }
}
