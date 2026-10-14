package com.civicconnect.access.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HandlerCategoriesUpdate(@NotNull List<@NotNull Long> categoryIds) {}
