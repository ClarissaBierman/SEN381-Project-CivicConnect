package com.civicconnect.access.dto;

/**
 * API-01 response. The same for a new and an already registered email address, so registration cannot
 * be used to find out who has an account (D-25).
 */
public record RegistrationResponse(String message) {

    public static final String MESSAGE = "If this email address was not registered yet, your account has been "
            + "created. Log in to continue. If you already have an account, log in with your existing password.";

    public static RegistrationResponse accepted() {
        return new RegistrationResponse(MESSAGE);
    }
}
