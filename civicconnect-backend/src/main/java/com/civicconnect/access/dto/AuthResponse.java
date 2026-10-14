package com.civicconnect.access.dto;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, String role) {}
