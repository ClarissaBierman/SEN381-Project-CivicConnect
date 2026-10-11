package com.civicconnect.error;

import java.time.Instant;
import java.util.List;

/**
 * The one error body every endpoint returns (RFC 9457 problem details + code + fieldErrors),
 * exactly as in the PED error model:
 * { "type", "title", "status", "code", "detail", "instance", "timestamp", "fieldErrors": [] }
 */
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
