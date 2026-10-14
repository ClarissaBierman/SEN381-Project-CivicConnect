package com.civicconnect.requests.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NewServiceRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @Size(max = 255) String location,
        @Size(max = 500) @Pattern(regexp = "^https?://.+$", message = "must be an http or https link") String attachmentUrl
) {}
