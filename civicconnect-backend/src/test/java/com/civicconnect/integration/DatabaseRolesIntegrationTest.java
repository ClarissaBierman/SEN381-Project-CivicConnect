package com.civicconnect.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database's own protection (V3 grants + V6), tested directly in SQL as each database role - the
 * layer that must still hold if a Java check is missed (defence in depth, ASR-001, ASR-005).
 * Every test runs in one transaction that is rolled back. Uses the dummy data of the build script:
 * clients 1 Thabo (requests 1 and 4), 2 Naledi; employees 1 Lerato (manager), 2 Johan, 3 Ayesha (staff).
 */
@SpringBootTest
@Transactional
@Tag("integration")
class DatabaseRolesIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    /** Act as a database role for the rest of the transaction, like DbSession does. */
    private void as(String role, Long empId, Long clientId) {
        jdbc.execute("RESET ROLE");
        jdbc.queryForList("select set_config('civicconnect.emp_id', ?, true), set_config('civicconnect.client_id', ?, true)",
                empId == null ? "" : empId.toString(), clientId == null ? "" : clientId.toString());
        if (role != null) jdbc.queryForList("select set_config('role', ?, true)", role);
    }

    /** Runs a statement that must fail and returns the database's message; a savepoint keeps the test transaction usable. */
    private String refused(String sql) {
        jdbc.execute("SAVEPOINT probe");
        try {
            jdbc.execute(sql);
        } catch (DataAccessException e) {
            return e.getMostSpecificCause().getMessage();
        } finally {
            jdbc.execute("ROLLBACK TO SAVEPOINT probe");
        }
        throw new AssertionError("Expected the database to refuse: " + sql);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    @DisplayName("D-19: the app's login role cannot create objects, so it cannot plant code a trigger would run as the owner")
    void appRoleCannotCreateObjects() {
        as(null, null, null);
        assertThat(refused("create function civicconnect.to_jsonb(civicconnect.category) returns jsonb "
                + "language sql as $$ select '{}'::jsonb $$"))
                .contains("permission denied for schema civicconnect");
        assertThat(refused("create table civicconnect.planted (id int)"))
                .contains("permission denied for schema civicconnect");
    }

    @Test
    @DisplayName("D-19: every SECURITY DEFINER function searches pg_catalog first")
    void definerFunctionsPinTheSearchPath() {
        List<String> settings = jdbc.queryForList("""
                select p.proname || ':' || coalesce(array_to_string(p.proconfig, ','), '')
                from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                where n.nspname = 'civicconnect' and p.prosecdef""", String.class);
        assertThat(settings).isNotEmpty()
                .allSatisfy(s -> assertThat(s).contains("search_path=pg_catalog, civicconnect, pg_temp"));
    }

    @Test
    @DisplayName("D-21: staff cannot read the raw audit table; their audit view only has request history")
    void staffAuditView() {
        as("civicconnect_staff", 2L, null);
        assertThat(refused("select count(*) from audit_entry")).contains("permission denied for table audit_entry");
        assertThat(jdbc.queryForList("select distinct table_name from audit_log", String.class))
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t).isIn("request", "request_handler", "comment"));
    }

    @Test
    @DisplayName("D-21: management sees client audit rows, but never the personal values in them")
    void managementSeesNoClientPersonalValues() {
        as("civicconnect_management", 1L, null);
        List<String> rows = jdbc.queryForList(
                "select coalesce(new_data, old_data) from audit_log where table_name = 'client'", String.class);
        assertThat(rows).isNotEmpty().allSatisfy(json -> assertThat(json)
                .doesNotContain("@")                 // emails
                .doesNotContain("1995-05-05")        // Thabo's date of birth
                .doesNotContain("Thabo"));
        assertThat(refused("select count(*) from audit_entry")).contains("permission denied for table audit_entry");
    }

    @Test
    @DisplayName("D-21: a requester's role only reaches their own account, requests and visible notes (row level security)")
    void requesterRowLevelSecurity() {
        as("civicconnect_client", null, 1L);
        assertThat(count("select count(*) from client")).isOne();
        assertThat(count("select count(*) from request")).isPositive();
        assertThat(count("select count(*) from request where requester_id <> 1")).isZero();
        assertThat(count("select count(*) from comment where not visible_to_requester")).isZero();
        assertThat(count("select count(*) from comment c join request r on r.req_id = c.req_id "
                + "where r.requester_id <> 1")).isZero();

        // Changing someone else's account or submitting a request in their name does nothing / is refused
        assertThat(jdbc.update("update client set phone = '000' where client_id = 2")).isZero();
        assertThat(refused("insert into request (ref_num, cat_id, description, location, requester_id) "
                + "values ('CC-TEST-1', 1, 'x', 'y', 2)")).contains("row-level security");
    }

    @Test
    @DisplayName("D-21: with no requester id set (e.g. a bug), the requester role sees nothing at all")
    void requesterRoleWithoutIdSeesNothing() {
        as("civicconnect_client", null, null);
        assertThat(count("select count(*) from client")).isZero();
        assertThat(count("select count(*) from request")).isZero();
        assertThat(count("select count(*) from comment")).isZero();
    }

    @Test
    @DisplayName("D-26: staff may change their own password and nothing else in the employee table")
    void staffOnlyChangeOwnPassword() {
        as("civicconnect_staff", 2L, null);
        assertThat(jdbc.update("update employee set password = 'x' where emp_id = 3")).as("someone else").isZero();
        assertThat(jdbc.update("update employee set password = 'x' where emp_id = 2")).as("own row").isOne();
        assertThat(refused("update employee set email = 'x@y.z' where emp_id = 2"))
                .contains("permission denied for table employee");
        assertThat(refused("select password from employee where emp_id = 2"))
                .contains("permission denied for table employee");
    }

    @Test
    @DisplayName("D-18: email_in_use() sees both account tables whatever the caller may read")
    void emailInUseForEveryRole() {
        as(null, null, null);
        String naledi = jdbc.queryForObject("select email from client where client_id = 2", String.class);

        as("civicconnect_client", null, 1L);
        assertThat(count("select count(*) from client where client_id = 2")).as("cannot read Naledi's row").isZero();
        assertThat(jdbc.queryForObject("select email_in_use(?)", Boolean.class, naledi.toUpperCase())).isTrue();
        assertThat(jdbc.queryForObject("select email_in_use('johan.vanwyk@civicconnect.example')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select email_in_use('nobody@example.com')", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("D-36: a requester's role gets the status history of their own requests only (V7)")
    void statusHistoryOnlyForOwnRequests() {
        as("civicconnect_client", null, 1L);   // Thabo: requests 1 and 4
        assertThat(count("select count(*) from request_status_history(1)")).isPositive();
        assertThat(count("select count(*) from request_status_history(2)")).as("Naledi's request").isZero();

        as("civicconnect_staff", 2L, null);    // staff and management keep their access (checked in Java)
        assertThat(count("select count(*) from request_status_history(2)")).isPositive();
    }
}
