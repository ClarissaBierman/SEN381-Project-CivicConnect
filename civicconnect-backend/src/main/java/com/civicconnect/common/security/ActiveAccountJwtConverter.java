package com.civicconnect.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Turns a verified JWT into the logged-in user - but only after checking the database (B1):
 *  - the account still exists and is active, otherwise 401 "Your account is no longer active";
 *  - the role in the token is still the account's role, otherwise 401 "please log in again"
 *    (a demoted manager must not keep management rights until the token expires).
 * One primary-key lookup per request. Runs for every authenticated call, so no endpoint can forget it.
 */
public class ActiveAccountJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AccountStatusLookup accounts;

    public ActiveAccountJwtConverter(AccountStatusLookup accounts) {
        this.accounts = accounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        CurrentUser.Role tokenRole;
        long id;
        try {
            tokenRole = CurrentUser.Role.valueOf(jwt.getClaimAsString(JwtService.ROLE_CLAIM));
            id = Long.parseLong(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Token has no valid subject or role");
        }

        AccountStatusLookup.AccountState state = accounts.find(tokenRole, id)
                .orElseThrow(() -> new InvalidBearerTokenException("Account no longer exists"));
        if (!state.active()) {
            throw new DisabledException("Account is deactivated");
        }
        if (state.role() != tokenRole) {
            throw new InvalidBearerTokenException("Role changed since the token was issued");
        }

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + tokenRole.name()));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
