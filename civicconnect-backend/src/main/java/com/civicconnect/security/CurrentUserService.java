package com.civicconnect.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Reads the logged-in user from the validated JWT. */
@Component
public class CurrentUserService {

    public CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        return new CurrentUser(
                Long.valueOf(jwt.getSubject()),
                CurrentUser.Role.valueOf(jwt.getClaimAsString(JwtService.ROLE_CLAIM)));
    }
}
