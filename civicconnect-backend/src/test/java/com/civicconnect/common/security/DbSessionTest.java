package com.civicconnect.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Each user type maps to the database role created by the Supabase build script. */
class DbSessionTest {

    @Test
    @DisplayName("Every user type has its own database role")
    void rolesMatchBuildScript() {
        assertThat(DbSession.databaseRole(CurrentUser.Role.REQUESTER)).isEqualTo("civicconnect_client");
        assertThat(DbSession.databaseRole(CurrentUser.Role.STAFF)).isEqualTo("civicconnect_staff");
        assertThat(DbSession.databaseRole(CurrentUser.Role.MANAGEMENT)).isEqualTo("civicconnect_management");
    }
}
