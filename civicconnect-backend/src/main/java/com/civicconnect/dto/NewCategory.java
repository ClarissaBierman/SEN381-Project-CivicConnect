package com.civicconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API-14 body. */
public record NewCategory(@NotBlank @Size(max = 100) String catName) {}
