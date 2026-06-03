package io.kelta.runtime.router;

import io.kelta.runtime.query.InvalidQueryException;
import io.kelta.runtime.validation.FieldError;
import io.kelta.runtime.validation.ValidationException;
import io.kelta.runtime.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the JSON:API error envelope returned by {@link GlobalExceptionHandler}
 * for every 4xx case populates at least {@code status}, {@code code}, {@code title},
 * and {@code detail}, and that field-level validation errors include
 * {@code source.pointer}.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void validationException_returnsFieldLevelPointers() throws Exception {
        mvc.perform(get("/probe/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].status").value("400"))
                .andExpect(jsonPath("$.errors[0].code").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].title").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].source.pointer").value("/data/attributes/email"));
    }

    @Test
    void invalidQuery_returnsBadRequestWithCodeAndDetail() throws Exception {
        mvc.perform(get("/probe/invalid-query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status").value("400"))
                .andExpect(jsonPath("$.errors[0].code").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].title").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].source.pointer").value("/data/attributes/status"));
    }

    @Test
    void responseStatusException_preservesStatusAndPopulatesAllFields() throws Exception {
        mvc.perform(get("/probe/teapot"))
                .andExpect(status().isIAmATeapot())
                .andExpect(jsonPath("$.errors[0].status").value("418"))
                .andExpect(jsonPath("$.errors[0].code").value("I_AM_A_TEAPOT"))
                .andExpect(jsonPath("$.errors[0].title").value("I'm a teapot"))
                .andExpect(jsonPath("$.errors[0].detail").value("brew coffee"));
    }

    @Test
    void noHandlerFound_returnsPopulated404() throws Exception {
        mvc.perform(get("/probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status").value("404"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].title").value("Not Found"))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());
    }

    @Test
    void methodNotAllowed_returnsPopulated405() throws Exception {
        mvc.perform(delete("/probe/teapot"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errors[0].status").value("405"))
                .andExpect(jsonPath("$.errors[0].code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.errors[0].title").value("Method Not Allowed"))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());
    }

    @Test
    void unsupportedMediaType_returnsPopulated415() throws Exception {
        mvc.perform(post("/probe/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errors[0].status").value("415"))
                .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.errors[0].title").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());
    }

    @Test
    void malformedJson_returnsPopulated400() throws Exception {
        mvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status").value("400"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PAYLOAD"))
                .andExpect(jsonPath("$.errors[0].title").value("Bad Request"))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());
    }

    @Test
    void missingRequiredParameter_returnsPopulated400() throws Exception {
        mvc.perform(get("/probe/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status").value("400"))
                .andExpect(jsonPath("$.errors[0].code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].source.parameter").value("name"));
    }

    @Test
    void missingRequiredHeader_returnsPopulated400() throws Exception {
        mvc.perform(get("/probe/needs-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status").value("400"))
                .andExpect(jsonPath("$.errors[0].code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.errors[0].source.header").value("X-Tenant-ID"));
    }

    @Test
    void parameterTypeMismatch_returnsPopulated400() throws Exception {
        mvc.perform(get("/probe/needs-int").param("n", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status").value("400"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].source.parameter").value("n"))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/validation")
        Map<String, Object> failsValidation() {
            ValidationResult result = ValidationResult.failure(
                    List.of(new FieldError("email", "must be a valid email address", "format")));
            throw new ValidationException(result);
        }

        @GetMapping("/invalid-query")
        Map<String, Object> invalidQuery() {
            throw new InvalidQueryException("status", "unknown operator");
        }

        @GetMapping("/teapot")
        Map<String, Object> teapot() {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "brew coffee");
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, Object> acceptBody(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/needs-param")
        Map<String, Object> needsParam(@RequestParam String name) {
            return Map.of("name", name);
        }

        @GetMapping("/needs-header")
        Map<String, Object> needsHeader(@RequestHeader("X-Tenant-ID") String tenant) {
            return Map.of("tenant", tenant);
        }

        @GetMapping("/needs-int")
        Map<String, Object> needsInt(@RequestParam int n) {
            return Map.of("n", n);
        }
    }
}
