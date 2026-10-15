package com.civicconnect.access.dto;

import com.civicconnect.common.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank @MaxBytes(72) String password
) {}
