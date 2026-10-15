package com.civicconnect.access.dto;

import com.civicconnect.common.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A requester's own profile changes. Only the fields sent are changed.
 * Changing the email or password also needs currentPassword (B3), so a stolen session token alone
 * cannot take the account over.
 */
public record ClientPatch(
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 100) String surname,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Past LocalDate dateOfBirth,
        @Size(min = 8, max = 72) @MaxBytes(72) String password,
        @MaxBytes(72) String currentPassword
) {}
