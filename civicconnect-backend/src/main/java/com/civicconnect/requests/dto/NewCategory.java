package com.civicconnect.requests.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewCategory(@NotBlank @Size(max = 100) String catName) {}
