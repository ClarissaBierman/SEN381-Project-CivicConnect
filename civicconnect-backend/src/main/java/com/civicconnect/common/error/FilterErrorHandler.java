package com.civicconnect.common.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.SQLTransientConnectionException;

/**
 * Errors thrown by the security filters (before any controller runs) get the PED error body too.
 * The main case: every logged-in call checks the account in the database (B1), so if the database is
 * down the call fails inside the filter chain. Without this it became Spring's default 500 page; now it
 * is 503 SERVICE_UNAVAILABLE, the same as when a controller hits the outage. Anything else is a 500
 * INTERNAL_ERROR with no internal details. Errors inside controllers never get here - GlobalExceptionHandler
 * answers those.
 */
public class FilterErrorHandler extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FilterErrorHandler.class);

    private final ProblemWriter writer;

    public FilterErrorHandler(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException | ServletException e) {
            if (response.isCommitted()) {
                throw e;
            }
            response.resetBuffer();
            if (isDatabaseUnavailable(e)) {
                log.warn("Database unavailable during {} {}: {}", request.getMethod(), request.getRequestURI(), e.toString());
                writer.write(response, ErrorCode.SERVICE_UNAVAILABLE,
                        "The service is temporarily unavailable. Please try again later.", request);
            } else {
                log.error("Unhandled error in the filter chain for {} {}", request.getMethod(), request.getRequestURI(), e);
                writer.write(response, ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.", request);
            }
        }
    }

    static boolean isDatabaseUnavailable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof CannotCreateTransactionException
                    || t instanceof DataAccessResourceFailureException
                    || t instanceof TransientDataAccessException
                    || t instanceof SQLTransientConnectionException) {
                return true;
            }
        }
        return false;
    }
}
