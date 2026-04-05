package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.MetadataPromotionService;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PromotionController")
class PromotionControllerTest {

    private MetadataPromotionService promotionService;
    private PromotionController controller;

    @BeforeEach
    void setUp() {
        promotionService = mock(MetadataPromotionService.class);
        controller = new PromotionController(promotionService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("listPromotions should return 400 without tenant context")
    void listShouldRejectNoTenant() {
        var response = controller.listPromotions(50, 0);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("listPromotions should return promotions")
    void listShouldReturnPromotions() {
        TenantContext.set("t1");
        when(promotionService.listPromotions("t1", 50, 0))
                .thenReturn(List.of(Map.of("id", "promo-1")));

        var response = controller.listPromotions(50, 0);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("getPromotion should return 404 when not found")
    void getShouldReturn404() {
        TenantContext.set("t1");
        when(promotionService.getPromotion("promo-bad", "t1")).thenReturn(Optional.empty());

        var response = controller.getPromotion("promo-bad");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("createPromotion should require both environment IDs")
    void createShouldRequireBothEnvIds() {
        TenantContext.set("t1");
        var body = new HashMap<String, Object>();
        body.put("sourceEnvironmentId", "env-1");

        var response = controller.createPromotion(body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("createPromotion should create promotion successfully")
    void createShouldSucceed() {
        TenantContext.set("t1");
        when(promotionService.createPromotion(eq("t1"), eq("env-1"), eq("env-2"), eq("FULL"), isNull(), isNull()))
                .thenReturn(Map.of("id", "promo-1", "status", "PENDING"));

        var body = new HashMap<String, Object>();
        body.put("sourceEnvironmentId", "env-1");
        body.put("targetEnvironmentId", "env-2");

        var response = controller.createPromotion(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("approvePromotion should return error for invalid promotion")
    void approveShouldHandleError() {
        TenantContext.set("t1");
        when(promotionService.approvePromotion("promo-bad", "t1", "manager"))
                .thenThrow(new IllegalArgumentException("Promotion not found"));

        var body = new HashMap<String, Object>();
        body.put("approvedBy", "manager");

        var response = controller.approvePromotion("promo-bad", body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("executePromotion should return 202 accepted")
    void executeShouldReturn202() {
        TenantContext.set("t1");

        var response = controller.executePromotion("promo-1");

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(promotionService).executePromotion("promo-1", "t1");
    }

    @Test
    @DisplayName("rollbackPromotion should return error for invalid promotion")
    void rollbackShouldHandleError() {
        TenantContext.set("t1");
        when(promotionService.rollbackPromotion("promo-bad", "t1"))
                .thenThrow(new IllegalArgumentException("Promotion not found"));

        var response = controller.rollbackPromotion("promo-bad");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("previewPromotion should require both environment IDs")
    void previewShouldRequireBothEnvIds() {
        TenantContext.set("t1");
        var body = new HashMap<String, Object>();
        body.put("sourceEnvironmentId", "env-1");

        var response = controller.previewPromotion(body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("getPromotionItems should return 404 for invalid promotion")
    void getItemsShouldReturn404() {
        TenantContext.set("t1");
        when(promotionService.getPromotion("promo-bad", "t1")).thenReturn(Optional.empty());

        var response = controller.getPromotionItems("promo-bad");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
