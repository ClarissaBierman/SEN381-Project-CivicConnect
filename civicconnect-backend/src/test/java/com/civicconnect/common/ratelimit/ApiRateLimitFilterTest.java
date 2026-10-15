package com.civicconnect.common.ratelimit;

import com.civicconnect.common.error.ProblemWriter;
import com.civicconnect.support.MutableClock;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** B4: per-IP and per-account limits together (small limits so the boundaries are easy to see). */
class ApiRateLimitFilterTest {

    private final MutableClock clock = new MutableClock();
    private final ApiRateLimitFilter filter = new ApiRateLimitFilter(
            new TokenBucketLimiter(3, Duration.ofMinutes(1), clock),   // per account
            new TokenBucketLimiter(5, Duration.ofMinutes(1), clock),   // per IP, every call
            new TokenBucketLimiter(2, Duration.ofMinutes(1), clock),   // per IP, login/register
            new ProblemWriter());

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private int send(String method, String path, String ip, Long accountId) throws Exception {
        SecurityContextHolder.clearContext();
        if (accountId != null) {
            Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(accountId))
                    .claim("role", "REQUESTER").build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        }
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response.getStatus();
    }

    @Test
    @DisplayName("One account is limited whatever IP it comes from (other devices or a VPN do not help)")
    void accountLimitFollowsTheAccount() throws Exception {
        assertThat(send("GET", "/api/v1/requests/mine", "10.0.0.1", 7L)).isEqualTo(200);
        assertThat(send("GET", "/api/v1/requests/mine", "10.0.0.2", 7L)).isEqualTo(200);
        assertThat(send("GET", "/api/v1/requests/mine", "10.0.0.3", 7L)).isEqualTo(200);
        assertThat(send("GET", "/api/v1/requests/mine", "10.0.0.4", 7L)).isEqualTo(429);
    }

    @Test
    @DisplayName("One IP is limited whatever accounts it uses (many accounts from one machine)")
    void ipLimitCoversManyAccounts() throws Exception {
        for (long account = 1; account <= 5; account++) {
            assertThat(send("GET", "/api/v1/categories", "10.0.0.9", account)).isEqualTo(200);
        }
        assertThat(send("GET", "/api/v1/categories", "10.0.0.9", 6L)).isEqualTo(429);
        assertThat(send("GET", "/api/v1/categories", "10.0.0.10", 6L)).as("another IP is unaffected").isEqualTo(200);
    }

    @Test
    @DisplayName("Login and registration have a tighter per-IP limit, so one machine cannot try many emails")
    void authAttemptsPerIp() throws Exception {
        assertThat(send("POST", "/api/v1/auth/login", "10.0.0.5", null)).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/register", "10.0.0.5", null)).isEqualTo(200);
        MockHttpServletResponse refused = new MockHttpServletResponse();
        MockHttpServletRequest third = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        third.setRemoteAddr("10.0.0.5");
        filter.doFilter(third, refused, new MockFilterChain());

        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isNotBlank();
        assertThat(refused.getContentAsString()).contains("TOO_MANY_REQUESTS", "sign-in");
        assertThat(send("GET", "/api/v1/categories", "10.0.0.5", 1L)).as("other calls only use the general limit")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Allowance comes back with time")
    void refills() throws Exception {
        for (int i = 0; i < 3; i++) send("GET", "/api/v1/requests/mine", "10.0.0.1", 8L);
        assertThat(send("GET", "/api/v1/requests/mine", "10.0.0.1", 8L)).isEqualTo(429);

        clock.advance(Duration.ofSeconds(20));
        assertThat(send("GET", "/api/v1/requests/mine", "10.0.0.1", 8L)).isEqualTo(200);
    }
}
