package com.civicconnect.controller;

import com.civicconnect.dto.AssignmentRequest;
import com.civicconnect.dto.RequestDetail;
import com.civicconnect.service.RequestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** API-08: assigning a handler to a request (FR-008). Handler rows are never edited or deleted directly. */
@RestController
@RequestMapping("/api/v1/requests/{id}/assignment")
public class RequestHandlerController {

    private final RequestService requestService;

    public RequestHandlerController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGEMENT')")
    public RequestDetail assign(@PathVariable Long id, @RequestBody(required = false) AssignmentRequest body) {
        return requestService.assign(id, body);
    }
}
