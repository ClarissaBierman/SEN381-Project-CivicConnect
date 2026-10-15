package com.civicconnect.common.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class FilterErrorHandlerTest {

    private final FilterErrorHandler filter = new FilterErrorHandler(new ProblemWriter());

    private MockHttpServletResponse run(Exception thrown) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        doThrow(thrown).when(chain).doFilter(any(), any());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/requests"), response, chain);
        return response;
    }

    @Test
    @DisplayName("Database down during the account check: 503 SERVICE_UNAVAILABLE with the PED body")
    void databaseDownIs503() throws Exception {
        MockHttpServletResponse response = run(new IllegalStateException("wrapped",
                new CannotCreateTransactionException("Could not open JPA EntityManager")));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    @DisplayName("Any other filter error: 500 INTERNAL_ERROR without internal details")
    void otherErrorIs500() throws Exception {
        MockHttpServletResponse response = run(new ServletException("boom: secret detail"));
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("INTERNAL_ERROR").doesNotContain("secret detail");
    }

    @Test
    @DisplayName("If the response was already sent, the error is passed on")
    void committedResponseRethrows() throws Exception {
        FilterChain chain = (req, res) -> {
            res.getWriter().write("partial");
            res.flushBuffer();
            throw new IllegalStateException("late");
        };
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
                .hasMessage("late");
    }
}
