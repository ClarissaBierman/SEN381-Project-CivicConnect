package com.civicconnect.common.error;

import com.civicconnect.common.ratelimit.RateLimitedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");

    private static ApiError body(ResponseEntity<Object> response) {
        return (ApiError) response.getBody();
    }

    @ParameterizedTest
    @CsvSource({"400,VALIDATION_FAILED", "401,UNAUTHENTICATED", "403,FORBIDDEN", "404,NOT_FOUND", "409,CONFLICT",
            "429,TOO_MANY_REQUESTS", "503,SERVICE_UNAVAILABLE", "500,INTERNAL_ERROR", "502,INTERNAL_ERROR",
            "405,BAD_REQUEST", "415,BAD_REQUEST"})
    @DisplayName("Every HTTP status maps to a documented error code")
    void statusToCode(int status, ErrorCode expected) {
        assertThat(ErrorCode.forStatus(status)).isEqualTo(expected);
    }

    @Test
    @DisplayName("429 carries Retry-After; other integrity errors are a generic 409; security errors are 401/403")
    void handlers() {
        ResponseEntity<Object> limited = handler.handleRateLimited(new RateLimitedException("Slow down.", 30), request);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(new RateLimitedException("x", 0).getRetryAfterSeconds()).isEqualTo(1);

        ResponseEntity<Object> integrity = handler.handleIntegrity(new DataIntegrityViolationException("fk violation"), request);
        assertThat(body(integrity).code()).isEqualTo("CONFLICT");

        assertThat(body(handler.handleAccessDenied(new AccessDeniedException("no"), request)).status()).isEqualTo(403);
        assertThat(body(handler.handleAuthentication(new BadCredentialsException("no"), request)).status()).isEqualTo(401);
        assertThat(body(handler.handleApi(new ApiException(ErrorCode.LAST_MANAGER, "Last."), request)).status()).isEqualTo(422);
    }
}
