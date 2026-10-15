package com.civicconnect.integration;

import com.civicconnect.access.dto.RegisterRequest;
import com.civicconnect.access.service.AuthService;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.reporting.domain.AuditResource;
import com.civicconnect.reporting.dto.RequestHistoryEntry;
import com.civicconnect.reporting.service.AuditService;
import com.civicconnect.requests.dto.NewComment;
import com.civicconnect.requests.dto.NewServiceRequest;
import com.civicconnect.requests.dto.RequestDetail;
import com.civicconnect.requests.dto.StatusChangeRequest;
import com.civicconnect.requests.service.CommentService;
import com.civicconnect.requests.service.RequestService;

import com.civicconnect.requests.domain.ServiceRequest.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static com.civicconnect.support.TestData.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests against the real database (the one in .env), using the dummy accounts from
 * database/civicconnect_supabase_build.sql:
 *   client 1 = Thabo (requester), employee 3 = Ayesha (staff, IT Support), employee 1 = Lerato (manager).
 *
 * Every test runs inside one transaction that is rolled back at the end, so nothing is left behind -
 * not even audit rows. Checks the parts unit tests cannot: Hibernate mappings, the database roles,
 * the audit trigger and the append-only rules (DEC-006, DEC-007, ASR-001, ASR-003).
 *
 * Tagged "integration": run with  .\mvnw test -Pintegration
 */
@SpringBootTest
@Transactional
@Tag("integration")
class RequestWorkflowIntegrationTest {

    private static final long THABO = 1, AYESHA = 3, LERATO = 1, IT_SUPPORT = 4;

    @Autowired private RequestService requests;
    @Autowired private CommentService comments;
    @Autowired private AuditService audit;
    @Autowired private AuthService auth;
    @Autowired private JdbcTemplate jdbc;
    @Value("${spring.datasource.url}") private String dbUrl;
    @Value("${spring.flyway.user}") private String ownerUser;
    @Value("${spring.flyway.password}") private String ownerPassword;

    /** Pretends a user is logged in, exactly as a verified token would. */
    private void loginAs(long id, CurrentUser.Role role) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(String.valueOf(id)).claim("role", role.name()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private RequestDetail submitTestRequest() {
        loginAs(THABO, CurrentUser.Role.REQUESTER);
        return requests.create(new NewServiceRequest(IT_SUPPORT, "Integration test request", "Test room", null));
    }

    @Test
    @DisplayName("Full lifecycle: submit, accept, progress with a note, comment - with the right audit trail")
    void fullLifecycleIsAudited() {
        RequestDetail created = submitTestRequest();
        assertThat(created.status()).isEqualTo(Status.NEW);
        assertThat(created.refNum()).startsWith("CC-");

        loginAs(AYESHA, CurrentUser.Role.STAFF);
        RequestDetail assigned = requests.assign(created.id(), null);
        assertThat(assigned.status()).isEqualTo(Status.ASSIGNED);
        assertThat(assigned.handlerId()).isEqualTo(AYESHA);

        requests.changeStatus(created.id(), new StatusChangeRequest(Status.IN_PROGRESS, "Part ordered", true));
        comments.add(created.id(), new NewComment("Internal: supplier is slow", false));

        // The requester sees the status and only the comment meant for them (FR-002, FR-004)
        loginAs(THABO, CurrentUser.Role.REQUESTER);
        RequestDetail seenByRequester = requests.get(created.id());
        assertThat(seenByRequester.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(seenByRequester.comments()).extracting("content").containsExactly("Part ordered");

        // The audit trigger recorded every change with the right actor (NFR-006, DEC-007)
        loginAs(AYESHA, CurrentUser.Role.STAFF);
        // (inside one test transaction every row has the same timestamp, so the order is not checked)
        List<RequestHistoryEntry> history = audit.forRequest(created.id());
        assertThat(history).extracting(RequestHistoryEntry::action).containsExactlyInAnyOrder(
                "submitted the request", "assigned Ayesha Patel as handler", "changed the status", "changed the status",
                "added a note for the requester", "added an internal note");
        assertThat(history).filteredOn(e -> e.toStatus() == Status.IN_PROGRESS)
                .extracting(RequestHistoryEntry::fromStatus).containsExactly(Status.ASSIGNED);
        for (RequestHistoryEntry e : history) {
            if (e.action().equals("submitted the request")) {
                assertThat(e.actorClientId()).as("submitted by the requester").isEqualTo(THABO);
                assertThat(e.actor()).isEqualTo("Requester #" + THABO);
                assertThat(e.actorEmpId()).isNull();
            } else {
                assertThat(e.actorEmpId()).as(e.action() + " done by the handler").isEqualTo(AYESHA);
                assertThat(e.actor()).isEqualTo("Ayesha Patel");
                assertThat(e.actorClientId()).isNull();
            }
        }
    }

    @Test
    @DisplayName("A requester only sees their own requests (FR-003)")
    void requesterSeesOnlyOwnRequests() {
        RequestDetail created = submitTestRequest();

        assertThat(requests.mine(PageRequest.of(0, 50)).getContent()).extracting("id").contains(created.id());

        loginAs(2, CurrentUser.Role.REQUESTER); // Naledi
        assertApiError(ErrorCode.NOT_FOUND, () -> requests.get(created.id()));
    }

    @Test
    @DisplayName("Registering stores a hash and the audit trail never contains the password")
    void registrationNeverLeaksPassword() {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest("Test", "User", email, null, "Secret123!"));
        long clientId = jdbc.queryForObject("select client_id from client where email = ?", Long.class, email);

        String stored = jdbc.queryForObject("select password from client where client_id = ?", String.class, clientId);
        assertThat(stored).startsWith("$2").doesNotContain("Secret123!");

        loginAs(LERATO, CurrentUser.Role.MANAGEMENT);
        var entries = audit.search(AuditResource.CLIENTS, String.valueOf(clientId), "INSERT", PageRequest.of(0, 5))
                .getContent();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).clientId()).as("the new client is the actor").isEqualTo(clientId);
        String shown = entries.get(0).newData().toString();
        assertThat(shown).doesNotContain("password").doesNotContain("$2");
        assertThat(shown).as("no client personal data in the audit API (ASR-005)")
                .doesNotContain(email).doesNotContain("Test").contains("\"client_id\"");
    }

    @Test
    @DisplayName("The database itself refuses a requester changing a request, even if the Java checks were bypassed")
    void databaseRoleBlocksRequester() {
        loginAs(THABO, CurrentUser.Role.REQUESTER);
        requests.mine(PageRequest.of(0, 1)); // switches the connection to the civicconnect_client role

        assertThrows(DataAccessException.class,
                () -> jdbc.update("update request set status = 'CLOSED' where req_id = 1"));
    }

    @Test
    @DisplayName("The database allows only one active handler per request (FR-008)")
    void databaseAllowsOneActiveHandler() {
        RequestDetail created = submitTestRequest();
        loginAs(AYESHA, CurrentUser.Role.STAFF);
        requests.assign(created.id(), null);

        assertThrows(DataAccessException.class,
                () -> jdbc.update("insert into request_handler (req_id, emp_id) values (?, ?)", created.id(), AYESHA));
    }

    @Test
    @DisplayName("A manager's database role cannot touch the audit table at all (V6)")
    void managerCannotChangeAuditEntries() {
        loginAs(LERATO, CurrentUser.Role.MANAGEMENT);
        audit.search(AuditResource.REQUESTS, null, null, PageRequest.of(0, 1)); // switches to the civicconnect_management role

        assertThrows(DataAccessException.class,
                () -> jdbc.update("update audit_entry set operation = 'EDITED' where aud_id = (select min(aud_id) from audit_entry)"));
    }

    /**
     * D-35: since V6 no application role may write to audit_entry, so a test as a manager is refused by a missing
     * grant before the trigger runs. This one connects as the schema owner, who has every right, so only the
     * append-only triggers can refuse - it fails if a trigger is missing or disabled.
     */
    @Test
    @DisplayName("Audit entries and notes cannot be changed or deleted, not even by the schema owner (ASR-003, FR-010)")
    void auditTrailAndNotesAreAppendOnly() throws SQLException {
        try (Connection owner = DriverManager.getConnection(dbUrl, ownerUser, ownerPassword)) {
            owner.setAutoCommit(false);
            for (String sql : List.of(
                    "update civicconnect.audit_entry set operation = 'EDITED' where aud_id = (select min(aud_id) from civicconnect.audit_entry)",
                    "delete from civicconnect.audit_entry where aud_id = (select min(aud_id) from civicconnect.audit_entry)",
                    "update civicconnect.comment set content = 'edited' where com_id = (select min(com_id) from civicconnect.comment)",
                    "delete from civicconnect.comment where com_id = (select min(com_id) from civicconnect.comment)")) {
                SQLException refused = assertThrows(SQLException.class, () -> {
                    try (Statement st = owner.createStatement()) {
                        st.executeUpdate(sql);
                    }
                }, sql);
                assertThat(refused.getMessage()).as(sql).contains("append-only");
                owner.rollback();
            }
        }
    }
}
