package com.civicconnect.common.error;

import java.util.List;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.FieldError> fieldErrors;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<ApiError.FieldError> fieldErrors) {
        super(detail);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public ErrorCode getCode() { return code; }

    public List<ApiError.FieldError> getFieldErrors() { return fieldErrors; }
}
