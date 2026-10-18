package com.civicconnect.integration;

import com.civicconnect.common.security.JwtService;
import com.civicconnect.requests.domain.ServiceRequest.Status;
import com.civicconnect.requests.dto.StatusChangeRequest;
import com.civicconnect.requests.service.RequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * DEC-012 / R-18: a status change and its audit entry are one unit. If the audit insert fails, the status
 * change (and its note) must be rolled back, never saved without an audit entry (NFR-006, ASR-003).
 *
 * Since C-02 the audit is written by the audit_row_change trigger inside the same UPDATE, so this tests the
 * real path: RequestService.changeStatus in its own committed transaction (no test transaction around it).
 * The schema owner adds a trigger that makes the audit insert fail for request 5 only, and removes it
 * afterwards. Nothing is committed: the dummy data is left as it was.
 */
@SpringBootTest
@Tag("integration")
class StatusChangeRollbackTest {

    private static final long JOHAN = 2;
    private static final long JOHANS_IN_PROGRESS = 5;    // In Progress, handled by Johan (dummy data)

    @Autowired private RequestService requests;
    @Value("${spring.datasource.url}") private String dbUrl;
    @Value("${spring.flyway.user}") private String ownerUser;
    @Value("${spring.flyway.password}") private String ownerPassword;

    private static final String DROP = """
            drop trigger if exists zz_test_force_audit_failure on civicconnect.audit_entry;
            drop function if exists civicconnect.test_force_audit_failure();
            """;

    private void asOwner(String sql) throws SQLException {
        try (Connection owner = DriverManager.getConnection(dbUrl, ownerUser, ownerPassword);
             Statement st = owner.createStatement()) {
            st.execute(sql);
        }
    }

    private List<Object> snapshot() throws SQLException {
        try (Connection owner = DriverManager.getConnection(dbUrl, ownerUser, ownerPassword);
             Statement st = owner.createStatement();
             ResultSet rs = st.executeQuery("""
                     select r.status, r.updated_at,
                            (select count(*) from civicconnect.audit_entry a
                              where a.table_name = 'request' and a.record_id = '5') as audit_rows,
                            (select count(*) from civicconnect.comment c where c.req_id = r.req_id) as comments
                       from civicconnect.request r where r.req_id = 5""")) {
            assertThat(rs.next()).isTrue();
            return List.of(rs.getString(1), String.valueOf(rs.getTimestamp(2)), rs.getLong(3), rs.getLong(4));
        }
    }

    @BeforeEach
    void forceAuditFailure() throws SQLException {
        asOwner(DROP + """
                create function civicconnect.test_force_audit_failure() returns trigger
                    language plpgsql as $$
                begin
                    raise exception 'forced audit failure (StatusChangeRollbackTest)';
                end;
                $$;
                create trigger zz_test_force_audit_failure before insert on civicconnect.audit_entry
                    for each row
                    when (new.table_name = 'request' and new.record_id = '5' and new.operation = 'UPDATE')
                    execute function civicconnect.test_force_audit_failure();
                """);
    }

    @AfterEach
    void removeForcedFailure() throws SQLException {
        SecurityContextHolder.clearContext();
        asOwner(DROP);
    }

    @Test
    @DisplayName("A failed audit insert rolls the status change and its note back (DEC-012, R-18, NFR-006)")
    void failedAuditRollsBackStatusChange() throws SQLException {
        List<Object> before = snapshot();
        assertThat(before.get(0)).isEqualTo("IN_PROGRESS");

        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(JOHAN))
                .claim(JwtService.ROLE_CLAIM, "STAFF").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        Throwable failure = catchThrowable(() -> requests.changeStatus(JOHANS_IN_PROGRESS,
                new StatusChangeRequest(Status.RESOLVED, "Fixed the leak", true)));

        assertThat(failure).as("the change is refused").isNotNull();
        assertThat(failure).rootCause().hasMessageContaining("forced audit failure");
        assertThat(snapshot()).as("status, updated_at, audit rows and notes are unchanged").isEqualTo(before);
    }
}
