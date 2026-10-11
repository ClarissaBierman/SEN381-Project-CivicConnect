package com.civicconnect.service;

import com.civicconnect.error.ApiError;
import com.civicconnect.error.ApiException;
import com.civicconnect.error.ErrorCode;

import java.util.List;

/** Short helpers for the documented API errors (PED error model). */
public final class Errors {

    private Errors() {}

    /** 400 VALIDATION_FAILED for one field, shown next to that field in the form. */
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

    /** 409: CONFLICT (duplicate), ALREADY_ASSIGNED or VERSION_CONFLICT. */
    public static ApiException conflict(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }

    /** 422: INVALID_TRANSITION, REQUEST_CLOSED, CATEGORY_INACTIVE or OUT_OF_SCOPE. */
    public static ApiException rule(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }
}
