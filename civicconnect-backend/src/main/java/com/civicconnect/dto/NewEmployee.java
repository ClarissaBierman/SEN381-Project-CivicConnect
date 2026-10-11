package com.civicconnect.dto;

import com.civicconnect.model.Employee;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body for Management creating an employee account. */
public record NewEmployee(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String surname,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 30) String phone,
        @NotNull Employee.Role role,
        @Past LocalDate dateOfBirth,
        @Size(max = 255) String street,
        @Size(max = 100) String area,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode
) {}
