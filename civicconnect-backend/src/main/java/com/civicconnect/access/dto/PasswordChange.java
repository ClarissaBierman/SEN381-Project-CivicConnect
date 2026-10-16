package com.civicconnect.access.dto;

import com.civicconnect.common.validation.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An employee changing their own password (API-26). The current password is always required (B3). */
public record PasswordChange(
        @NotBlank @MaxBytes(72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) @MaxBytes(72) String password
) {}
