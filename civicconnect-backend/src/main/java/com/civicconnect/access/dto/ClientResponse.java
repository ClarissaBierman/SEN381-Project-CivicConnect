package com.civicconnect.access.dto;

import com.civicconnect.access.domain.Client;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String firstName,
        String surname,
        String email,
        String phone,
        LocalDate dateOfBirth,
        boolean active,
        LocalDateTime createdAt
) {
    public static ClientResponse from(Client c) {
        return new ClientResponse(c.getClientId(), c.getFirstName(), c.getSurname(), c.getEmail(), c.getPhone(),
                c.getDateOfBirth(), c.isActive(), c.getCreatedAt());
    }
}
