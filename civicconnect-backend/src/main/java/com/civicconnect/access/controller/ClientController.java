package com.civicconnect.access.controller;

import com.civicconnect.access.dto.ActivePatch;
import com.civicconnect.access.dto.ClientContactResponse;
import com.civicconnect.access.dto.ClientPatch;
import com.civicconnect.access.dto.ClientResponse;
import com.civicconnect.access.service.ClientService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Client accounts. A requester views and edits only their own profile. Management can activate or
 * deactivate an account and look up one client's contact details to contact them about a request,
 * but cannot list clients or see their date of birth (ASR-005). No DELETE.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('REQUESTER')")
    public ClientResponse me() {
        return clientService.me();
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('REQUESTER')")
    public ClientResponse updateMe(@Valid @RequestBody ClientPatch body) {
        return clientService.updateMe(body);
    }

    /** Management: contact details of one client (name, email, phone). Each lookup is logged. */
    @GetMapping("/{id}/contact")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public ClientContactResponse contact(@PathVariable Long id) {
        return clientService.contact(id);
    }

    @PatchMapping("/{id}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGEMENT')")
    public void setActive(@PathVariable Long id, @Valid @RequestBody ActivePatch body) {
        clientService.setActive(id, body.active());
    }
}
