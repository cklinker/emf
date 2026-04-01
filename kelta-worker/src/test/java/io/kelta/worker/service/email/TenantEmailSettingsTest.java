package io.kelta.worker.service.email;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantEmailSettings")
class TenantEmailSettingsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Should parse full email settings from JSONB")
    void shouldParseFullSettings() throws Exception {
        var json = mapper.readTree("""
                {
                    "email": {
                        "smtp": {
                            "host": "smtp.tenant.com",
                            "port": 465,
                            "username": "user",
                            "password": "secret123",
                            "startTls": false
                        },
                        "fromAddress": "noreply@tenant.com",
                        "fromName": "Tenant Inc"
                    }
                }
                """);

        TenantEmailSettings settings = TenantEmailSettings.fromJsonNode(json);

        assertThat(settings).isNotNull();
        assertThat(settings.smtpHost()).isEqualTo("smtp.tenant.com");
        assertThat(settings.smtpPort()).isEqualTo(465);
        assertThat(settings.smtpUsername()).isEqualTo("user");
        assertThat(settings.smtpPassword()).isEqualTo("secret123");
        assertThat(settings.smtpStartTls()).isFalse();
        assertThat(settings.fromAddress()).isEqualTo("noreply@tenant.com");
        assertThat(settings.fromName()).isEqualTo("Tenant Inc");
        assertThat(settings.hasSmtpOverride()).isTrue();
        assertThat(settings.hasFromOverride()).isTrue();
    }

    @Test
    @DisplayName("Should return null when no email section exists")
    void shouldReturnNullForNoEmailSection() throws Exception {
        var json = mapper.readTree("""
                {"theme": "dark", "locale": "en"}
                """);

        assertThat(TenantEmailSettings.fromJsonNode(json)).isNull();
    }

    @Test
    @DisplayName("Should return null for null JSON")
    void shouldReturnNullForNullJson() {
        assertThat(TenantEmailSettings.fromJsonNode(null)).isNull();
    }

    @Test
    @DisplayName("Should parse partial settings (fromAddress only, no SMTP)")
    void shouldParsePartialSettings() throws Exception {
        var json = mapper.readTree("""
                {
                    "email": {
                        "fromAddress": "custom@tenant.com",
                        "fromName": "Custom"
                    }
                }
                """);

        TenantEmailSettings settings = TenantEmailSettings.fromJsonNode(json);

        assertThat(settings).isNotNull();
        assertThat(settings.hasSmtpOverride()).isFalse();
        assertThat(settings.hasFromOverride()).isTrue();
        assertThat(settings.fromAddress()).isEqualTo("custom@tenant.com");
        assertThat(settings.smtpHost()).isNull();
    }

    @Test
    @DisplayName("Should mask smtpPassword in toString()")
    void shouldMaskPasswordInToString() throws Exception {
        var json = mapper.readTree("""
                {
                    "email": {
                        "smtp": {"host": "smtp.test.com", "password": "supersecret"}
                    }
                }
                """);

        TenantEmailSettings settings = TenantEmailSettings.fromJsonNode(json);

        String str = settings.toString();
        assertThat(str).doesNotContain("supersecret");
        assertThat(str).contains("smtpPassword=****");
    }
}
