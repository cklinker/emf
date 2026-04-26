package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("SvixTenantService")
class SvixTenantServiceTest {

    private MockRestServiceServer server;
    private SvixTenantService service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder()
                .baseUrl("http://svix.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SvixTenantService(builder.build());
    }

    @Nested
    @DisplayName("ensureApplication")
    class EnsureApplication {

        @Test
        @DisplayName("POSTs to /api/v1/app/ with get_if_exists=true")
        void postsWithGetIfExistsFlag() {
            server.expect(requestTo("http://svix.test/api/v1/app/?get_if_exists=true"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                    .andExpect(jsonPath("$.uid").value("tenant-1"))
                    .andExpect(jsonPath("$.name").value("Acme Corp"))
                    .andRespond(withSuccess("{\"id\":\"app_xyz\"}", MediaType.APPLICATION_JSON));

            service.ensureApplication("tenant-1", "Acme Corp");

            server.verify();
        }

        @Test
        @DisplayName("falls back to tenant id when name is null")
        void fallsBackToIdWhenNameMissing() {
            server.expect(requestTo("http://svix.test/api/v1/app/?get_if_exists=true"))
                    .andExpect(jsonPath("$.name").value("tenant-1"))
                    .andRespond(withSuccess());

            service.ensureApplication("tenant-1", null);

            server.verify();
        }

        @Test
        @DisplayName("swallows Svix errors and does not throw")
        void swallowsErrors() {
            server.expect(requestTo("http://svix.test/api/v1/app/?get_if_exists=true"))
                    .andRespond(withServerError());

            service.ensureApplication("tenant-1", "Acme Corp");

            server.verify();
        }
    }

    @Nested
    @DisplayName("deleteApplication")
    class DeleteApplication {

        @Test
        @DisplayName("DELETEs /api/v1/app/{id}/")
        void deletesApplication() {
            server.expect(requestTo("http://svix.test/api/v1/app/tenant-1/"))
                    .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                    .andRespond(withNoContent());

            service.deleteApplication("tenant-1");

            server.verify();
        }

        @Test
        @DisplayName("swallows Svix errors and does not throw")
        void swallowsErrors() {
            server.expect(requestTo("http://svix.test/api/v1/app/tenant-1/"))
                    .andRespond(withServerError());

            service.deleteApplication("tenant-1");

            server.verify();
        }
    }

    @Nested
    @DisplayName("getPortalAccess")
    class GetPortalAccess {

        @Test
        @DisplayName("returns the token and url from Svix")
        void returnsToken() {
            server.expect(requestTo("http://svix.test/api/v1/auth/app-portal-access/tenant-1/"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andRespond(withSuccess(
                            "{\"token\":\"portal-token\",\"url\":\"https://svix.test/portal\"}",
                            MediaType.APPLICATION_JSON));

            var access = service.getPortalAccess("tenant-1");

            assertThat(access.token()).isEqualTo("portal-token");
            assertThat(access.url()).isEqualTo("https://svix.test/portal");
        }

        @Test
        @DisplayName("propagates upstream HTTP errors so the caller can map them")
        void propagatesHttpErrors() {
            server.expect(requestTo("http://svix.test/api/v1/auth/app-portal-access/tenant-1/"))
                    .andRespond(withUnauthorizedRequest());

            assertThatThrownBy(() -> service.getPortalAccess("tenant-1"))
                    .isInstanceOf(org.springframework.web.client.RestClientResponseException.class);
        }
    }
}
