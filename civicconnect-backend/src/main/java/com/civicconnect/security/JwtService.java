package com.civicconnect.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Issues signed JWTs (HS256) at login - DEC-010. */
@Service
public class JwtService {

    public static final String ROLE_CLAIM = "role";
    private static final String ISSUER = "civicconnect";

    private final JwtEncoder encoder;
    private final long expiryMinutes;

    public JwtService(JwtEncoder encoder, @Value("${civicconnect.jwt.expiry-minutes:60}") long expiryMinutes) {
        this.encoder = encoder;
        this.expiryMinutes = expiryMinutes;
    }

    public IssuedToken issue(CurrentUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLE_CLAIM, user.role().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
