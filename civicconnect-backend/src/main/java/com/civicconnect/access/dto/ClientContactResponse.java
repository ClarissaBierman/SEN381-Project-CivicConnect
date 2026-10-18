package com.civicconnect.access.dto;

import com.civicconnect.access.repository.ClientRepository;

/** What Management may see of a client to contact them about a request: no password. */
public record ClientContactResponse(Long id, String firstName, String surname, String email, String phone, boolean active) {

    public static ClientContactResponse from(ClientRepository.ClientContact c) {
        return new ClientContactResponse(c.getId(), c.getFirstName(), c.getSurname(), c.getEmail(), c.getPhone(),
                c.getActive());
    }
}
