package com.civicconnect.controller;

import com.civicconnect.dto.ActivePatch;
import com.civicconnect.dto.ClientPatch;
import com.civicconnect.dto.ClientResponse;
import com.civicconnect.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Client accounts. A requester views and edits only their own profile. Management can only
 * activate or deactivate an account (no listing of client data - ASR-005). No DELETE.
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

    @PatchMapping("/{id}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGEMENT')")
    public void setActive(@PathVariable Long id, @Valid @RequestBody ActivePatch body) {
        clientService.setActive(id, body.active());
    }
}
