package com.civicconnect.access.dto;

import jakarta.validation.constraints.NotNull;

/** Activate (true) or deactivate (false) an account. */
public record ActivePatch(@NotNull Boolean active) {}
