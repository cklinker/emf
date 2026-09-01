package io.kelta.worker.controller;

import io.kelta.worker.service.S3StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/files/**} and {@code /api/images/**} were both unreachable in production.
 *
 * <p>{@code DynamicCollectionRouter} maps all-variable GETs up to four segments under {@code /api},
 * and Spring's {@code PathPattern} specificity comparator ranks any pattern containing {@code **}
 * last — so both controllers lost every request to it and it answered 404 as a record read of a
 * collection named "files"/"images". Both endpoints are documented as working capabilities in
 * {@code status.md} and are routed by the gateway, so nothing surfaced the breakage.
 *
 * <p>The competing router mapping is registered here deliberately: with either controller alone the
 * assertions pass even against the broken mapping, which is exactly how this shipped.
 */
@DisplayName("Static asset route mappings vs the generic collection router")
class StaticAssetRouteMappingTest {

    /** Stands in for {@code DynamicCollectionRouter}'s nested GET mappings, shape for shape. */
    @RestController
    @RequestMapping("/api")
    static class NestedRouteStub {
        @GetMapping("/{collectionName}/{id}")
        String two() {
            return "router";
        }

        @GetMapping("/{parentName}/{parentId}/{childName}")
        String three() {
            return "router";
        }

        @GetMapping("/{parentName}/{parentId}/{childName}/{childId}")
        String four() {
            return "router";
        }
    }

    private MockMvc mvcWith(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller, new NestedRouteStub()).build();
    }

    @Test
    @DisplayName("A file key reaches FileController rather than the collection router")
    void fileControllerWinsAtEveryDepthTheRouterClaims() throws Exception {
        MockMvc mvc = mvcWith(new FileController(mock(S3StorageService.class)));

        // Unauthenticated (no X-User-Email) is FileController's own 401. The router would answer
        // 200 "router" — so this distinguishes which handler ran without needing working storage.
        for (String key : new String[]{"a", "a/b", "a/b/c"}) {
            mvc.perform(get("/api/files/" + key))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(""));
        }
    }

    @Test
    @DisplayName("An image key reaches ImageController rather than the collection router")
    void imageControllerWinsAtEveryDepthTheRouterClaims() throws Exception {
        MockMvc mvc = mvcWith(new ImageController(mock(S3StorageService.class),
                mock(io.kelta.worker.service.ImageTransformService.class)));

        for (String key : new String[]{"a", "a/b", "a/b/c"}) {
            mvc.perform(get("/api/images/" + key))
                    .andExpect(content().string(org.hamcrest.Matchers.not("router")));
        }
    }
}
