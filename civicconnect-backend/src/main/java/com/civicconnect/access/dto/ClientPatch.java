package com.civicconnect.access.dto;

import com.civicconnect.common.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import com.civicconnect.common.validation.NotBlankIfPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A requester's own profile changes. Only the fields sent are changed.
 * Changing the email or password also needs currentPassword (B3), so a stolen session token alone
 * cannot take the account over.
 */
public record ClientPatch(
        @NotBlankIfPresent @Size(max = 100) String firstName,
        @NotBlankIfPresent @Size(max = 100) String surname,
        @NotBlankIfPresent @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Past LocalDate dateOfBirth,
        @Size(min = 8, max = 72) @MaxBytes(72) String password,
        @MaxBytes(72) String currentPassword
) {}
