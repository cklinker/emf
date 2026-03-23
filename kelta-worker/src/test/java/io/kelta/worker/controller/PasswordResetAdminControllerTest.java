package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetAdminController Tests")
class PasswordResetAdminControllerTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private PasswordResetAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new PasswordResetAdminController(jdbcTemplate);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("POST /{userId}/reset-password")
    class ResetPassword {
        @Test
        void shouldSetForceChangeOnLoginFlag() {
            when(jdbcTemplate.queryForList(contains("platform_user"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("id", "user-1", "email", "user@test.com")));
            when(jdbcTemplate.update(contains("UPDATE user_credential"), any(), eq("user-1")))
                    .thenReturn(1);

            var response = controller.resetPassword("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jdbcTemplate).update(contains("force_change_on_login = true"), any(), eq("user-1"));
        }

        @Test
        void shouldReturn404ForCrossTenantUser() {
            when(jdbcTemplate.queryForList(contains("platform_user"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of());

            var response = controller.resetPassword("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldReturn400WhenNoTenantContext() {
            TenantContext.clear();

            var response = controller.resetPassword("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturn404WhenNoCredentialRecord() {
            when(jdbcTemplate.queryForList(contains("platform_user"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("id", "user-1", "email", "user@test.com")));
            when(jdbcTemplate.update(contains("UPDATE user_credential"), any(), eq("user-1")))
                    .thenReturn(0);

            var response = controller.resetPassword("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
