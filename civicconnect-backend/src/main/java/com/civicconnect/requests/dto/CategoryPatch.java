package com.civicconnect.requests.dto;

import jakarta.validation.constraints.Size;

public record CategoryPatch(
        @Size(min = 1, max = 100) String catName,
        Boolean active
) {}
