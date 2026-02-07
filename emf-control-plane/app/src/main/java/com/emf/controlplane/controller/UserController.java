package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateUserRequest;
import com.emf.controlplane.dto.LoginHistoryDto;
import com.emf.controlplane.dto.UpdateUserRequest;
import com.emf.controlplane.dto.UserDto;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user management operations.
 * All endpoints are tenant-scoped via TenantContextHolder.
 */
@RestController
@RequestMapping("/control/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Page<UserDto>> listUsers(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<User> users = userService.listUsers(filter, status, pageable);
        Page<UserDto> dtos = users.map(UserDto::fromEntity);
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.fromEntity(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable String id) {
        User user = userService.getUser(id);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.updateUser(id, request);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable String id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable String id) {
        userService.activateUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/login-history")
    public ResponseEntity<Page<LoginHistoryDto>> getLoginHistory(
            @PathVariable String id,
            @PageableDefault(size = 20) Pageable pageable) {
        var history = userService.getLoginHistory(id, pageable);
        var dtos = history.map(LoginHistoryDto::fromEntity);
        return ResponseEntity.ok(dtos);
    }
}
