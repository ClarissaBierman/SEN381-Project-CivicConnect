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
import java.util.Set;

/**
 * Request rate limits (B4), checked in this order - a request must pass every one that applies:
 *
 *  1. per IP address, every API call:        stops one machine flooding the API with many accounts
 *  2. per IP address, login and registration: stops one machine trying many emails (credential stuffing)
 *                                             and tying up the CPU with password hashing
 *  3. per account, every authenticated call:  the account in the token (role + id), so the same account
 *                                             on several devices, networks or VPNs shares one allowance
 * Failed passwords are also counted per email address in AuthService (LoginAttemptLimiter), so an
 * attacker cannot get around the email lock by changing IP address, and cannot get around the IP limits
 * by changing email address.
 *
 * The IP address is request.getRemoteAddr(). Behind a reverse proxy, configure the proxy as trusted
 * (server.forward-headers-strategy) so this is the real client address. The X-Forwarded-For header is
 * never read directly: a client can put anything in it and get a fresh limit with every request.
 *
 * Registered twice in the security filter chain (SecurityConfig), once per stage:
 *  - BEFORE_AUTHENTICATION (limits 1 and 2), before the JWT is checked, so calls with a missing, expired,
 *    forged or deactivated token also count against the IP address and cannot be sent without limit;
 *  - AFTER_AUTHENTICATION (limit 3), right after the JWT is verified, when the account is known.
 * Not a @Component on purpose: Spring Boot would otherwise also register it as a plain servlet filter.
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    /** Which limits this instance checks. ALL is for tests that exercise every limit in one filter. */
    public enum Stage { BEFORE_AUTHENTICATION, AFTER_AUTHENTICATION, ALL }

    private static final Set<String> AUTH_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");

    private final TokenBucketLimiter perAccount;
    private final TokenBucketLimiter perIp;
    private final TokenBucketLimiter authPerIp;
    private final ProblemWriter writer;
    private final Stage stage;

    public ApiRateLimitFilter(TokenBucketLimiter perAccount, TokenBucketLimiter perIp, TokenBucketLimiter authPerIp,
                              ProblemWriter writer) {
        this(perAccount, perIp, authPerIp, writer, Stage.ALL);
    }

    public ApiRateLimitFilter(TokenBucketLimiter perAccount, TokenBucketLimiter perIp, TokenBucketLimiter authPerIp,
                              ProblemWriter writer, Stage stage) {
        this.perAccount = perAccount;
        this.perIp = perIp;
        this.authPerIp = authPerIp;
        this.writer = writer;
        this.stage = stage;
    }

    /** Each stage is its own "once per request" filter, so the second instance is not skipped. */
    @Override
    protected String getAlreadyFilteredAttributeName() {
        return ApiRateLimitFilter.class.getName() + "." + stage + ".FILTERED";
    }

    /**
     * The per-IP limits cover every path, including the Angular files and made-up URLs (D-30), so one machine
     * cannot send unlimited requests anywhere. The per-account limit only applies to the API.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return stage == Stage.AFTER_AUTHENTICATION && !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (stage != Stage.AFTER_AUTHENTICATION) {
            String ip = request.getRemoteAddr();
            if (refused(perIp.tryConsume(ip), "Too many requests from your network.", request, response)) {
                return;
            }
            if ("POST".equals(request.getMethod()) && AUTH_PATHS.contains(request.getRequestURI())
                    && refused(authPerIp.tryConsume(ip), "Too many sign-in or registration attempts from your network.",
                    request, response)) {
                return;
            }
        }
        Authentication auth = stage == Stage.BEFORE_AUTHENTICATION ? null
                : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String account = jwt.getClaimAsString(JwtService.ROLE_CLAIM) + ":" + jwt.getSubject();
            if (refused(perAccount.tryConsume(account), "Too many requests.", request, response)) {
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean refused(long retryAfter, String reason, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (retryAfter <= 0) return false;
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        writer.write(response, ErrorCode.TOO_MANY_REQUESTS,
                reason + " Please wait " + retryAfter + " seconds and try again.", request);
        return true;
    }
}
