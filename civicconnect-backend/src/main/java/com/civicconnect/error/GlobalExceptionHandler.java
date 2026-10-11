package com.civicconnect.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns every error thrown by a controller or service into the PED error model (ApiError).
 * Errors from the security filters (missing/expired token, wrong role on a URL) are written in the
 * same format by SecurityProblemHandler. Stack traces are logged, never returned (INTERNAL_ERROR).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Business rule errors raised by the services. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(ex.getCode(), ex.getCode().status().value(), ex.getMessage(), request, ex.getFieldErrors());
    }

    /** DEC-006: the request was changed by someone else since the user loaded it. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleVersionConflict(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(ErrorCode.VERSION_CONFLICT, 409,
                "This request was updated by someone else - please refresh.", request, null);
    }

    /** A database rule caught a duplicate or a second active handler that the service checks missed (race). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("ux_request_handler_active")) {
            return respond(ErrorCode.ALREADY_ASSIGNED, 409, "This request is already assigned.", request, null);
        }
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), message);
        return respond(ErrorCode.CONFLICT, 409, "This change conflicts with existing data.", request, null);
    }

    /** NFR-005: the database cannot be reached, e.g. the Supabase project is paused. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<Object> handleDatabaseDown(Exception ex, HttpServletRequest request) {
        log.error("Database unavailable on {}", request.getRequestURI(), ex);
        return respond(ErrorCode.SERVICE_UNAVAILABLE, 503,
                "The service is temporarily unavailable. Please try again later.", request, null);
    }

    /** @PreAuthorize failed: the user is logged in but has the wrong role. */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Object> handleAccessDenied(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, 403, "You do not have access.", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, 401, "Please log in.", request, null);
    }

    /** Anything unexpected: log it with the stack trace, return a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, 500, "Something went wrong. Please try again.", request, null);
    }

    // ------------------------------------------------------------------ Spring's own exceptions

    /** @Valid failed on a request body: 400 VALIDATION_FAILED with one entry per field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldError> fields = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.add(new ApiError.FieldError(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> fields.add(new ApiError.FieldError(e.getObjectName(), e.getDefaultMessage())));
        return respond(ErrorCode.VALIDATION_FAILED, 400, "Some fields are invalid.", path(request), fields);
    }

    /** Validation failed on a path or query parameter. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<ApiError.FieldError> fields = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(e -> fields.add(new ApiError.FieldError(name, e.getDefaultMessage())));
        }
        return respond(ErrorCode.VALIDATION_FAILED, 400, "Some fields are invalid.", path(request), fields);
    }

    /**
     * Every other Spring MVC error (unreadable JSON, wrong type in a query parameter, unknown URL,
     * wrong HTTP method ...) arrives here; convert it to the same format.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String detail = (body instanceof ProblemDetail pd && pd.getDetail() != null) ? pd.getDetail() : ex.getMessage();
        int status = statusCode.value();
        ResponseEntity<Object> response = respond(ErrorCode.forStatus(status), status, detail, path(request), null);
        HttpHeaders merged = new HttpHeaders();
        merged.putAll(headers);
        merged.putAll(response.getHeaders());
        return ResponseEntity.status(status).headers(merged).body(response.getBody());
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<Object> respond(ErrorCode code, int status, String detail, HttpServletRequest request,
                                           List<ApiError.FieldError> fields) {
        return respond(code, status, detail, request.getRequestURI(), fields);
    }

    private ResponseEntity<Object> respond(ErrorCode code, int status, String detail, String path,
                                           List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiError.of(code, status, detail, path, fields));
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }
}
