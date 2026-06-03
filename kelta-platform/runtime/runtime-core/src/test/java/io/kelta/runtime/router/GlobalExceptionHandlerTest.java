package io.kelta.runtime.router;

import io.kelta.jsonapi.JsonApiError;
import io.kelta.runtime.query.InvalidQueryException;
import io.kelta.runtime.storage.UniqueConstraintViolationException;
import io.kelta.runtime.validation.RecordValidationException;
import io.kelta.runtime.validation.ValidationError;
import io.kelta.runtime.validation.ValidationException;
import io.kelta.runtime.validation.ValidationResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the JSON:API 4xx error envelope: every response body has a non-empty
 * {@code errors[]} array, and each error object carries {@code status}, {@code code},
 * and {@code detail} so clients can distinguish failure classes without trial-and-error.
 */
@SuppressWarnings("unchecked")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/widgets");
    }

    @Test
    void methodArgumentNotValid_emitsFieldLevelErrors() throws NoSuchMethodException {
        Method m = Sample.class.getDeclaredMethod("create", String.class);
        MethodParameter mp = new MethodParameter(m, 0);

        BindingResult bindingResult = new MapBindingResult(Map.of(), "widget");
        bindingResult.addError(new FieldError("widget", "name", null, false,
                new String[]{"NotBlank"}, null, "name must not be blank"));
        bindingResult.addError(new FieldError("widget", "size", null, false,
                new String[]{"Positive"}, null, "size must be > 0"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<JsonApiError> errors = (List<JsonApiError>) response.getBody().get("errors");
        assertThat(errors).hasSize(2);
        for (JsonApiError e : errors) {
            assertThat(e.getStatus()).isEqualTo("400");
            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
            assertThat(e.getDetail()).isNotBlank();
            assertThat(e.getSource()).containsKey("pointer");
        }
        assertThat(errors.get(0).getSource().get("pointer")).isEqualTo("/data/attributes/name");
        assertThat(errors.get(1).getSource().get("pointer")).isEqualTo("/data/attributes/size");
    }

    @Test
    void constraintViolation_emitsParameterPointers() {
        ConstraintViolation<Object> v = mock(ConstraintViolation.class);
        when(v.getMessage()).thenReturn("must be greater than 0");
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn("limit");
        when(v.getPropertyPath()).thenReturn(path);

        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.add(v);
        ConstraintViolationException ex = new ConstraintViolationException("bad params", violations);

        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<JsonApiError> errors = (List<JsonApiError>) response.getBody().get("errors");
        assertThat(errors).hasSize(1);
        JsonApiError e = errors.get(0);
        assertThat(e.getStatus()).isEqualTo("400");
        assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(e.getDetail()).isEqualTo("must be greater than 0");
        assertThat(e.getSource()).containsEntry("parameter", "limit");
    }

    @Test
    void httpMessageNotReadable_emits400WithInvalidPayload() {
        HttpInputMessage input = new HttpInputMessage() {
            @Override public InputStream getBody() { return InputStream.nullInputStream(); }
            @Override public org.springframework.http.HttpHeaders getHeaders() {
                return new org.springframework.http.HttpHeaders();
            }
        };
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("malformed JSON", input);

        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<JsonApiError> errors = (List<JsonApiError>) response.getBody().get("errors");
        assertThat(errors).hasSize(1);
        JsonApiError e = errors.get(0);
        assertThat(e.getStatus()).isEqualTo("400");
        assertThat(e.getCode()).isEqualTo("INVALID_PAYLOAD");
        assertThat(e.getTitle()).isEqualTo("Bad Request");
        assertThat(e.getDetail()).contains("could not be parsed");
        assertThat(e.getMeta()).containsEntry("path", "/api/widgets");
    }

    @Test
    void missingParameter_emits400WithSource() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("filter", "String");

        ResponseEntity<Map<String, Object>> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("400");
        assertThat(e.getCode()).isEqualTo("MISSING_PARAMETER");
        assertThat(e.getDetail()).isEqualTo("Required parameter 'filter' is missing");
        assertThat(e.getSource()).containsEntry("parameter", "filter");
    }

    @Test
    void typeMismatch_emits400WithSource() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, new IllegalArgumentException("bad number"));

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("400");
        assertThat(e.getCode()).isEqualTo("INVALID_PARAMETER");
        assertThat(e.getDetail()).contains("'id'").contains("Long");
        assertThat(e.getSource()).containsEntry("parameter", "id");
    }

    @Test
    void noResourceFound_emits404() {
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/api/missing", "static");
        request.setRequestURI("/api/missing");
        request.setMethod("GET");

        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("404");
        assertThat(e.getCode()).isEqualTo("NOT_FOUND");
        assertThat(e.getTitle()).isEqualTo("Not Found");
        assertThat(e.getDetail()).contains("GET").contains("/api/missing");
    }

    @Test
    void noHandlerFound_emits404() {
        NoHandlerFoundException ex = new NoHandlerFoundException(
                "PUT", "/api/missing", new org.springframework.http.HttpHeaders());
        request.setRequestURI("/api/missing");
        request.setMethod("PUT");

        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("404");
        assertThat(e.getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void methodNotAllowed_emits405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<Map<String, Object>> response = handler.handleMethodNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("405");
        assertThat(e.getCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(e.getDetail()).contains("DELETE");
    }

    @Test
    void unsupportedMediaType_emits415() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(org.springframework.http.MediaType.TEXT_PLAIN,
                        List.of(org.springframework.http.MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = handler.handleUnsupportedMediaType(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("415");
        assertThat(e.getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(e.getDetail()).contains("text/plain");
    }

    @Test
    void responseStatus_preservesStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "Widget already exists");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("409");
        assertThat(e.getCode()).isEqualTo("CONFLICT");
        assertThat(e.getDetail()).isEqualTo("Widget already exists");
    }

    @Test
    void validationException_emitsFieldLevelErrorsWithCodeAndPointer() {
        ValidationResult result = ValidationResult.failure(List.of(
                new io.kelta.runtime.validation.FieldError("email", "must be a valid email", "PATTERN"),
                io.kelta.runtime.validation.FieldError.nullable("name")));

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidationException(new ValidationException(result), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<JsonApiError> errors = (List<JsonApiError>) response.getBody().get("errors");
        assertThat(errors).hasSize(2);
        for (JsonApiError e : errors) {
            assertThat(e.getStatus()).isEqualTo("400");
            assertThat(e.getCode()).isNotBlank();
            assertThat(e.getDetail()).isNotBlank();
            assertThat(e.getSource()).containsKey("pointer");
        }
        assertThat(errors.get(0).getSource().get("pointer")).isEqualTo("/data/attributes/email");
        assertThat(errors.get(0).getCode()).isEqualTo("PATTERN");
        assertThat(errors.get(1).getCode()).isEqualTo("nullable");
    }

    @Test
    void recordValidationException_emits422WithRuleFailedCode() {
        RecordValidationException ex = new RecordValidationException(List.of(
                new ValidationError("title_year_range",
                        "Year must be between 1888 and 2031", "year")));

        ResponseEntity<Map<String, Object>> response =
                handler.handleRecordValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        List<JsonApiError> errors = (List<JsonApiError>) response.getBody().get("errors");
        assertThat(errors).hasSize(1);
        JsonApiError e = errors.get(0);
        assertThat(e.getStatus()).isEqualTo("422");
        assertThat(e.getCode()).isEqualTo("VALIDATION_RULE_FAILED");
        assertThat(e.getDetail()).isEqualTo("Year must be between 1888 and 2031");
        assertThat(e.getSource()).containsEntry("pointer", "/data/attributes/year");
    }

    @Test
    void recordValidationException_withoutErrorField_pointsAtRuleName() {
        RecordValidationException ex = new RecordValidationException(List.of(
                new ValidationError("cross_field_rule", "Inconsistent state", null)));

        ResponseEntity<Map<String, Object>> response =
                handler.handleRecordValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getSource()).containsEntry("pointer", "/data/attributes/cross_field_rule");
    }

    @Test
    void invalidQueryException_withField_includesPointer() {
        InvalidQueryException ex = new InvalidQueryException("sort", "unknown sort field");

        ResponseEntity<Map<String, Object>> response =
                handler.handleInvalidQueryException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("400");
        assertThat(e.getCode()).isEqualTo("INVALID_QUERY");
        assertThat(e.getDetail()).isEqualTo("unknown sort field");
        assertThat(e.getSource()).containsEntry("pointer", "/data/attributes/sort");
    }

    @Test
    void uniqueConstraintViolation_emits409WithPointer() {
        UniqueConstraintViolationException ex =
                new UniqueConstraintViolationException("widgets", "name", "duplicate");

        ResponseEntity<Map<String, Object>> response =
                handler.handleUniqueConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("409");
        assertThat(e.getCode()).isEqualTo("UNIQUE_VIOLATION");
        assertThat(e.getDetail()).isNotBlank();
        assertThat(e.getSource()).containsEntry("pointer", "/data/attributes/name");
    }

    @Test
    void genericException_emits500WithGenericDetail() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new RuntimeException("secret stack trace"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonApiError e = ((List<JsonApiError>) response.getBody().get("errors")).get(0);
        assertThat(e.getStatus()).isEqualTo("500");
        assertThat(e.getCode()).isEqualTo("INTERNAL_ERROR");
        // detail must not leak the original exception message
        assertThat(e.getDetail()).doesNotContain("secret stack trace");
    }

    static class Sample {
        @SuppressWarnings("unused")
        public void create(String body) {}
    }
}
