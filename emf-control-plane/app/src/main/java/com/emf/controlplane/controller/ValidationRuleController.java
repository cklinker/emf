package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateValidationRuleRequest;
import com.emf.controlplane.dto.UpdateValidationRuleRequest;
import com.emf.controlplane.dto.ValidationRuleDto;
import com.emf.controlplane.entity.ValidationRule;
import com.emf.controlplane.service.ValidationRuleService;
import com.emf.runtime.validation.ValidationError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/control/collections/{collectionId}/validation-rules")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Validation Rules", description = "Validation rule management APIs")
public class ValidationRuleController {

    private static final Logger log = LoggerFactory.getLogger(ValidationRuleController.class);

    private final ValidationRuleService validationRuleService;

    public ValidationRuleController(ValidationRuleService validationRuleService) {
        this.validationRuleService = validationRuleService;
    }

    @GetMapping
    @Operation(summary = "List validation rules for a collection")
    public ResponseEntity<List<ValidationRuleDto>> listRules(
            @PathVariable String collectionId) {
        List<ValidationRule> rules = validationRuleService.listRules(collectionId);
        List<ValidationRuleDto> dtos = rules.stream()
                .map(ValidationRuleDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Create a validation rule")
    public ResponseEntity<ValidationRuleDto> createRule(
            @PathVariable String collectionId,
            @RequestParam(defaultValue = "default") String tenantId,
            @Valid @RequestBody CreateValidationRuleRequest request) {
        log.info("REST request to create validation rule: {}", request.getName());
        ValidationRule created = validationRuleService.createRule(collectionId, tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ValidationRuleDto.fromEntity(created));
    }

    @GetMapping("/{ruleId}")
    @Operation(summary = "Get a validation rule")
    public ResponseEntity<ValidationRuleDto> getRule(
            @PathVariable String collectionId,
            @PathVariable String ruleId) {
        ValidationRule rule = validationRuleService.getRule(ruleId);
        return ResponseEntity.ok(ValidationRuleDto.fromEntity(rule));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Update a validation rule")
    public ResponseEntity<ValidationRuleDto> updateRule(
            @PathVariable String collectionId,
            @PathVariable String ruleId,
            @Valid @RequestBody UpdateValidationRuleRequest request) {
        log.info("REST request to update validation rule: {}", ruleId);
        ValidationRule updated = validationRuleService.updateRule(ruleId, request);
        return ResponseEntity.ok(ValidationRuleDto.fromEntity(updated));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Delete a validation rule")
    public ResponseEntity<Void> deleteRule(
            @PathVariable String collectionId,
            @PathVariable String ruleId) {
        log.info("REST request to delete validation rule: {}", ruleId);
        validationRuleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ruleId}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Activate a validation rule")
    public ResponseEntity<Void> activateRule(
            @PathVariable String collectionId,
            @PathVariable String ruleId) {
        log.info("REST request to activate validation rule: {}", ruleId);
        validationRuleService.activateRule(ruleId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ruleId}/deactivate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Deactivate a validation rule")
    public ResponseEntity<Void> deactivateRule(
            @PathVariable String collectionId,
            @PathVariable String ruleId) {
        log.info("REST request to deactivate validation rule: {}", ruleId);
        validationRuleService.deactivateRule(ruleId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Test validation rules against a sample record")
    public ResponseEntity<List<ValidationError>> testRules(
            @PathVariable String collectionId,
            @RequestBody Map<String, Object> testRecord) {
        log.info("REST request to test validation rules for collection: {}", collectionId);
        List<ValidationError> errors = validationRuleService.testRules(collectionId, testRecord);
        return ResponseEntity.ok(errors);
    }
}
