package com.civicconnect.dto;

import java.time.Instant;

/** API-02 response: the JWT the Angular app sends as "Authorization: Bearer ..." */
public record AuthResponse(String token, Instant expiresAt, String role) {}
