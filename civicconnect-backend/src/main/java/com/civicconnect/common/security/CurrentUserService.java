package com.civicconnect.common.security;

import com.civicconnect.common.error.Errors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {

    public CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw Errors.unauthenticated("Please log in.");
        }
        return new CurrentUser(
                Long.valueOf(jwt.getSubject()),
                CurrentUser.Role.valueOf(jwt.getClaimAsString(JwtService.ROLE_CLAIM)));
    }
}
