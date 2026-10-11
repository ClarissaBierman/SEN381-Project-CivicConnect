package com.civicconnect.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** API-16 PUT body: the full list of categories the handler should cover (replaces the old list). */
public record HandlerCategoriesUpdate(@NotNull List<@NotNull Long> categoryIds) {}
