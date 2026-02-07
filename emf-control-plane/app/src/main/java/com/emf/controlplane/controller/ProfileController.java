package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.Profile;
import com.emf.controlplane.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for profile management operations.
 * All endpoints are tenant-scoped via TenantContextHolder.
 */
@RestController
@RequestMapping("/control/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<List<ProfileDto>> listProfiles() {
        List<Profile> profiles = profileService.listProfiles();
        List<ProfileDto> dtos = profiles.stream()
                .map(ProfileDto::fromEntitySummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<ProfileDto> createProfile(@Valid @RequestBody CreateProfileRequest request) {
        Profile profile = profileService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileDto.fromEntity(profile));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileDto> getProfile(@PathVariable String id) {
        Profile profile = profileService.getProfile(id);
        return ResponseEntity.ok(ProfileDto.fromEntity(profile));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileDto> updateProfile(
            @PathVariable String id,
            @Valid @RequestBody UpdateProfileRequest request) {
        Profile profile = profileService.updateProfile(id, request);
        return ResponseEntity.ok(ProfileDto.fromEntitySummary(profile));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String id) {
        profileService.deleteProfile(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/object-permissions/{collectionId}")
    public ResponseEntity<Void> setObjectPermissions(
            @PathVariable String id,
            @PathVariable String collectionId,
            @Valid @RequestBody ObjectPermissionRequest request) {
        profileService.setObjectPermissions(id, collectionId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/field-permissions")
    public ResponseEntity<Void> setFieldPermissions(
            @PathVariable String id,
            @Valid @RequestBody List<FieldPermissionRequest> requests) {
        profileService.setFieldPermissions(id, requests);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/system-permissions")
    public ResponseEntity<Void> setSystemPermissions(
            @PathVariable String id,
            @Valid @RequestBody List<SystemPermissionRequest> requests) {
        profileService.setSystemPermissions(id, requests);
        return ResponseEntity.noContent().build();
    }
}
