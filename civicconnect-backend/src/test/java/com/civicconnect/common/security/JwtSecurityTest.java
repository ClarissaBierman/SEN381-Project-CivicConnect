package com.civicconnect.common.security;

import com.civicconnect.common.config.SecurityConfig;
import com.civicconnect.common.error.ErrorCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static com.civicconnect.support.TestData.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** DEC-010: tokens are signed with JWT_SECRET, expire after 60 minutes and cannot be forged or edited. */
class JwtSecurityTest {

    private final SecurityConfig config = new SecurityConfig();
    private final SecretKey key = config.jwtSigningKey("unit-test-secret-unit-test-secret-1234");
    private final JwtService jwtService = new JwtService(config.jwtEncoder(key), 60);
    private final JwtDecoder decoder = config.jwtDecoder(key);

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A token carries the user ID and role, and expires after 60 minutes")
    void tokenContents() {
        JwtService.IssuedToken issued = jwtService.issue(new CurrentUser(3L, CurrentUser.Role.STAFF));

        Jwt jwt = decoder.decode(issued.token());
        assertThat(jwt.getSubject()).isEqualTo("3");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STAFF");
        assertThat(Duration.between(Instant.now(), jwt.getExpiresAt()).toMinutes()).isBetween(58L, 60L);
    }

    @Test
    @DisplayName("A token signed with a different secret is rejected")
    void otherSecretRejected() {
        SecretKey otherKey = config.jwtSigningKey("another-secret-another-secret-123456");
        String forged = new JwtService(config.jwtEncoder(otherKey), 60)
                .issue(new CurrentUser(1L, CurrentUser.Role.MANAGEMENT)).token();

        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    @Test
    @DisplayName("Editing the token (e.g. changing the role) breaks the signature")
    void editedTokenRejected() {
        String token = jwtService.issue(new CurrentUser(1L, CurrentUser.Role.REQUESTER)).token();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("REQUESTER", "MANAGEMENT");
        String edited = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
                + "." + parts[2];

        assertThrows(JwtException.class, () -> decoder.decode(edited));
    }

    @Test
    @DisplayName("JWT_SECRET must be at least 32 characters")
    void shortSecretRefused() {
        assertThrows(IllegalStateException.class, () -> config.jwtSigningKey("too-short"));
    }

    @Test
    @DisplayName("The current user comes from the verified token")
    void currentUserFromToken() {
        Jwt jwt = decoder.decode(jwtService.issue(new CurrentUser(7L, CurrentUser.Role.REQUESTER)).token());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(new CurrentUserService().get()).isEqualTo(new CurrentUser(7L, CurrentUser.Role.REQUESTER));
    }

    @Test
    @DisplayName("Without a token there is no current user")
    void noTokenNoUser() {
        assertApiError(ErrorCode.UNAUTHENTICATED, () -> new CurrentUserService().get());
    }

    @Test
    @DisplayName("A token signed with our secret but issued by someone else (iss) is rejected")
    void otherIssuerRejected() {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("someone-else").subject("1").claim("role", "MANAGEMENT")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(Duration.ofMinutes(5))).build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader.with(
                org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        String token = config.jwtEncoder(key).encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
}
