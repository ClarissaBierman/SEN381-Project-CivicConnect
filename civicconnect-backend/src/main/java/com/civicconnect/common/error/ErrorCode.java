package com.civicconnect.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not logged in"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Version conflict"),
    ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Already assigned"),
    INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid status transition"),
    REQUEST_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "Request closed"),
    CATEGORY_INACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "Category inactive"),
    OUT_OF_SCOPE(HttpStatus.UNPROCESSABLE_CONTENT, "Handler not in category"),
    LAST_MANAGER(HttpStatus.UNPROCESSABLE_CONTENT, "Last active manager"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() { return status; }

    public String title() { return title; }

    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_FAILED;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 409 -> CONFLICT;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
        };
    }
}
