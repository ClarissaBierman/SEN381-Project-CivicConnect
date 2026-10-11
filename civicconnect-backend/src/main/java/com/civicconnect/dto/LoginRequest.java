package com.civicconnect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** API-02 body. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
