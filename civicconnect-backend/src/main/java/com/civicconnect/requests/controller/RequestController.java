package com.civicconnect.requests.controller;

import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.common.web.SortGuard;
import com.civicconnect.requests.dto.NewServiceRequest;
import com.civicconnect.requests.dto.PriorityChange;
import com.civicconnect.requests.dto.RequestDetail;
import com.civicconnect.requests.dto.RequestSummary;
import com.civicconnect.requests.dto.StatusChangeRequest;
import com.civicconnect.requests.service.RequestService;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * API-04 to API-07 and API-09. There is no general update or delete endpoint: requests change only
 * through the status and assignment endpoints (DEC-011), and are never deleted.
 */
@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {

    /** Fields a request list may be sorted by (anything else is a 400 - see SortGuard). */
    static final String[] SORTABLE = {"createdAt", "updatedAt", "closedAt", "refNum", "status", "priority"};

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /** API-04 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('REQUESTER')")
    public RequestDetail create(@Valid @RequestBody NewServiceRequest body) {
        return requestService.create(body);
    }

    /** API-05 */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('REQUESTER')")
    public PagedModel<RequestSummary> mine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(requestService.mine(SortGuard.allow(pageable, SORTABLE)));
    }

    /** API-06 */
    @GetMapping("/{id}")
    public RequestDetail get(@PathVariable Long id) {
        return requestService.get(id);
    }

    /** API-07 e.g. GET /api/v1/requests?status=NEW&categoryId=2&from=2026-10-01&to=2026-10-31&page=0&size=20 */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGEMENT')")
    public PagedModel<RequestSummary> list(
            @RequestParam(required = false) ServiceRequest.Status status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(requestService.list(status, categoryId, from, to, SortGuard.allow(pageable, SORTABLE)));
    }

    /** API-09 */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGEMENT')")
    public RequestDetail changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest body) {
        return requestService.changeStatus(id, body);
    }

    /** API-12: Management sets the priority (FR-016). */
    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public RequestDetail setPriority(@PathVariable Long id, @Valid @RequestBody PriorityChange body) {
        return requestService.setPriority(id, body.priority());
    }
}
