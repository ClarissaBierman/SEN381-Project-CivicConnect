package com.civicconnect.requests.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewComment(
        @NotBlank @Size(max = 2000) String content,
        Boolean visibleToRequester
) {}
