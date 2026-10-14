package com.civicconnect.common.ratelimit;

import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.error.ProblemWriter;
import com.civicconnect.common.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limits every authenticated API call per account (B4). Runs inside the security filter chain, right
 * after the JWT has been verified, so the key is the account in the token (role + id) - the same
 * account on several devices, networks or VPNs shares one allowance.
 *
 * Not a @Component on purpose: Spring Boot would otherwise also register it as a plain servlet filter
 * that runs before authentication. SecurityConfig adds it to the chain.
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final TokenBucketLimiter limiter;
    private final ProblemWriter writer;

    public ApiRateLimitFilter(TokenBucketLimiter limiter, ProblemWriter writer) {
        this.limiter = limiter;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String key = jwt.getClaimAsString(JwtService.ROLE_CLAIM) + ":" + jwt.getSubject();
            long retryAfter = limiter.tryConsume(key);
            if (retryAfter > 0) {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
                writer.write(response, ErrorCode.TOO_MANY_REQUESTS,
                        "Too many requests. Please wait " + retryAfter + " seconds and try again.", request);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
