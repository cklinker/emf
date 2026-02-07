package com.emf.controlplane.controller;

import com.emf.controlplane.dto.OidcProviderDto;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.service.OidcProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for OIDC provider lookups by the gateway.
 * No authentication required — only exposed on the internal network.
 */
@RestController
@RequestMapping("/internal/oidc")
public class InternalOidcController {

    private static final Logger log = LoggerFactory.getLogger(InternalOidcController.class);

    private final OidcProviderService oidcProviderService;

    public InternalOidcController(OidcProviderService oidcProviderService) {
        this.oidcProviderService = oidcProviderService;
    }

    @GetMapping("/by-issuer")
    public ResponseEntity<OidcProviderDto> getByIssuer(@RequestParam String issuer) {
        log.debug("Internal lookup: OIDC provider by issuer: {}", issuer);
        OidcProvider provider = oidcProviderService.getProviderByIssuer(issuer);
        return ResponseEntity.ok(OidcProviderDto.fromEntity(provider));
    }
}
