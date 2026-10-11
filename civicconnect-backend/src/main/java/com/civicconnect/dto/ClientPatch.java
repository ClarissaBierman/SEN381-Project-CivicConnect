package com.civicconnect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body for a requester updating their own profile. Every field is optional. */
public record ClientPatch(
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 100) String surname,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Past LocalDate dateOfBirth,
        @Size(min = 8, max = 72) String password
) {}
