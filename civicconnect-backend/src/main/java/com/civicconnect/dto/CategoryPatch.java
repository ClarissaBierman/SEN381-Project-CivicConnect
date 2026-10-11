package com.civicconnect.dto;

import jakarta.validation.constraints.Size;

/** API-15 body. Both fields are optional; null means "leave unchanged". */
public record CategoryPatch(
        @Size(min = 1, max = 100) String catName,
        Boolean active
) {}
