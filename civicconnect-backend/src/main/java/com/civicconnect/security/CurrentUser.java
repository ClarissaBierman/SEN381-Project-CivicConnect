package com.civicconnect.security;

/**
 * The logged-in user, taken from the JWT - never from the request body (API & Integration Design).
 * id is a client_id for requesters and an emp_id for staff and managers.
 */
public record CurrentUser(Long id, Role role) {

    public enum Role { REQUESTER, STAFF, MANAGEMENT }

    public boolean isRequester() { return role == Role.REQUESTER; }

    public boolean isStaff() { return role == Role.STAFF; }

    public boolean isManagement() { return role == Role.MANAGEMENT; }

    public boolean isEmployee() { return role != Role.REQUESTER; }
}
