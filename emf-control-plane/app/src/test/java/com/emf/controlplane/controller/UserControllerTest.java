package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateUserRequest;
import com.emf.controlplane.dto.UpdateUserRequest;
import com.emf.controlplane.dto.UserDto;
import com.emf.controlplane.entity.LoginHistory;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService);
    }

    private User createTestUser(String email, String firstName, String lastName) {
        User user = new User(email, firstName, lastName);
        user.setTenantId("tenant-1");
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    @Nested
    @DisplayName("GET /control/users")
    class ListUsersTests {

        @Test
        @DisplayName("should return 200 OK with paginated users")
        void shouldReturnPaginatedUsers() {
            Pageable pageable = PageRequest.of(0, 20);
            List<User> users = List.of(
                    createTestUser("alice@example.com", "Alice", "Smith"),
                    createTestUser("bob@example.com", "Bob", "Jones"));
            Page<User> page = new PageImpl<>(users, pageable, 2);
            when(userService.listUsers(null, null, pageable)).thenReturn(page);

            ResponseEntity<Page<UserDto>> response = controller.listUsers(null, null, pageable);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).hasSize(2);
            assertThat(response.getBody().getContent().get(0).getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("should pass filter and status to service")
        void shouldPassFilterAndStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(), pageable, 0);
            when(userService.listUsers("alice", "ACTIVE", pageable)).thenReturn(page);

            controller.listUsers("alice", "ACTIVE", pageable);

            verify(userService).listUsers("alice", "ACTIVE", pageable);
        }
    }

    @Nested
    @DisplayName("POST /control/users")
    class CreateUserTests {

        @Test
        @DisplayName("should return 201 CREATED with new user")
        void shouldCreateUser() {
            CreateUserRequest request = new CreateUserRequest("new@example.com", "New", "User");
            User user = createTestUser("new@example.com", "New", "User");
            when(userService.createUser(request)).thenReturn(user);

            ResponseEntity<UserDto> response = controller.createUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().getEmail()).isEqualTo("new@example.com");
            assertThat(response.getBody().getFirstName()).isEqualTo("New");
        }

        @Test
        @DisplayName("should propagate DuplicateResourceException")
        void shouldPropagateDuplicateException() {
            CreateUserRequest request = new CreateUserRequest("existing@example.com", "Dup", "User");
            when(userService.createUser(request))
                    .thenThrow(new DuplicateResourceException("User", "email", "existing@example.com"));

            assertThatThrownBy(() -> controller.createUser(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("GET /control/users/{id}")
    class GetUserTests {

        @Test
        @DisplayName("should return 200 OK with user")
        void shouldReturnUser() {
            User user = createTestUser("alice@example.com", "Alice", "Smith");
            when(userService.getUser(user.getId())).thenReturn(user);

            ResponseEntity<UserDto> response = controller.getUser(user.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException")
        void shouldPropagateNotFoundException() {
            when(userService.getUser("missing"))
                    .thenThrow(new ResourceNotFoundException("User", "missing"));

            assertThatThrownBy(() -> controller.getUser("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("PUT /control/users/{id}")
    class UpdateUserTests {

        @Test
        @DisplayName("should return 200 OK with updated user")
        void shouldUpdateUser() {
            User user = createTestUser("alice@example.com", "Alice", "Updated");
            UpdateUserRequest request = new UpdateUserRequest();
            request.setLastName("Updated");
            when(userService.updateUser(eq(user.getId()), any(UpdateUserRequest.class))).thenReturn(user);

            ResponseEntity<UserDto> response = controller.updateUser(user.getId(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getLastName()).isEqualTo("Updated");
        }
    }

    @Nested
    @DisplayName("POST /control/users/{id}/deactivate")
    class DeactivateUserTests {

        @Test
        @DisplayName("should return 204 No Content")
        void shouldDeactivateUser() {
            doNothing().when(userService).deactivateUser("user-1");

            ResponseEntity<Void> response = controller.deactivateUser("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(userService).deactivateUser("user-1");
        }
    }

    @Nested
    @DisplayName("POST /control/users/{id}/activate")
    class ActivateUserTests {

        @Test
        @DisplayName("should return 204 No Content")
        void shouldActivateUser() {
            doNothing().when(userService).activateUser("user-1");

            ResponseEntity<Void> response = controller.activateUser("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(userService).activateUser("user-1");
        }
    }

    @Nested
    @DisplayName("GET /control/users/{id}/login-history")
    class LoginHistoryTests {

        @Test
        @DisplayName("should return 200 OK with paginated login history")
        void shouldReturnLoginHistory() {
            Pageable pageable = PageRequest.of(0, 20);
            LoginHistory entry = new LoginHistory();
            entry.setUserId("user-1");
            entry.setTenantId("tenant-1");
            entry.setSourceIp("127.0.0.1");
            entry.setLoginType("UI");
            entry.setStatus("SUCCESS");
            Page<LoginHistory> page = new PageImpl<>(List.of(entry), pageable, 1);
            when(userService.getLoginHistory("user-1", pageable)).thenReturn(page);

            var response = controller.getLoginHistory("user-1", pageable);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).hasSize(1);
            assertThat(response.getBody().getContent().get(0).getSourceIp()).isEqualTo("127.0.0.1");
        }
    }
}
