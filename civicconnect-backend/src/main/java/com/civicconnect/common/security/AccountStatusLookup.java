package com.civicconnect.common.security;

import java.util.Optional;

/**
 * Reads an account's current state from the database. Implemented by the Access module (which owns the
 * Client and Employee tables), used by the security layer on every request (B1).
 */
public interface AccountStatusLookup {

    /** The account's active flag and current role, or empty if the account no longer exists. */
    Optional<AccountState> find(CurrentUser.Role tokenRole, long id);

    record AccountState(boolean active, CurrentUser.Role role) {}
}
