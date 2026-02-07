package com.emf.controlplane.controller;

import com.emf.controlplane.dto.ConnectedAppCreatedResponse;
import com.emf.controlplane.dto.ConnectedAppDto;
import com.emf.controlplane.dto.ConnectedAppTokenDto;
import com.emf.controlplane.dto.CreateConnectedAppRequest;
import com.emf.controlplane.service.ConnectedAppService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/connected-apps")
public class ConnectedAppController {

    private final ConnectedAppService connectedAppService;

    public ConnectedAppController(ConnectedAppService connectedAppService) {
        this.connectedAppService = connectedAppService;
    }

    @GetMapping
    public List<ConnectedAppDto> listApps(@RequestParam String tenantId) {
        return connectedAppService.listApps(tenantId).stream()
                .map(ConnectedAppDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public ConnectedAppDto getApp(@PathVariable String id) {
        return ConnectedAppDto.fromEntity(connectedAppService.getApp(id));
    }

    @PostMapping
    public ResponseEntity<ConnectedAppCreatedResponse> createApp(
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody CreateConnectedAppRequest request) {
        var result = connectedAppService.createApp(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConnectedAppCreatedResponse.fromEntity(result.getApp(), result.getPlaintextSecret()));
    }

    @PutMapping("/{id}")
    public ConnectedAppDto updateApp(
            @PathVariable String id,
            @RequestBody CreateConnectedAppRequest request) {
        return ConnectedAppDto.fromEntity(connectedAppService.updateApp(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApp(@PathVariable String id) {
        connectedAppService.deleteApp(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<ConnectedAppCreatedResponse> rotateSecret(@PathVariable String id) {
        var result = connectedAppService.rotateSecret(id);
        return ResponseEntity.ok(ConnectedAppCreatedResponse.fromEntity(result.getApp(), result.getPlaintextSecret()));
    }

    @GetMapping("/{id}/tokens")
    public List<ConnectedAppTokenDto> listTokens(@PathVariable String id) {
        return connectedAppService.listTokens(id).stream()
                .map(ConnectedAppTokenDto::fromEntity).toList();
    }
}
