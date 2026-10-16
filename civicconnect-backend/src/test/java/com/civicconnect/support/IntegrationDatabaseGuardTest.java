package com.civicconnect.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D-33: the guard reads the database host correctly from every JDBC URL form the team uses. */
class IntegrationDatabaseGuardTest {

    @Test
    @DisplayName("Local and CI hosts are allowed; the Supabase pooler is not")
    void hosts() {
        assertThat(IntegrationDatabaseGuard.host("jdbc:postgresql://localhost:5432/postgres")).isEqualTo("localhost");
        assertThat(IntegrationDatabaseGuard.host("jdbc:postgresql://127.0.0.1:55432/civicconnect?sslmode=disable"))
                .isEqualTo("127.0.0.1");
        assertThat(IntegrationDatabaseGuard.host("jdbc:postgresql://[::1]:5432/db")).isEqualTo("::1");
        assertThat(IntegrationDatabaseGuard.host("jdbc:postgresql://LOCALHOST/db")).isEqualTo("localhost");
        String supabase = IntegrationDatabaseGuard.host(
                "jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require");
        assertThat(supabase).isEqualTo("aws-0-eu-central-1.pooler.supabase.com");
        assertThat(IntegrationDatabaseGuard.LOCAL_HOSTS).contains("localhost", "127.0.0.1").doesNotContain(supabase);
    }
}
