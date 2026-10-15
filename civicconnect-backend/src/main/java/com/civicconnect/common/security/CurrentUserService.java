package com.civicconnect.common.security;

import com.civicconnect.common.error.Errors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserService {

    /** The logged-in user, or 401 UNAUTHENTICATED if nobody is logged in. */
    public CurrentUser get() {
        return find().orElseThrow(() -> Errors.unauthenticated("Please log in."));
    }

    /** The logged-in user, if there is one. */
    public Optional<CurrentUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(
                Long.valueOf(jwt.getSubject()),
                CurrentUser.Role.valueOf(jwt.getClaimAsString(JwtService.ROLE_CLAIM))));
    }
}
