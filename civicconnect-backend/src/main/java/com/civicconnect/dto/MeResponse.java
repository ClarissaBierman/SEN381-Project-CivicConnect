package com.civicconnect.dto;

/** API-03 response: who is logged in. */
public record MeResponse(Long id, String role, String firstName, String surname, String email) {}
