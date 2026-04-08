package io.kelta.worker.service.push;

import io.kelta.worker.repository.PushRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultPushService Tests")
class DefaultPushServiceTest {

    @Mock private PushProvider pushProvider;
    @Mock private PushRepository pushRepository;

    private DefaultPushService service;

    @BeforeEach
    void setUp() {
        service = new DefaultPushService(pushProvider, pushRepository);
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
            when(pushRepository.findDeviceIdByToken("t1", "valid-token")).thenReturn(Optional.empty());
            when(pushRepository.insertDevice("u1", "t1", "ios", "valid-token", "iPhone"))
                    .thenReturn("new-device-id");

            String id = service.registerDevice("u1", "t1", "ios", "valid-token", "iPhone");
            assertThat(id).isEqualTo("new-device-id");
            verify(pushRepository).insertDevice("u1", "t1", "ios", "valid-token", "iPhone");
        }

        @Test
        void shouldUpdateExistingDevice() {
            when(pushRepository.findDeviceIdByToken("t1", "existing-token"))
                    .thenReturn(Optional.of("device-1"));

            String id = service.registerDevice("u1", "t1", "android", "existing-token", "Pixel");
            assertThat(id).isEqualTo("device-1");
            verify(pushRepository).updateDevice("device-1", "u1", "android", "Pixel");
        }
    }

    @Nested
    @DisplayName("Send Notifications")
    class SendNotifications {
        @Test
        void shouldSendToAllUserDevices() {
            when(pushRepository.findDevicesForUser("u1", "t1")).thenReturn(List.of(
                    Map.of("id", "d1", "device_token", "tok1", "platform", "ios"),
                    Map.of("id", "d2", "device_token", "tok2", "platform", "android")
            ));
            when(pushRepository.getTenantSettings("t1")).thenReturn(null);

            int delivered = service.sendToUser("u1", "t1", "Title", "Body", null);

            assertThat(delivered).isEqualTo(2);
            verify(pushProvider, times(2)).send(any(), isNull());
        }

        @Test
        void shouldRemoveStaleTokenOnInvalidTokenError() {
            when(pushRepository.findDevicesForUser("u1", "t1")).thenReturn(
                    List.of(Map.of("id", "d1", "device_token", "stale", "platform", "ios")));
            when(pushRepository.getTenantSettings("t1")).thenReturn(null);

            doThrow(new PushDeliveryException("Invalid token", true))
                    .when(pushProvider).send(any(), any());

            int delivered = service.sendToUser("u1", "t1", "Title", "Body", null);

            assertThat(delivered).isEqualTo(0);
            verify(pushRepository).deleteDeviceById("d1");
        }

        @Test
        void shouldLoadTenantPushSettings() throws Exception {
            when(pushRepository.findDevicesForUser("u1", "t1")).thenReturn(
                    List.of(Map.of("id", "d1", "device_token", "tok1", "platform", "ios")));
            var mapper = new ObjectMapper();
            var settingsNode = mapper.readTree(
                    "{\"push\":{\"fcm\":{\"projectId\":\"tenant-proj\",\"clientEmail\":\"sa@tenant.iam\",\"privateKey\":\"pk\"}}}");
            when(pushRepository.getTenantSettings("t1")).thenReturn(settingsNode);

            service.sendToUser("u1", "t1", "Title", "Body", null);

            verify(pushProvider).send(any(), argThat(settings ->
                    settings != null && "tenant-proj".equals(settings.fcmProjectId())));
        }

        @Test
        void shouldHandleMissingTenantSettings() {
            when(pushRepository.findDevicesForTenant("t1")).thenReturn(
                    List.of(Map.of("id", "d1", "device_token", "tok1", "platform", "web")));
            when(pushRepository.getTenantSettings("t1")).thenReturn(null);

            int delivered = service.sendToTenant("t1", "Title", "Body", null);

            assertThat(delivered).isEqualTo(1);
            verify(pushProvider).send(any(), isNull());
        }
    }
}
