package com.civicconnect.requests.dto;

import com.civicconnect.common.validation.NotBlankIfPresent;
import jakarta.validation.constraints.Size;

public record CategoryPatch(
        @NotBlankIfPresent @Size(max = 100) String catName,
        Boolean active
) {}
