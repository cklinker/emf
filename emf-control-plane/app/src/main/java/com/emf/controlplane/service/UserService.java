package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateUserRequest;
import com.emf.controlplane.dto.UpdateUserRequest;
import com.emf.controlplane.entity.LoginHistory;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.LoginHistoryRepository;
import com.emf.controlplane.repository.UserRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for managing platform users within a tenant.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    public UserService(UserRepository userRepository,
                       LoginHistoryRepository loginHistoryRepository) {
        this.userRepository = userRepository;
        this.loginHistoryRepository = loginHistoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<User> listUsers(String filter, String status, Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing users for tenant: {} with filter: {}, status: {}", tenantId, filter, status);

        if (tenantId == null) {
            return userRepository.findAll(pageable);
        }

        boolean hasFilter = filter != null && !filter.isBlank();
        boolean hasStatus = status != null && !status.isBlank();

        if (hasFilter && hasStatus) {
            return userRepository.findByTenantIdAndStatusAndFilter(tenantId, status, filter.trim(), pageable);
        } else if (hasFilter) {
            return userRepository.findByTenantIdAndFilter(tenantId, filter.trim(), pageable);
        } else if (hasStatus) {
            return userRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            return userRepository.findByTenantId(tenantId, pageable);
        }
    }

    @Transactional
    public User createUser(CreateUserRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating user with email: {} for tenant: {}", request.getEmail(), tenantId);

        if (tenantId != null && userRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        User user = new User(request.getEmail(), request.getFirstName(), request.getLastName());
        if (tenantId != null) {
            user.setTenantId(tenantId);
        }
        user.setUsername(request.getUsername());
        user.setLocale(request.getLocale() != null ? request.getLocale() : "en_US");
        user.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
        user.setProfileId(request.getProfileId());
        user.setStatus("ACTIVE");

        user = userRepository.save(user);
        log.info("Created user {} in tenant {}", user.getId(), tenantId);
        return user;
    }

    @Transactional(readOnly = true)
    public User getUser(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return userRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", id));
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return userRepository.findByTenantIdAndEmail(tenantId, email).orElse(null);
        }
        return null;
    }

    @Transactional
    public User updateUser(String id, UpdateUserRequest request) {
        User user = getUser(id);

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getLocale() != null) user.setLocale(request.getLocale());
        if (request.getTimezone() != null) user.setTimezone(request.getTimezone());
        if (request.getProfileId() != null) user.setProfileId(request.getProfileId());

        if (request.getManagerId() != null) {
            String tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                userRepository.findByIdAndTenantId(request.getManagerId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Manager", request.getManagerId()));
            }
            user.setManagerId(request.getManagerId());
        }

        user = userRepository.save(user);
        log.info("Updated user {}", user.getId());
        return user;
    }

    @Transactional
    public void deactivateUser(String id) {
        User user = getUser(id);
        user.setStatus("INACTIVE");
        userRepository.save(user);
        log.info("Deactivated user {}", user.getId());
    }

    @Transactional
    public void activateUser(String id) {
        User user = getUser(id);
        user.setStatus("ACTIVE");
        userRepository.save(user);
        log.info("Activated user {}", user.getId());
    }

    @Transactional
    public void recordLogin(String userId, String tenantId, String sourceIp,
                            String loginType, String status, String userAgent) {
        LoginHistory history = new LoginHistory();
        history.setUserId(userId);
        history.setTenantId(tenantId);
        history.setLoginTime(Instant.now());
        history.setSourceIp(sourceIp);
        history.setLoginType(loginType);
        history.setStatus(status);
        history.setUserAgent(userAgent);
        loginHistoryRepository.save(history);

        if ("SUCCESS".equals(status)) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setLastLoginAt(Instant.now());
                user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0) + 1);
                userRepository.save(user);
            });
        }

        if ("FAILED".equals(status)) {
            long failedCount = loginHistoryRepository.countByUserIdAndStatusAndLoginTimeAfter(
                    userId, "FAILED", Instant.now().minus(LOCKOUT_WINDOW));
            if (failedCount >= MAX_FAILED_ATTEMPTS) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.setStatus("LOCKED");
                    userRepository.save(user);
                    log.warn("Locked user {} after {} failed login attempts", userId, failedCount);
                });
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<LoginHistory> getLoginHistory(String userId, Pageable pageable) {
        getUser(userId); // verify user exists in tenant
        return loginHistoryRepository.findByUserIdOrderByLoginTimeDesc(userId, pageable);
    }

    /**
     * Provision or update a user from OIDC JWT claims (JIT provisioning).
     */
    @Transactional
    public User provisionOrUpdate(String tenantId, String email, String firstName,
                                   String lastName, String username) {
        if (email == null || email.isBlank()) {
            throw new com.emf.controlplane.exception.ValidationException("Email is required for user provisioning");
        }

        var existing = userRepository.findByTenantIdAndEmail(tenantId, email);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setLastLoginAt(Instant.now());
            user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0) + 1);
            return userRepository.save(user);
        }

        User user = new User(email, firstName, lastName);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setStatus("ACTIVE");
        user = userRepository.save(user);
        log.info("JIT provisioned user {} ({}) in tenant {}", user.getId(), email, tenantId);
        return user;
    }
}
