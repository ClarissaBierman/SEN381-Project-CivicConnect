package com.civicconnect.web;

import com.civicconnect.error.SecurityProblemHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;

/** 401 and 403 from the security filters use the same error format as everything else. */
class SecurityErrorFormatTest {

    private final SecurityProblemHandler handler = new SecurityProblemHandler();

    @Test
    @DisplayName("No token: 401 UNAUTHENTICATED with a WWW-Authenticate header")
    void missingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"UNAUTHENTICATED\"", "\"detail\":\"Please log in.\"", "/api/v1/categories");
    }

    @Test
    @DisplayName("Expired or tampered token: 401 asking the user to log in again")
    void invalidToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new InvalidBearerTokenException("expired"));

        assertThat(response.getContentAsString()).contains("Please log in again");
    }

    @Test
    @DisplayName("Wrong role: 403 FORBIDDEN")
    void wrongRole() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
