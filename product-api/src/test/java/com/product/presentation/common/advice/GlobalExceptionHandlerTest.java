package com.product.presentation.common.advice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.application.common.exception.DbFallbackRejectedException;
import com.product.domain.common.exception.CommonErrorCode;
import com.product.domain.common.exception.DomainException;
import com.product.domain.product.exception.ProductErrorCode;
import com.product.presentation.common.advice.GlobalExceptionHandler;
import com.product.presentation.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private GlobalExceptionHandler globalExceptionHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        globalExceptionHandler = new GlobalExceptionHandler();
        objectMapper = new ObjectMapper().findAndRegisterModules();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestExceptionController())
                .setControllerAdvice(globalExceptionHandler)
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (validator != null) {
            validator.close();
        }
    }

    @Test
    @DisplayName("DomainException 발생 시 상태코드와 failure 응답을 반환한다")
    void handleDomainException_thenReturnFailureResponse() throws Exception {
        mockMvc.perform(get("/test-exceptions/domain"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(ProductErrorCode.PRODUCT_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.error.message").value(ProductErrorCode.PRODUCT_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0))
                .andExpect(jsonPath("$.path").value("/test-exceptions/domain"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 발생 시 validation failure 응답을 반환한다")
    void handleMethodArgumentNotValidException_thenReturnFailureResponse() throws Exception {
        String requestBody = """
                {
                  "name": ""
                }
                """;

        mockMvc.perform(post("/test-exceptions/body-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(CommonErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.error.message").value(CommonErrorCode.VALIDATION_ERROR.getMessage()))
                .andExpect(jsonPath("$.error.details[0].field").value("name"))
                .andExpect(jsonPath("$.error.details[0].reason").value("name은 필수입니다."))
                .andExpect(jsonPath("$.error.details[0].rejectedValue").value(""))
                .andExpect(jsonPath("$.path").value("/test-exceptions/body-validation"));
    }

    @Test
    @DisplayName("ConstraintViolationException 발생 시 invalid input failure 응답을 반환한다")
    void handleConstraintViolationException_thenReturnFailureResponse() {
        QueryValidationRequest requestObject = new QueryValidationRequest("");
        Set<ConstraintViolation<QueryValidationRequest>> violations = validator.validate(requestObject);
        ConstraintViolationException exception = new ConstraintViolationException(violations);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-exceptions/query-validation");

        ResponseEntity<ApiResponse<Void>> actual =
                globalExceptionHandler.handleConstraintViolationException(exception, request);

        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(actual.getBody()).isNotNull();
        assertThat(actual.getBody().success()).isFalse();
        assertThat(actual.getBody().data()).isNull();
        assertThat(actual.getBody().path()).isEqualTo("/test-exceptions/query-validation");

        assertThat(actual.getBody().error()).isNotNull();
        assertThat(actual.getBody().error().code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode());
        assertThat(actual.getBody().error().message()).isEqualTo(CommonErrorCode.INVALID_INPUT.getMessage());
        assertThat(actual.getBody().error().details()).hasSize(1);

        Map<String, Object> detail = objectMapper.convertValue(
                actual.getBody().error().details().get(0),
                new TypeReference<Map<String, Object>>() {}
        );

        assertThat(detail.get("field")).isEqualTo("name");
        assertThat(detail.get("reason")).isEqualTo("name은 필수입니다.");
        assertThat(detail.get("rejectedValue")).isEqualTo("");
    }

    @Test
    @DisplayName("잘못된 JSON 요청 시 message not readable failure 응답을 반환한다")
    void handleHttpMessageNotReadableException_thenReturnFailureResponse() throws Exception {
        mockMvc.perform(post("/test-exceptions/body-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(CommonErrorCode.MESSAGE_NOT_READABLE.getCode()))
                .andExpect(jsonPath("$.error.message").value(CommonErrorCode.MESSAGE_NOT_READABLE.getMessage()))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0))
                .andExpect(jsonPath("$.path").value("/test-exceptions/body-validation"));
    }

    @Test
    @DisplayName("PathVariable 타입 변환 실패 시 invalid input failure 응답을 반환한다")
    void handleMethodArgumentTypeMismatchException_thenReturnFailureResponse() throws Exception {
        mockMvc.perform(get("/test-exceptions/jobs/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(CommonErrorCode.INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.error.message").value(CommonErrorCode.INVALID_INPUT.getMessage()))
                .andExpect(jsonPath("$.error.details[0].field").value("jobId"))
                .andExpect(jsonPath("$.error.details[0].reason").value("Invalid parameter type."))
                .andExpect(jsonPath("$.error.details[0].rejectedValue").value("not-a-uuid"))
                .andExpect(jsonPath("$.path").value("/test-exceptions/jobs/not-a-uuid"));
    }

    @Test
    @DisplayName("DB fallback bulkhead 거절 시 503 failure 응답을 반환한다")
    void handleDbFallbackRejectedException_thenReturnServiceUnavailableResponse() throws Exception {
        mockMvc.perform(get("/test-exceptions/db-fallback-rejected"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(CommonErrorCode.SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.error.message").value(CommonErrorCode.SERVICE_UNAVAILABLE.getMessage()))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0))
                .andExpect(jsonPath("$.path").value("/test-exceptions/db-fallback-rejected"));
    }

    @Test
    @DisplayName("예상하지 못한 예외 발생 시 500 failure 응답을 반환한다")
    void handleException_thenReturnInternalServerErrorResponse() throws Exception {
        mockMvc.perform(get("/test-exceptions/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.getCode()))
                .andExpect(jsonPath("$.error.message").value(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0))
                .andExpect(jsonPath("$.path").value("/test-exceptions/unexpected"));
    }

    @RestController
    @Validated
    @RequestMapping("/test-exceptions")
    static class TestExceptionController {

        @GetMapping("/domain")
        public void throwDomainException() {
            throw new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        @PostMapping("/body-validation")
        public void validateBody(@RequestBody @Valid TestRequest request) {
        }

        @GetMapping("/unexpected")
        public void throwUnexpectedException() {
            throw new RuntimeException("unexpected error");
        }

        @GetMapping("/db-fallback-rejected")
        public void throwDbFallbackRejectedException() {
            throw new DbFallbackRejectedException(1, new RuntimeException("bulkhead full"));
        }

        @GetMapping("/jobs/{jobId}")
        public void getJob(@PathVariable UUID jobId) {
        }
    }

    record TestRequest(
            @NotBlank(message = "name은 필수입니다.")
            String name
    ) {
    }

    record QueryValidationRequest(
            @NotBlank(message = "name은 필수입니다.")
            String name
    ) {
    }
}
