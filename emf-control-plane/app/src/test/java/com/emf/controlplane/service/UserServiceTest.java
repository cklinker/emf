package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateUserRequest;
import com.emf.controlplane.dto.UpdateUserRequest;
import com.emf.controlplane.entity.LoginHistory;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.LoginHistoryRepository;
import com.emf.controlplane.repository.UserRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String TENANT_ID = "test-tenant-id";

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, loginHistoryRepository, null, null);
        TenantContextHolder.set(TENANT_ID, "test-tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private User testUser(String email) {
        User user = new User(email, "Test", "User");
        user.setTenantId(TENANT_ID);
        user.setStatus("ACTIVE");
        return user;
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsersTests {

        @Test
        @DisplayName("should list users for tenant without filter")
        void shouldListUsersWithoutFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(testUser("a@b.com")), pageable, 1);
            when(userRepository.findByTenantId(TENANT_ID, pageable)).thenReturn(page);

            Page<User> result = userService.listUsers(null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(userRepository).findByTenantId(TENANT_ID, pageable);
        }

        @Test
        @DisplayName("should list users with filter")
        void shouldListUsersWithFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(), pageable, 0);
            when(userRepository.findByTenantIdAndFilter(TENANT_ID, "alice", pageable)).thenReturn(page);

            userService.listUsers("alice", null, pageable);

            verify(userRepository).findByTenantIdAndFilter(TENANT_ID, "alice", pageable);
        }

        @Test
        @DisplayName("should list users with status filter")
        void shouldListUsersWithStatusFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(), pageable, 0);
            when(userRepository.findByTenantIdAndStatus(TENANT_ID, "ACTIVE", pageable)).thenReturn(page);

            userService.listUsers(null, "ACTIVE", pageable);

            verify(userRepository).findByTenantIdAndStatus(TENANT_ID, "ACTIVE", pageable);
        }

        @Test
        @DisplayName("should list users with both filter and status")
        void shouldListUsersWithFilterAndStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(), pageable, 0);
            when(userRepository.findByTenantIdAndStatusAndFilter(TENANT_ID, "ACTIVE", "alice", pageable))
                    .thenReturn(page);

            userService.listUsers("alice", "ACTIVE", pageable);

            verify(userRepository).findByTenantIdAndStatusAndFilter(TENANT_ID, "ACTIVE", "alice", pageable);
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUserTests {

        @Test
        @DisplayName("should create user successfully")
        void shouldCreateUser() {
            CreateUserRequest request = new CreateUserRequest("new@example.com", "New", "User");
            when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "new@example.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createUser(request);

            assertThat(result.getEmail()).isEqualTo("new@example.com");
            assertThat(result.getFirstName()).isEqualTo("New");
            assertThat(result.getLastName()).isEqualTo("User");
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should throw DuplicateResourceException for existing email")
        void shouldThrowForDuplicateEmail() {
            CreateUserRequest request = new CreateUserRequest("existing@example.com", "Dup", "User");
            when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should use default locale and timezone")
        void shouldUseDefaults() {
            CreateUserRequest request = new CreateUserRequest("new@example.com", "New", "User");
            when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "new@example.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createUser(request);

            assertThat(result.getLocale()).isEqualTo("en_US");
            assertThat(result.getTimezone()).isEqualTo("UTC");
        }
    }

    @Nested
    @DisplayName("getUser")
    class GetUserTests {

        @Test
        @DisplayName("should return user by id and tenant")
        void shouldReturnUser() {
            User user = testUser("alice@example.com");
            when(userRepository.findByIdAndTenantId(user.getId(), TENANT_ID))
                    .thenReturn(Optional.of(user));

            User result = userService.getUser(user.getId());

            assertThat(result.getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for missing user")
        void shouldThrowForMissingUser() {
            when(userRepository.findByIdAndTenantId("missing", TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUserTests {

        @Test
        @DisplayName("should update user fields")
        void shouldUpdateUserFields() {
            User user = testUser("alice@example.com");
            when(userRepository.findByIdAndTenantId(user.getId(), TENANT_ID))
                    .thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateUserRequest request = new UpdateUserRequest();
            request.setFirstName("Updated");
            request.setLocale("fr_FR");

            User result = userService.updateUser(user.getId(), request);

            assertThat(result.getFirstName()).isEqualTo("Updated");
            assertThat(result.getLocale()).isEqualTo("fr_FR");
        }

        @Test
        @DisplayName("should validate manager exists in same tenant")
        void shouldValidateManagerExists() {
            User user = testUser("alice@example.com");
            when(userRepository.findByIdAndTenantId(user.getId(), TENANT_ID))
                    .thenReturn(Optional.of(user));
            when(userRepository.findByIdAndTenantId("bad-manager", TENANT_ID))
                    .thenReturn(Optional.empty());

            UpdateUserRequest request = new UpdateUserRequest();
            request.setManagerId("bad-manager");

            assertThatThrownBy(() -> userService.updateUser(user.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUserTests {

        @Test
        @DisplayName("should set status to INACTIVE")
        void shouldDeactivateUser() {
            User user = testUser("alice@example.com");
            when(userRepository.findByIdAndTenantId(user.getId(), TENANT_ID))
                    .thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.deactivateUser(user.getId());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("INACTIVE");
        }
    }

    @Nested
    @DisplayName("recordLogin")
    class RecordLoginTests {

        @Test
        @DisplayName("should save login history entry")
        void shouldSaveLoginHistory() {
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            userService.recordLogin("user-1", TENANT_ID, "127.0.0.1", "UI", "SUCCESS", "Chrome");

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
            assertThat(captor.getValue().getSourceIp()).isEqualTo("127.0.0.1");
            assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("should update user on successful login")
        void shouldUpdateUserOnSuccess() {
            User user = testUser("alice@example.com");
            user.setLoginCount(5);
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.recordLogin("user-1", TENANT_ID, "127.0.0.1", "UI", "SUCCESS", "Chrome");

            assertThat(user.getLoginCount()).isEqualTo(6);
            assertThat(user.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("should lock user after max failed attempts")
        void shouldLockUserAfterMaxFailedAttempts() {
            User user = testUser("alice@example.com");
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(loginHistoryRepository.countByUserIdAndStatusAndLoginTimeAfter(
                    eq("user-1"), eq("FAILED"), any())).thenReturn(5L);
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.recordLogin("user-1", TENANT_ID, "127.0.0.1", "UI", "FAILED", "Chrome");

            assertThat(user.getStatus()).isEqualTo("LOCKED");
        }
    }

    @Nested
    @DisplayName("provisionOrUpdate (JIT)")
    class JitProvisioningTests {

        @Test
        @DisplayName("should create new user on first login")
        void shouldCreateNewUser() {
            when(userRepository.findByTenantIdAndEmail(TENANT_ID, "new@example.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.provisionOrUpdate(TENANT_ID, "new@example.com",
                    "New", "User", "newuser");

            assertThat(result.getEmail()).isEqualTo("new@example.com");
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should update existing user on subsequent login")
        void shouldUpdateExistingUser() {
            User existing = testUser("existing@example.com");
            existing.setLoginCount(3);
            when(userRepository.findByTenantIdAndEmail(TENANT_ID, "existing@example.com"))
                    .thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.provisionOrUpdate(TENANT_ID, "existing@example.com",
                    "Existing", "User", "existinguser");

            assertThat(result.getLoginCount()).isEqualTo(4);
            assertThat(result.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw ValidationException for blank email")
        void shouldThrowForBlankEmail() {
            assertThatThrownBy(() -> userService.provisionOrUpdate(TENANT_ID, "", "A", "B", "c"))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
