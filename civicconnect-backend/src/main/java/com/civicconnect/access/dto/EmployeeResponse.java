package com.civicconnect.access.dto;

import com.civicconnect.access.domain.Employee;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String firstName,
        String surname,
        String email,
        String phone,
        Employee.Role role,
        boolean active,
        LocalDate dateOfBirth,
        String street,
        String area,
        String city,
        String postalCode,
        LocalDateTime createdAt
) {
    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(e.getEmpId(), e.getFirstName(), e.getSurname(), e.getEmail(), e.getPhone(),
                e.getRole(), e.isActive(), e.getDateOfBirth(), e.getStreet(), e.getArea(), e.getCity(),
                e.getPostalCode(), e.getCreatedAt());
    }
}
