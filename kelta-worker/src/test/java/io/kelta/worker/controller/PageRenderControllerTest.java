package io.kelta.worker.controller;

import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.PageRenderContract;
import io.kelta.worker.service.PageRenderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PageRenderController")
class PageRenderControllerTest {

    @Mock
    private PageRenderService pageRenderService;

    @Mock
    private CerbosPermissionResolver permissionResolver;

    private PageRenderController controller() {
        return new PageRenderController(pageRenderService, permissionResolver);
    }

    private HttpServletRequest request(String profileId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (profileId != null) {
            req.addHeader("X-User-Profile-Id", profileId);
        }
        return req;
    }

    @Test
    @DisplayName("returns 200 with the contract for a published page, passing the resolved profile id")
    void rendersPage() {
        PageRenderContract contract = new PageRenderContract("2.0", "home", "Home", "/home",
                java.util.List.of(), java.util.List.of(),
                Map.of("components", java.util.List.of()));
        when(permissionResolver.getProfileId(any())).thenReturn("p-1");
        when(pageRenderService.render(eq("home"), eq("p-1"))).thenReturn(Optional.of(contract));

        ResponseEntity<PageRenderContract> response = controller().render("home", request("p-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().slug()).isEqualTo("home");
    }

    @Test
    @DisplayName("returns 404 when the service denies (unknown slug, or per-page permission denial)")
    void notFound() {
        when(pageRenderService.render(eq("missing"), any())).thenReturn(Optional.empty());

        ResponseEntity<PageRenderContract> response = controller().render("missing", request("p-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
