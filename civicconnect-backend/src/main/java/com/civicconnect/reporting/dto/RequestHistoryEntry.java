package com.civicconnect.reporting.dto;

import com.civicconnect.requests.domain.ServiceRequest;

import java.time.Instant;

/**
 * API-11: one step in a request's history (FR-007, NFR-006), described for people:
 * who did it, what they did, and the status before and after when the status changed.
 */
public record RequestHistoryEntry(
        Long audId,
        Instant at,
        String actor,
        Long actorEmpId,
        Long actorClientId,
        String action,
        ServiceRequest.Status fromStatus,
        ServiceRequest.Status toStatus
) {}
