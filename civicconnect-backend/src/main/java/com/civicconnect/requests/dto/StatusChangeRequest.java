package com.civicconnect.requests.dto;

import com.civicconnect.requests.domain.ServiceRequest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(
        @NotNull ServiceRequest.Status toStatus,
        @Size(max = 2000) String note,
        Boolean noteVisibleToRequester
) {}
