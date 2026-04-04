package io.kelta.worker.service.push;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultPushService Tests")
class DefaultPushServiceTest {

    @Mock private PushProvider pushProvider;
    @Mock private JdbcTemplate jdbcTemplate;

    private DefaultPushService service;

    @BeforeEach
    void setUp() {
        service = new DefaultPushService(pushProvider, jdbcTemplate, new ObjectMapper());
    }

    @Nested
    @DisplayName("Device Registration")
    class DeviceRegistration {
        @Test
        void shouldRejectBlankToken() {
            assertThatThrownBy(() -> service.registerDevice("u1", "t1", "ios", "", "iPhone"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNullToken() {
            assertThatThrownBy(() -> service.registerDevice("u1", "t1", "ios", null, "iPhone"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectTokenTooLong() {
            String longToken = "x".repeat(501);
            assertThatThrownBy(() -> service.registerDevice("u1", "t1", "ios", longToken, "iPhone"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectInvalidPlatform() {
            assertThatThrownBy(() -> service.registerDevice("u1", "t1", "blackberry", "token", "BB"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldAcceptValidRegistration() {
            when(jdbcTemplate.queryForList(contains("push_device"), eq("t1"), eq("valid-token")))
                    .thenReturn(List.of());

            String id = service.registerDevice("u1", "t1", "ios", "valid-token", "iPhone");
            assertThat(id).isNotNull();
            verify(jdbcTemplate).update(contains("INSERT INTO push_device"), any(), eq("u1"), eq("t1"),
                    eq("ios"), eq("valid-token"), eq("iPhone"));
        }

        @Test
        void shouldUpdateExistingDevice() {
            when(jdbcTemplate.queryForList(contains("push_device"), eq("t1"), eq("existing-token")))
                    .thenReturn(List.of(Map.of("id", "device-1")));

            String id = service.registerDevice("u1", "t1", "android", "existing-token", "Pixel");
            assertThat(id).isEqualTo("device-1");
            verify(jdbcTemplate).update(contains("UPDATE push_device"), eq("u1"), eq("android"), eq("Pixel"), eq("device-1"));
        }
    }

    @Nested
    @DisplayName("Send Notifications")
    class SendNotifications {
        @Test
        void shouldSendToAllUserDevices() {
            when(jdbcTemplate.queryForList(contains("push_device WHERE user_id"), eq("u1"), eq("t1")))
                    .thenReturn(List.of(
                            Map.of("id", "d1", "device_token", "tok1", "platform", "ios"),
                            Map.of("id", "d2", "device_token", "tok2", "platform", "android")
                    ));
            when(jdbcTemplate.queryForList(contains("tenant WHERE id"), eq("t1")))
                    .thenReturn(List.of());

            int delivered = service.sendToUser("u1", "t1", "Title", "Body", null);

            assertThat(delivered).isEqualTo(2);
            verify(pushProvider, times(2)).send(any(), isNull());
        }

        @Test
        void shouldRemoveStaleTokenOnInvalidTokenError() {
            when(jdbcTemplate.queryForList(contains("push_device WHERE user_id"), eq("u1"), eq("t1")))
                    .thenReturn(List.of(Map.of("id", "d1", "device_token", "stale", "platform", "ios")));
            when(jdbcTemplate.queryForList(contains("tenant WHERE id"), eq("t1")))
                    .thenReturn(List.of());

            doThrow(new PushDeliveryException("Invalid token", true))
                    .when(pushProvider).send(any(), any());

            int delivered = service.sendToUser("u1", "t1", "Title", "Body", null);

            assertThat(delivered).isEqualTo(0);
            verify(jdbcTemplate).update(contains("DELETE FROM push_device WHERE id"), eq("d1"));
        }

        @Test
        void shouldLoadTenantPushSettings() {
            when(jdbcTemplate.queryForList(contains("push_device WHERE user_id"), eq("u1"), eq("t1")))
                    .thenReturn(List.of(Map.of("id", "d1", "device_token", "tok1", "platform", "ios")));
            when(jdbcTemplate.queryForList(contains("tenant WHERE id"), eq("t1")))
                    .thenReturn(List.of(Map.of("settings",
                            "{\"push\":{\"fcm\":{\"projectId\":\"tenant-proj\",\"clientEmail\":\"sa@tenant.iam\",\"privateKey\":\"pk\"}}}")));

            service.sendToUser("u1", "t1", "Title", "Body", null);

            verify(pushProvider).send(any(), argThat(settings ->
                    settings != null && "tenant-proj".equals(settings.fcmProjectId())));
        }

        @Test
        void shouldHandleMissingTenantSettings() {
            when(jdbcTemplate.queryForList(contains("push_device WHERE tenant_id"), eq("t1")))
                    .thenReturn(List.of(Map.of("id", "d1", "device_token", "tok1", "platform", "web")));
            when(jdbcTemplate.queryForList(contains("tenant WHERE id"), eq("t1")))
                    .thenReturn(List.of(Map.of("settings", "{}")));

            int delivered = service.sendToTenant("t1", "Title", "Body", null);

            assertThat(delivered).isEqualTo(1);
            verify(pushProvider).send(any(), isNull());
        }
    }
}
