package com.civicconnect.requests.dto;

import com.civicconnect.requests.domain.ServiceRequest;
import jakarta.validation.constraints.NotNull;

/** API-12 body. */
public record PriorityChange(@NotNull ServiceRequest.Priority priority) {}
