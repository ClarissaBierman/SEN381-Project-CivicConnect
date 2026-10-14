package com.civicconnect.common.error;

import java.util.List;

public final class Errors {

    private Errors() {}

    public static ApiException invalidField(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Some fields are invalid.",
                List.of(new ApiError.FieldError(field, message)));
    }

    public static ApiException unauthenticated(String detail) {
        return new ApiException(ErrorCode.UNAUTHENTICATED, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(ErrorCode.FORBIDDEN, detail);
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found.");
    }

    public static ApiException conflict(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }

    public static ApiException rule(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }
}
