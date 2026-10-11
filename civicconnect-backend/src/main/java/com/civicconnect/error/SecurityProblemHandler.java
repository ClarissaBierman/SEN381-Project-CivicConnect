package com.civicconnect.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Writes 401 and 403 errors that happen in the security filters (before any controller runs)
 * in the same ApiError format as GlobalExceptionHandler.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json = JsonMapper.builder().build();

    /** 401: no token, or the token is expired, tampered with or signed with another secret. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        String detail = ex instanceof InvalidBearerTokenException
                ? "Your session has expired or is invalid. Please log in again."
                : "Please log in.";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, ErrorCode.UNAUTHENTICATED, 401, detail, request);
    }

    /** 403: logged in, but not allowed. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, ErrorCode.FORBIDDEN, 403, "You do not have access.", request);
    }

    private void write(HttpServletResponse response, ErrorCode code, int status, String detail,
                       HttpServletRequest request) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ApiError.of(code, status, detail, request.getRequestURI(), null));
    }
}
