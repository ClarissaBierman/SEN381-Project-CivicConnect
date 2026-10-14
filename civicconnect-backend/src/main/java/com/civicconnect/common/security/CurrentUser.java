package com.civicconnect.common.security;

public record CurrentUser(Long id, Role role) {

    public enum Role { REQUESTER, STAFF, MANAGEMENT }

    public boolean isRequester() { return role == Role.REQUESTER; }

    public boolean isStaff() { return role == Role.STAFF; }

    public boolean isManagement() { return role == Role.MANAGEMENT; }

    public boolean isEmployee() { return role != Role.REQUESTER; }
}
