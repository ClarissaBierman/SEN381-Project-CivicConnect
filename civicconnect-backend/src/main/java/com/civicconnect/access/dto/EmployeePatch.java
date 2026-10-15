package com.civicconnect.access.dto;

import com.civicconnect.common.validation.MaxBytes;
import com.civicconnect.access.domain.Employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import com.civicconnect.common.validation.NotBlankIfPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EmployeePatch(
        @NotBlankIfPresent @Size(max = 100) String firstName,
        @NotBlankIfPresent @Size(max = 100) String surname,
        @NotBlankIfPresent @Email @Size(max = 255) String email,
        @Size(min = 8, max = 72) @MaxBytes(72) String password,
        @Size(max = 30) String phone,
        Employee.Role role,
        Boolean active,
        @Past LocalDate dateOfBirth,
        @Size(max = 255) String street,
        @Size(max = 100) String area,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode
) {}
