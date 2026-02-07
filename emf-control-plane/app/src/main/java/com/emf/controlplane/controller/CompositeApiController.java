package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CompositeRequest;
import com.emf.controlplane.dto.CompositeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/control/composite")
public class CompositeApiController {

    private static final Logger log = LoggerFactory.getLogger(CompositeApiController.class);

    @PostMapping
    public ResponseEntity<CompositeResponse> executeComposite(
            @RequestParam String tenantId,
            @RequestBody CompositeRequest request) {
        log.info("Executing composite request with {} sub-requests for tenant: {}",
                request.getRequests() != null ? request.getRequests().size() : 0, tenantId);

        // Stubbed response — echoes back sub-requests with 200 status and empty bodies.
        // The actual execution engine will be implemented in a later phase.
        List<CompositeResponse.SubResponse> subResponses = request.getRequests() != null
                ? request.getRequests().stream()
                    .map(sub -> new CompositeResponse.SubResponse(
                            sub.getReferenceId(),
                            200,
                            Collections.emptyMap()))
                    .toList()
                : List.of();

        return ResponseEntity.ok(new CompositeResponse(subResponses));
    }
}
