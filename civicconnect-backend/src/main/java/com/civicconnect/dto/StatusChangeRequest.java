package com.civicconnect.dto;

import com.civicconnect.model.ServiceRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API-09 body. An optional note is saved as a comment in the same transaction. */
public record StatusChangeRequest(
        @NotNull ServiceRequest.Status toStatus,
        @Size(max = 2000) String note,
        Boolean noteVisibleToRequester
) {}
