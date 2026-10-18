package com.civicconnect.access.dto;

import com.civicconnect.common.time.Time;
import com.civicconnect.access.domain.Client;

import java.time.Instant;

public record ClientResponse(
        Long id,
        String firstName,
        String surname,
        String email,
        String phone,
        boolean active,
        Instant createdAt
) {
    public static ClientResponse from(Client c) {
        return new ClientResponse(c.getClientId(), c.getFirstName(), c.getSurname(), c.getEmail(), c.getPhone(),
                c.isActive(), Time.toInstant(c.getCreatedAt()));
    }
}
