package com.civicconnect.common.error;

import com.civicconnect.common.logging.LogSafe;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import com.civicconnect.common.ratelimit.RateLimitedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 429 with Retry-After, so the client knows how long to wait (B4). */
    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<Object> handleRateLimited(RateLimitedException ex, HttpServletRequest request) {
        ResponseEntity<Object> body = respond(ex.getCode(), 429, ex.getMessage(), request, null);
        return ResponseEntity.status(429).header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body.getBody());
    }

    /**
     * ?sort=unknownField. Endpoints check sort fields with SortGuard first; this is the safety net for
     * any paged endpoint that does not, so a bad sort is always a 400 and never a 500.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Object> handleBadSort(PropertyReferenceException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, 400, "Some fields are invalid.", request,
                List.of(new ApiError.FieldError("sort", "Cannot sort by '" + ex.getPropertyName() + "'.")));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(ex.getCode(), ex.getCode().status().value(), ex.getMessage(), request, ex.getFieldErrors());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleVersionConflict(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(ErrorCode.VERSION_CONFLICT, 409,
                "This request was updated by someone else - please refresh.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("ux_request_handler_active")) {
            return respond(ErrorCode.ALREADY_ASSIGNED, 409, "This request is already assigned.", request, null);
        }
        log.warn("Data integrity violation on {}: {}", LogSafe.of(request.getRequestURI()), LogSafe.of(constraintOf(ex)));
        return respond(ErrorCode.CONFLICT, 409, "This change conflicts with existing data.", request, null);
    }

    /**
     * D-40: only the constraint name (or the SQL state) goes into the log. The database message itself
     * repeats the duplicate value, e.g. "Key (email)=(someone@example.com) already exists" (POPIA).
     */
    static String constraintOf(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException c && c.getConstraintName() != null) {
                return "constraint " + c.getConstraintName();
            }
            if (t instanceof SQLException s) {
                return "SQL state " + s.getSQLState();
            }
        }
        return "no database detail";
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<Object> handleDatabaseDown(Exception ex, HttpServletRequest request) {
        log.error("Database unavailable on {}", LogSafe.of(request.getRequestURI()), ex);
        return respond(ErrorCode.SERVICE_UNAVAILABLE, 503,
                "The service is temporarily unavailable. Please try again later.", request, null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Object> handleAccessDenied(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, 403, "You do not have access.", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, 401, "Please log in.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", LogSafe.of(request.getMethod()), LogSafe.of(request.getRequestURI()), ex);
        return respond(ErrorCode.INTERNAL_ERROR, 500, "Something went wrong. Please try again.", request, null);
    }

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
