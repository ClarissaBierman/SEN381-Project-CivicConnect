package com.civicconnect.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        Instant timestamp,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(ErrorCode code, int status, String detail, String path, List<FieldError> fieldErrors) {
        return new ApiError("about:blank", code.title(), status, code.name(), detail, path, Instant.now(),
                fieldErrors == null ? List.of() : fieldErrors);
    }
}
