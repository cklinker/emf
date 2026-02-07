package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateWebhookRequest;
import com.emf.controlplane.entity.Webhook;
import com.emf.controlplane.entity.WebhookDelivery;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.WebhookDeliveryRepository;
import com.emf.controlplane.repository.WebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;

    public WebhookService(WebhookRepository webhookRepository,
                          WebhookDeliveryRepository deliveryRepository) {
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public List<Webhook> listWebhooks(String tenantId) {
        return webhookRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Webhook getWebhook(String id) {
        return webhookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook", id));
    }

    @Transactional
    @SetupAudited(section = "Webhooks", entityType = "Webhook")
    public Webhook createWebhook(String tenantId, String userId, CreateWebhookRequest request) {
        log.info("Creating webhook '{}' for tenant: {}", request.getName(), tenantId);

        Webhook webhook = new Webhook();
        webhook.setTenantId(tenantId);
        webhook.setName(request.getName());
        webhook.setUrl(request.getUrl());
        webhook.setEvents(request.getEvents());
        webhook.setCollectionId(request.getCollectionId());
        webhook.setFilterFormula(request.getFilterFormula());
        webhook.setHeaders(request.getHeaders());
        webhook.setSecret(request.getSecret());
        webhook.setActive(request.getActive() != null ? request.getActive() : true);
        webhook.setRetryPolicy(request.getRetryPolicy());
        webhook.setCreatedBy(userId);

        return webhookRepository.save(webhook);
    }

    @Transactional
    @SetupAudited(section = "Webhooks", entityType = "Webhook")
    public Webhook updateWebhook(String id, CreateWebhookRequest request) {
        log.info("Updating webhook: {}", id);
        Webhook webhook = getWebhook(id);

        if (request.getName() != null) webhook.setName(request.getName());
        if (request.getUrl() != null) webhook.setUrl(request.getUrl());
        if (request.getEvents() != null) webhook.setEvents(request.getEvents());
        if (request.getCollectionId() != null) webhook.setCollectionId(request.getCollectionId());
        if (request.getFilterFormula() != null) webhook.setFilterFormula(request.getFilterFormula());
        if (request.getHeaders() != null) webhook.setHeaders(request.getHeaders());
        if (request.getSecret() != null) webhook.setSecret(request.getSecret());
        if (request.getActive() != null) webhook.setActive(request.getActive());
        if (request.getRetryPolicy() != null) webhook.setRetryPolicy(request.getRetryPolicy());

        return webhookRepository.save(webhook);
    }

    @Transactional
    @SetupAudited(section = "Webhooks", entityType = "Webhook")
    public void deleteWebhook(String id) {
        log.info("Deleting webhook: {}", id);
        Webhook webhook = getWebhook(id);
        webhookRepository.delete(webhook);
    }

    // --- Deliveries ---

    @Transactional(readOnly = true)
    public List<WebhookDelivery> listDeliveriesByWebhook(String webhookId) {
        return deliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId);
    }
}
