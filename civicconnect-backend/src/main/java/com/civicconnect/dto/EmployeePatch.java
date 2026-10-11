package com.civicconnect.dto;

import com.civicconnect.model.Employee;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body for Management updating an employee. Every field is optional; null means "leave unchanged". */
public record EmployeePatch(
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 100) String surname,
        @Email @Size(max = 255) String email,
        @Size(min = 8, max = 72) String password,
        @Size(max = 30) String phone,
        Employee.Role role,
        Boolean active,
        @Past LocalDate dateOfBirth,
        @Size(max = 255) String street,
        @Size(max = 100) String area,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode
) {}
