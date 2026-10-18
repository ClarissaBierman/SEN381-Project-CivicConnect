package com.civicconnect.common.error;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.civicconnect.common.ratelimit.RateLimitedException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

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

        assertThat(body(handler.handleAccessDenied(request)).status()).isEqualTo(403);
        assertThat(body(handler.handleAuthentication(request)).status()).isEqualTo(401);
        assertThat(body(handler.handleApi(new ApiException(ErrorCode.LAST_MANAGER, "Last."), request)).status()).isEqualTo(422);
    }

    @Test
    @DisplayName("D-40: a duplicate-key error logs the constraint name, never the duplicate email address")
    void integrityLogHasNoPersonalData() {
        String postgresMessage = "ERROR: duplicate key value violates unique constraint \"ux_client_email\"\n"
                + "  Detail: Key (email)=(thabo.mokoena@example.com) already exists.";
        SQLException sql = new SQLException(postgresMessage, "23505");
        // Through JPA: Hibernate knows the constraint name
        DataIntegrityViolationException viaJpa = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException(postgresMessage, sql, "ux_client_email"));
        // Through JdbcTemplate: only the SQLException
        DataIntegrityViolationException viaJdbc = new DataIntegrityViolationException(postgresMessage, sql);

        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertThat(body(handler.handleIntegrity(viaJpa, request)).code()).isEqualTo("CONFLICT");
            assertThat(body(handler.handleIntegrity(viaJdbc, request)).code()).isEqualTo("CONFLICT");
        } finally {
            logger.detachAppender(logs);
        }

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .hasSize(2)
                .noneMatch(line -> line.contains("@") || line.contains("thabo"))
                .anyMatch(line -> line.contains("constraint ux_client_email"))
                .anyMatch(line -> line.contains("SQL state 23505"));
    }
}
