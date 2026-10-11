package com.civicconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API-10 body. The author is the logged-in employee, never sent in the body. */
public record NewComment(
        @NotBlank @Size(max = 2000) String content,
        Boolean visibleToRequester
) {}
