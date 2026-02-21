package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateWebhookRequest;
import com.emf.controlplane.dto.WebhookDeliveryDto;
import com.emf.controlplane.dto.WebhookDto;
import com.emf.controlplane.service.WebhookService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/webhooks")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_CONNECTED_APPS')")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    public List<WebhookDto> listWebhooks() {
        String tenantId = TenantContextHolder.requireTenantId();
        return webhookService.listWebhooks(tenantId).stream()
                .map(WebhookDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public WebhookDto getWebhook(@PathVariable String id) {
        return WebhookDto.fromEntity(webhookService.getWebhook(id));
    }

    @PostMapping
    public ResponseEntity<WebhookDto> createWebhook(
            @RequestParam String userId,
            @RequestBody CreateWebhookRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var webhook = webhookService.createWebhook(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(WebhookDto.fromEntity(webhook));
    }

    @PutMapping("/{id}")
    public WebhookDto updateWebhook(
            @PathVariable String id,
            @RequestBody CreateWebhookRequest request) {
        return WebhookDto.fromEntity(webhookService.updateWebhook(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable String id) {
        webhookService.deleteWebhook(id);
        return ResponseEntity.noContent().build();
    }

    // --- Deliveries ---

    @GetMapping("/{id}/deliveries")
    public List<WebhookDeliveryDto> listDeliveriesByWebhook(@PathVariable String id) {
        return webhookService.listDeliveriesByWebhook(id).stream()
                .map(WebhookDeliveryDto::fromEntity).toList();
    }
}
