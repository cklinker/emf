package com.emf.controlplane.config;

import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.runtime.router.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ControlPlaneExceptionHandler")
class ControlPlaneExceptionHandlerTest {

    private ControlPlaneExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ControlPlaneExceptionHandler();
        request = new MockHttpServletRequest("POST", "/control/collections/123/validation-rules");
    }

    @Nested
    @DisplayName("handleValidationException")
    class HandleValidationExceptionTests {

        @Test
        @DisplayName("should return 400 with field error details")
        void shouldReturn400WithFieldErrorDetails() {
            ValidationException ex = new ValidationException("errorField", "Field 'quantity' not found in collection");

            ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(400);
            assertThat(response.getBody().error()).isEqualTo("Bad Request");
            assertThat(response.getBody().message()).contains("errorField");
            assertThat(response.getBody().errors()).hasSize(1);
            assertThat(response.getBody().errors().get(0).field()).isEqualTo("errorField");
            assertThat(response.getBody().errors().get(0).code()).isEqualTo("validation");
        }

        @Test
        @DisplayName("should return 400 without field errors when fieldName is null")
        void shouldReturn400WithoutFieldErrors() {
            ValidationException ex = new ValidationException("General validation error");

            ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(400);
            assertThat(response.getBody().errors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("handleResourceNotFoundException")
    class HandleResourceNotFoundExceptionTests {

        @Test
        @DisplayName("should return 404 with resource details")
        void shouldReturn404WithResourceDetails() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Collection", "abc-123");

            ResponseEntity<ErrorResponse> response = handler.handleResourceNotFoundException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(404);
            assertThat(response.getBody().error()).isEqualTo("Not Found");
            assertThat(response.getBody().message()).contains("Collection");
            assertThat(response.getBody().message()).contains("abc-123");
        }
    }

    @Nested
    @DisplayName("handleDuplicateResourceException")
    class HandleDuplicateResourceExceptionTests {

        @Test
        @DisplayName("should return 409 with field error details")
        void shouldReturn409WithFieldErrorDetails() {
            DuplicateResourceException ex = new DuplicateResourceException("Collection", "name", "Products");

            ResponseEntity<ErrorResponse> response = handler.handleDuplicateResourceException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(409);
            assertThat(response.getBody().error()).isEqualTo("Conflict");
            assertThat(response.getBody().errors()).hasSize(1);
            assertThat(response.getBody().errors().get(0).field()).isEqualTo("name");
            assertThat(response.getBody().errors().get(0).code()).isEqualTo("duplicate");
        }

        @Test
        @DisplayName("should return 409 without field errors when fieldName is null")
        void shouldReturn409WithoutFieldErrors() {
            DuplicateResourceException ex = new DuplicateResourceException("Duplicate resource");

            ResponseEntity<ErrorResponse> response = handler.handleDuplicateResourceException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errors()).isEmpty();
        }
    }
}
