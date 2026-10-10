package com.civicconnect.controller;

import com.civicconnect.dto.AuditEntryResponse;
import com.civicconnect.service.AuditService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only access to the audit trail. Audit rows are written by the database trigger (DEC-007),
 * never through the API, so there are no POST, PATCH or DELETE endpoints.
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
    public List<AuditEntryResponse> requestHistory(@PathVariable Long id) {
        return auditService.forRequest(id);
    }

    /** Management search over all audit entries, newest first. e.g. /api/v1/audit?table=employee&operation=UPDATE */
    @GetMapping("/audit")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public PagedModel<AuditEntryResponse> search(
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String recordId,
            @RequestParam(required = false) String operation,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(auditService.search(table, recordId, operation, pageable));
    }

    @GetMapping("/audit/{auditId}")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public AuditEntryResponse get(@PathVariable Long auditId) {
        return auditService.get(auditId);
    }
}
