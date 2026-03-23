package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalAccessTokenController Tests")
class PersonalAccessTokenControllerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private PersonalAccessTokenController controller;

    @BeforeEach
    void setUp() {
        controller = new PersonalAccessTokenController(jdbcTemplate, redisTemplate);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("GET /api/me/tokens")
    class ListTokens {
        @Test
        void shouldListUserTokens() {
            when(jdbcTemplate.queryForList(contains("user_api_token"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of(
                            Map.of("id", "tok-1", "name", "Test Token", "token_prefix", "klt_ABCD")));

            var response = controller.listTokens("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void shouldReturn400WhenNoUserContext() {
            var response = controller.listTokens(null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturn400WhenNoTenantContext() {
            TenantContext.clear();

            var response = controller.listTokens("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("POST /api/me/tokens")
    class CreateToken {
        @Test
        void shouldCreateTokenSuccessfully() {
            when(jdbcTemplate.queryForList(contains("platform_user"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("email", "user@test.com")));
            when(jdbcTemplate.queryForList(contains("COUNT"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("cnt", 0L)));

            var response = controller.createToken("user-1",
                    Map.of("name", "My Token", "expiresInDays", 90));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("token");
            String token = (String) body.get("token");
            assertThat(token).startsWith("klt_");
            assertThat(token).hasSize(44); // "klt_" + 40 chars
        }

        @Test
        void shouldReturn400WhenNameMissing() {
            var response = controller.createToken("user-1", Map.of());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturn400WhenExpirationTooLong() {
            var response = controller.createToken("user-1",
                    Map.of("name", "Token", "expiresInDays", 999));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturn400WhenMaxTokensReached() {
            when(jdbcTemplate.queryForList(contains("platform_user"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("email", "user@test.com")));
            when(jdbcTemplate.queryForList(contains("COUNT"), eq("user-1"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("cnt", 10L)));

            var response = controller.createToken("user-1",
                    Map.of("name", "Token", "expiresInDays", 90));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("DELETE /api/me/tokens/{tokenId}")
    class RevokeToken {
        @Test
        void shouldRevokeTokenSuccessfully() {
            when(jdbcTemplate.queryForList(contains("user_api_token WHERE id"), eq("tok-1"), eq("user-1")))
                    .thenReturn(List.of(Map.of("id", "tok-1", "token_hash", "abc123")));
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            var response = controller.revokeToken("tok-1", "user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jdbcTemplate).update(contains("revoked = true"), any(), any(), eq("tok-1"));
        }

        @Test
        void shouldReturn404ForNonExistentToken() {
            when(jdbcTemplate.queryForList(contains("user_api_token WHERE id"), eq("tok-1"), eq("user-1")))
                    .thenReturn(List.of());

            var response = controller.revokeToken("tok-1", "user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldReturn400WhenNoUserContext() {
            var response = controller.revokeToken("tok-1", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void sha256ShouldProduceConsistentHash() {
        String hash1 = PersonalAccessTokenController.sha256("test_token");
        String hash2 = PersonalAccessTokenController.sha256("test_token");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex chars
    }
}
