package com.civicconnect.reporting.controller;

import com.civicconnect.common.error.Errors;
import com.civicconnect.common.web.SortGuard;
import com.civicconnect.reporting.domain.AuditResource;
import com.civicconnect.reporting.dto.AuditEntryResponse;
import com.civicconnect.reporting.dto.RequestHistoryEntry;
import com.civicconnect.reporting.service.AuditService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to the audit trail. Audit rows are written by the database trigger (DEC-007),
 * never through the API, so there are no POST, PATCH or DELETE endpoints.
 *
 * Split per table: each audited table has its own endpoint, and each endpoint only returns rows of
 * its table, shaped for it. /audit/clients never includes client personal data (ASR-005).
 *
 *   GET /requests/{id}/audit                     history of one request (staff in scope, managers)
 *   GET /audit/{resource}?recordId=&operation=   one table's entries, newest first (managers)
 *   GET /audit/{resource}/{auditId}              one entry of that table (managers)
 *
 * resource = requests | assignments | comments | categories | handler-categories | employees | clients
 */
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** API-11: full history of one request, oldest first (FR-007, NFR-006). */
    @GetMapping("/requests/{id}/audit")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGEMENT')")
    public List<RequestHistoryEntry> requestHistory(@PathVariable Long id) {
        return auditService.forRequest(id);
    }

    /** e.g. /api/v1/audit/employees?recordId=5&operation=UPDATE */
    @GetMapping("/audit/{resource}")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public PagedModel<AuditEntryResponse> search(
            @PathVariable String resource,
            @RequestParam(required = false) String recordId,
            @RequestParam(required = false) String operation,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(auditService.search(resource(resource), recordId, operation,
                SortGuard.allow(pageable, "changedAt", "audId", "operation", "recId")));
    }

    @GetMapping("/audit/{resource}/{auditId}")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public AuditEntryResponse get(@PathVariable String resource, @PathVariable Long auditId) {
        return auditService.get(resource(resource), auditId);
    }

    private static AuditResource resource(String slug) {
        return AuditResource.fromSlug(slug).orElseThrow(() -> Errors.invalidField("resource",
                "Unknown audit resource '" + slug + "'. Use one of: " + AuditResource.slugs() + "."));
    }
}
