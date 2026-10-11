package com.civicconnect.dto;

import com.civicconnect.model.ServiceRequest;

import java.time.LocalDateTime;

/** One row in a request list (API-05, API-07). */
public record RequestSummary(
        Long id,
        String refNum,
        Long categoryId,
        String categoryName,
        ServiceRequest.Status status,
        String location,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt
) {
    public static RequestSummary from(ServiceRequest r) {
        return new RequestSummary(r.getReqId(), r.getRefNum(), r.getCategory().getCatId(),
                r.getCategory().getCatName(), r.getStatus(), r.getLocation(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getClosedAt());
    }
}
