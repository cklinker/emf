package io.kelta.runtime.router;

import io.kelta.jsonapi.JsonApiError;
import io.kelta.runtime.query.InvalidQueryException;
import io.kelta.runtime.storage.UniqueConstraintViolationException;
import io.kelta.runtime.validation.FieldError;
import io.kelta.runtime.validation.RecordValidationException;
import io.kelta.runtime.validation.ValidationError;
import io.kelta.runtime.validation.ValidationException;
import io.kelta.runtime.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies every 4xx exception path produces a populated JSON:API error payload.
 *
 * <p>The contract: each error object must carry at minimum {@code status},
 * {@code code}, {@code title}, and {@code detail}. Field-level errors must also
 * include {@code source.pointer}. No 4xx response is allowed to emit an empty
 * {@code {}} error object.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/widgets");
        request.setMethod("POST");
    }

    @Test
    @DisplayName("ValidationException with field errors → 400 with source.pointer per field")
    void validationException_fieldLevel() {
        FieldError fe = new FieldError("email", "must be a valid email", "pattern");
        ValidationResult result = ValidationResult.failure(List.of(fe));
        ValidationException ex = new ValidationException(result);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonApiError error = firstError(response);
        assertThat(error.getStatus()).isEqualTo("400");
        assertThat(error.getCode()).isEqualTo("PATTERN");
        assertThat(error.getTitle()).isEqualTo("Validation Error");
        assertThat(error.getDetail()).isEqualTo("must be a valid email");
        assertSourcePointer(error, "/data/attributes/email");
    }

    @Test
    @DisplayName("ValidationException without field details → 400 with VALIDATION_FAILED code")
    void validationException_messageOnly() {
        ValidationException ex = new ValidationException("Boom");

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex, request);

        JsonApiError error = firstError(response);
        assertThat(error.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.getDetail()).isEqualTo("Boom");
    }

    @Test
    @DisplayName("RecordValidationException → 400 with VALIDATION_RULE_FAILED + pointer to rule field")
    void recordValidationException() {
        ValidationError ve = new ValidationError("amount-positive", "must be > 0", "amount");
        RecordValidationException ex = new RecordValidationException(List.of(ve));

        ResponseEntity<Map<String, Object>> response = handler.handleRecordValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonApiError error = firstError(response);
        assertThat(error.getCode()).isEqualTo("VALIDATION_RULE_FAILED");
        assertThat(error.getDetail()).isEqualTo("must be > 0");
        assertSourcePointer(error, "/data/attributes/amount");
    }

    @Test
    @DisplayName("InvalidQueryException with field → 400 with INVALID_QUERY + source.pointer")
    void invalidQueryException_fieldLevel() {
        InvalidQueryException ex = new InvalidQueryException("createdAt", "unknown sort field");

        ResponseEntity<Map<String, Object>> response = handler.handleInvalidQueryException(ex, request);

        JsonApiError error = firstError(response);
        assertThat(error.getCode()).isEqualTo("INVALID_QUERY");
        assertThat(error.getTitle()).isEqualTo("Bad Request");
        assertThat(error.getDetail()).isEqualTo("unknown sort field");
        assertSourcePointer(error, "/data/attributes/createdAt");
    }

    @Test
    @DisplayName("UniqueConstraintViolationException → 409 with UNIQUE_CONSTRAINT_VIOLATION + pointer")
    void uniqueConstraintViolation() {
        UniqueConstraintViolationException ex = new UniqueConstraintViolationException(
                "users", "email", "a@b.com");

        ResponseEntity<Map<String, Object>> response = handler.handleUniqueConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonApiError error = firstError(response);
        assertThat(error.getStatus()).isEqualTo("409");
        assertThat(error.getCode()).isEqualTo("UNIQUE_CONSTRAINT_VIOLATION");
        assertSourcePointer(error, "/data/attributes/email");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException → 400 with INVALID_PAYLOAD")
    void messageNotReadable() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Cannot parse JSON",
                new MockHttpInputMessage("{".getBytes()));

        ResponseEntity<Map<String, Object>> response =
                handler.handleHttpMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonApiError error = firstError(response);
        assertThat(error.getCode()).isEqualTo("INVALID_PAYLOAD");
        assertThat(error.getTitle()).isEqualTo("Bad Request");
        assertThat(error.getDetail()).isNotNull();
    }

    @Test
    @DisplayName("MissingServletRequestParameterException → 400 with MISSING_PARAMETER")
    void missingParameter() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("q", "String");

        ResponseEntity<Map<String, Object>> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonApiError error = firstError(response);
        assertThat(error.getCode()).isEqualTo("MISSING_PARAMETER");
        assertThat(error.getDetail()).contains("'q'");
        assertThat(error.getSource()).isNotNull();
        assertThat(error.getSource().get("parameter")).isEqualTo("q");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException → 405 with METHOD_NOT_ALLOWED + allowedMethods")
    void methodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST"));

        ResponseEntity<Map<String, Object>> response = handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        JsonApiError error = firstError(response);
        assertThat(error.getStatus()).isEqualTo("405");
        assertThat(error.getCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(error.getMeta()).containsKey("allowedMethods");
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException → 415 with UNSUPPORTED_MEDIA_TYPE")
    void mediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = handler.handleMediaTypeNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        JsonApiError error = firstError(response);
        assertThat(error.getStatus()).isEqualTo("415");
        assertThat(error.getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("NoResourceFoundException → 404 with NOT_FOUND + path detail")
    void noResourceFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/missing", "/api/missing");
        request.setMethod("GET");
        request.setRequestURI("/api/missing");

        ResponseEntity<Map<String, Object>> response = handler.handleNoHandler(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonApiError error = firstError(response);
        assertThat(error.getStatus()).isEqualTo("404");
        assertThat(error.getCode()).isEqualTo("NOT_FOUND");
        assertThat(error.getDetail()).contains("/api/missing");
    }

    @Test
    @DisplayName("every 4xx handler produces non-empty status/code/detail (no empty {} objects)")
    void noEmptyErrors() {
        List<ResponseEntity<Map<String, Object>>> responses = List.of(
                handler.handleValidationException(new ValidationException("x"), request),
                handler.handleInvalidQueryException(new InvalidQueryException("bad query"), request),
                handler.handleHttpMessageNotReadable(
                        new HttpMessageNotReadableException(
                                "bad", new MockHttpInputMessage(new byte[0])),
                        request),
                handler.handleMissingParameter(
                        new MissingServletRequestParameterException("id", "UUID"), request),
                handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("DELETE"), request),
                handler.handleMediaTypeNotSupported(
                        new HttpMediaTypeNotSupportedException(MediaType.TEXT_HTML, List.of()),
                        request));

        for (ResponseEntity<Map<String, Object>> response : responses) {
            assertThat(response.getStatusCode().is4xxClientError()).isTrue();
            JsonApiError err = firstError(response);
            assertThat(err.getStatus()).isNotBlank();
            assertThat(err.getCode()).isNotBlank();
            assertThat(err.getDetail()).isNotBlank();
            // title is also part of the platform contract, even though JSON:API
            // makes it optional — clients render it as the headline.
            assertThat(err.getTitle()).isNotBlank();
        }
    }

    @SuppressWarnings("unchecked")
    private static JsonApiError firstError(ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        List<JsonApiError> errors = (List<JsonApiError>) body.get("errors");
        assertThat(errors).isNotEmpty();
        return errors.get(0);
    }

    private static void assertSourcePointer(JsonApiError error, String expected) {
        assertThat(error.getSource()).isNotNull();
        assertThat(error.getSource().get("pointer")).isEqualTo(expected);
    }
}
