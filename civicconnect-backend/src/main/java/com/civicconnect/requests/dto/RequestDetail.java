package com.civicconnect.requests.dto;

import com.civicconnect.requests.domain.ServiceRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * API-06. Times are UTC instants. handlerId/handlerName and allowedTransitions are only filled for
 * staff and managers; requesters see the status, their own fields and comments meant for them.
 * statusHistory lists every status the request has had, oldest first, with no names (FR-003, FR-007).
 * allowedTransitions is empty for staff who may not move the request (not its handler).
 */
public record RequestDetail(
        Long id,
        String refNum,
        Long categoryId,
        String categoryName,
        String description,
        String location,
        String attachmentUrl,
        ServiceRequest.Status status,
        ServiceRequest.Priority priority,
        Long requesterId,
        Long handlerId,
        String handlerName,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        Set<ServiceRequest.Status> allowedTransitions,
        List<CommentResponse> comments,
        List<StatusChange> statusHistory
) {}
