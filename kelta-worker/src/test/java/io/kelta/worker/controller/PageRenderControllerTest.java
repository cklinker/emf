package io.kelta.worker.controller;

import io.kelta.worker.service.PageRenderContract;
import io.kelta.worker.service.PageRenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PageRenderController")
class PageRenderControllerTest {

    @Mock
    private PageRenderService pageRenderService;

    private PageRenderController controller() {
        return new PageRenderController(pageRenderService);
    }

    @Test
    @DisplayName("returns 200 with the contract for a published page")
    void rendersPage() {
        PageRenderContract contract = new PageRenderContract("1.0", "home", "Home", "/home",
                Map.of("components", java.util.List.of()));
        when(pageRenderService.render("home")).thenReturn(Optional.of(contract));

        ResponseEntity<PageRenderContract> response = controller().render("home");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().slug()).isEqualTo("home");
    }

    @Test
    @DisplayName("returns 404 for an unknown or unpublished slug")
    void notFound() {
        when(pageRenderService.render("missing")).thenReturn(Optional.empty());

        ResponseEntity<PageRenderContract> response = controller().render("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
