package com.civicconnect.requests.dto;

import com.civicconnect.common.time.Time;
import com.civicconnect.requests.domain.ServiceRequest;

import java.time.Instant;

/** One row in API-05 (own requests) and API-07 (staff list). Handler fields are null for requesters. */
public record RequestSummary(
        Long id,
        String refNum,
        Long categoryId,
        String categoryName,
        ServiceRequest.Status status,
        ServiceRequest.Priority priority,
        String location,
        Long handlerId,
        String handlerName,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
    public static RequestSummary from(ServiceRequest r, Long handlerId, String handlerName) {
        return new RequestSummary(r.getReqId(), r.getRefNum(), r.getCategory().getCatId(),
                r.getCategory().getCatName(), r.getStatus(), r.getPriority(), r.getLocation(), handlerId, handlerName,
                Time.toInstant(r.getCreatedAt()), Time.toInstant(r.getUpdatedAt()), Time.toInstant(r.getClosedAt()));
    }

    /** For requesters: no handler details (ASR-005). */
    public static RequestSummary from(ServiceRequest r) {
        return from(r, null, null);
    }
}
