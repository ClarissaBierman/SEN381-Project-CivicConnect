package com.civicconnect.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 and 403 from the security filters, in the same error format as the controllers. */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemWriter writer;

    public SecurityProblemHandler(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        String detail;
        if (ex instanceof DisabledException) {
            detail = "Your account is no longer active. Please contact CivicConnect.";
        } else if (ex instanceof InvalidBearerTokenException) {
            detail = "Your session has expired or is invalid. Please log in again.";
        } else {
            detail = "Please log in.";
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(response, ErrorCode.UNAUTHENTICATED, detail, request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        writer.write(response, ErrorCode.FORBIDDEN, "You do not have access.", request);
    }
}
