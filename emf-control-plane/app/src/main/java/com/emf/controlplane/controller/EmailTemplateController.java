package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateEmailTemplateRequest;
import com.emf.controlplane.dto.EmailLogDto;
import com.emf.controlplane.dto.EmailTemplateDto;
import com.emf.controlplane.service.EmailTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/email-templates")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    public EmailTemplateController(EmailTemplateService emailTemplateService) {
        this.emailTemplateService = emailTemplateService;
    }

    @GetMapping
    public List<EmailTemplateDto> listTemplates(@RequestParam String tenantId) {
        return emailTemplateService.listTemplates(tenantId).stream()
                .map(EmailTemplateDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public EmailTemplateDto getTemplate(@PathVariable String id) {
        return EmailTemplateDto.fromEntity(emailTemplateService.getTemplate(id));
    }

    @PostMapping
    public ResponseEntity<EmailTemplateDto> createTemplate(
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody CreateEmailTemplateRequest request) {
        var template = emailTemplateService.createTemplate(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(EmailTemplateDto.fromEntity(template));
    }

    @PutMapping("/{id}")
    public EmailTemplateDto updateTemplate(
            @PathVariable String id,
            @RequestBody CreateEmailTemplateRequest request) {
        return EmailTemplateDto.fromEntity(emailTemplateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        emailTemplateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    // --- Email Logs ---

    @GetMapping("/logs")
    public List<EmailLogDto> listLogs(
            @RequestParam String tenantId,
            @RequestParam(required = false) String status) {
        if (status != null) {
            return emailTemplateService.listLogsByStatus(tenantId, status).stream()
                    .map(EmailLogDto::fromEntity).toList();
        }
        return emailTemplateService.listLogs(tenantId).stream()
                .map(EmailLogDto::fromEntity).toList();
    }
}
