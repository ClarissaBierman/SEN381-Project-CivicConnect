package com.civicconnect.dto;

import com.civicconnect.model.ServiceRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * API-06 response. handlerId and allowedTransitions are only filled for staff and managers;
 * requesters only receive comments marked visible to them (FR-002).
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
        Long requesterId,
        Long handlerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt,
        Set<ServiceRequest.Status> allowedTransitions,
        List<CommentResponse> comments
) {}
