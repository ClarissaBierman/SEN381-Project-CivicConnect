package com.civicconnect.reporting.service;

import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.access.repository.HandlerCategoryRepository;
import com.civicconnect.access.service.StaffDirectory;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.reporting.domain.AuditEntry;
import com.civicconnect.reporting.domain.AuditResource;
import com.civicconnect.reporting.dto.AuditEntryResponse;
import com.civicconnect.reporting.dto.RequestHistoryEntry;
import com.civicconnect.requests.domain.ServiceRequest.Status;
import com.civicconnect.reporting.repository.AuditRepository;
import com.civicconnect.requests.repository.RequestRepository;
import com.civicconnect.requests.service.RequestAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The per-table audit API: client personal data is never returned (ASR-005), other tables are unchanged. */
class AuditServiceTest {

    private AuditRepository audit;
    private EmployeeRepository employees;
    private RequestRepository requests;
    private CurrentUserService currentUser;
    private AuditService service;

    @BeforeEach
    void setUp() {
        audit = mock(AuditRepository.class);
        employees = mock(EmployeeRepository.class);
        requests = mock(RequestRepository.class);
        currentUser = mock(CurrentUserService.class);
        when(currentUser.get()).thenReturn(manager(1));
        StaffDirectory staff = new StaffDirectory(employees, mock(HandlerCategoryRepository.class));
        service = new AuditService(audit, new RequestAccess(requests, staff), staff, currentUser);
        when(requests.findById(10L)).thenReturn(Optional.of(request(10, Status.ASSIGNED, category(4, "IT", true), 1)));
        EmployeeRepository.EmployeeName ayesha = mock(EmployeeRepository.EmployeeName.class);
        when(ayesha.getId()).thenReturn(3L);
        when(ayesha.fullName()).thenReturn("Ayesha Patel");
        when(employees.findNames(any())).thenReturn(List.of(ayesha));
    }

    private static AuditEntry entry(long id, String table, String operation, String oldData, String newData) {
        return entry(id, table, operation, oldData, newData, 1L, null);
    }

    private static AuditEntry entry(long id, String table, String operation, String oldData, String newData,
                                    Long empId, Long clientId) {
        AuditEntry e = AuditEntry.probe(table, "6", operation);
        ReflectionTestUtils.setField(e, "audId", id);
        ReflectionTestUtils.setField(e, "oldData", oldData);
        ReflectionTestUtils.setField(e, "newData", newData);
        ReflectionTestUtils.setField(e, "empId", empId);
        ReflectionTestUtils.setField(e, "clientId", clientId);
        ReflectionTestUtils.setField(e, "changedAt", LocalDateTime.of(2026, 10, 14, 10, 0).plusMinutes(id));
        return e;
    }

    @Test
    @DisplayName("Request history describes each step with the actor's name and the status change")
    void requestHistoryIsDescribed() {
        when(audit.findRequestHistory("10")).thenReturn(List.of(
                entry(1, "request", "INSERT", null, "{\"req_id\":10,\"status\":\"NEW\"}", null, 1L),
                entry(2, "request_handler", "INSERT", null, "{\"req_id\":10,\"emp_id\":3,\"unassigned_at\":null}", 3L, null),
                entry(3, "request", "UPDATE", "{\"status\":\"NEW\"}", "{\"status\":\"ASSIGNED\"}", 3L, null),
                entry(4, "request", "UPDATE", "{\"status\":\"ASSIGNED\",\"priority\":null}",
                        "{\"status\":\"ASSIGNED\",\"priority\":\"HIGH\"}", 1L, null),
                entry(5, "request", "UPDATE", "{\"status\":\"ASSIGNED\",\"priority\":\"HIGH\"}",
                        "{\"status\":\"ASSIGNED\",\"priority\":null}", 1L, null),
                entry(6, "request", "UPDATE", "{\"status\":\"ASSIGNED\",\"location\":\"A\"}",
                        "{\"status\":\"ASSIGNED\",\"location\":\"B\"}", null, null),
                entry(7, "request_handler", "UPDATE", "{\"emp_id\":3,\"unassigned_at\":null}",
                        "{\"emp_id\":3,\"unassigned_at\":\"2026-10-14T10:00\"}", 1L, null),
                entry(8, "comment", "INSERT", null, "{\"visible_to_requester\":true}", 3L, null),
                entry(9, "comment", "INSERT", null, "{\"visible_to_requester\":false}", 3L, null),
                entry(10, "request_handler", "INSERT", null, "{\"emp_id\":99}", 99L, null),
                entry(11, "something_else", "DELETE", "{}", null, 1L, null),
                entry(12, "request", "UPDATE", "not json", "{\"status\":\"BOGUS\"}", 1L, null)));

        List<RequestHistoryEntry> history = service.forRequest(10L);

        assertThat(history).extracting(RequestHistoryEntry::action).containsExactly(
                "submitted the request", "assigned Ayesha Patel as handler", "changed the status",
                "set the priority to High", "cleared the priority", "updated the request",
                "removed Ayesha Patel as handler", "added a note for the requester", "added an internal note",
                "assigned employee 99 as handler", "delete something_else", "updated the request");
        assertThat(history.get(0).actor()).isEqualTo("Requester #1");
        assertThat(history.get(0).toStatus()).isEqualTo(Status.NEW);
        assertThat(history.get(2).fromStatus()).isEqualTo(Status.NEW);
        assertThat(history.get(2).toStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(history.get(1).actor()).isEqualTo("Ayesha Patel");
        assertThat(history.get(5).actor()).isEqualTo("System");
        assertThat(history.get(9).actor()).isEqualTo("Employee 99");
        assertThat(history.get(0).at()).isEqualTo(Instant.parse("2026-10-14T10:01:00Z"));
    }

    @Test
    @DisplayName("Staff outside the request's category cannot read its history")
    void historyNeedsScope() {
        when(currentUser.get()).thenReturn(staff(4));

        assertApiError(ErrorCode.FORBIDDEN, () -> service.forRequest(10L));
        verify(audit, never()).findRequestHistory(any());
    }

    @Test
    @DisplayName("Search only looks in the requested table, upper-cases the operation, and redacts clients")
    void searchIsPerTable() {
        when(audit.findAll(ArgumentMatchers.<Example<AuditEntry>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                entry(31, "client", "INSERT", null, "{\"client_id\":6,\"email\":\"x@y.z\"}"))));

        var page = service.search(AuditResource.CLIENTS, "6", "insert", PageRequest.of(0, 20));

        ArgumentCaptor<Example<AuditEntry>> probe = ArgumentCaptor.forClass(Example.class);
        verify(audit).findAll(probe.capture(), any(Pageable.class));
        assertThat(probe.getValue().getProbe().getTable()).isEqualTo("client");
        assertThat(probe.getValue().getProbe().getOperation()).isEqualTo("INSERT");
        assertThat(page.getContent().get(0).newData().toString()).doesNotContain("x@y.z");
        assertThat(page.getContent().get(0).changedFields()).isEmpty();

        service.search(AuditResource.REQUESTS, null, null, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("Resource slugs map to tables; unknown slugs are not found")
    void resourceSlugs() {
        assertThat(AuditResource.fromSlug("handler-categories")).contains(AuditResource.HANDLER_CATEGORIES);
        assertThat(AuditResource.fromSlug("nope")).isEmpty();
        assertThat(AuditResource.slugs()).contains("requests", "clients");
        assertThat(AuditResource.EMPLOYEES.shows("street")).isTrue();
        assertThat(AuditResource.CLIENTS.shows("email")).isFalse();
    }

    @Test
    @DisplayName("A client UPDATE shows who changed what field, but not the values of personal fields")
    void clientEntryIsRedacted() {
        when(audit.findById(31L)).thenReturn(Optional.of(entry(31, "client", "UPDATE",
                "{\"client_id\":6,\"email\":\"fatima.adams@example.com\",\"phone\":\"0820000006\",\"active\":true}",
                "{\"client_id\":6,\"email\":\"fatima@new.example\",\"phone\":\"0820000006\",\"active\":false,\"password_changed\":true}")));

        AuditEntryResponse r = service.get(AuditResource.CLIENTS, 31L);

        assertThat(r.oldData().toString() + r.newData()).doesNotContain("fatima", "0820000006", "email", "phone");
        assertThat(r.newData().get("active").asBoolean()).isFalse();
        assertThat(r.changedFields()).containsExactly("active", "email", "password");
        assertThat(r.empId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Other tables show the full row, e.g. an employee change")
    void employeeEntryIsComplete() {
        when(audit.findById(32L)).thenReturn(Optional.of(entry(32, "employee", "UPDATE",
                "{\"emp_id\":5,\"surname\":\"Botha\",\"active\":true}", "{\"emp_id\":5,\"surname\":\"Botha\",\"active\":false}")));

        AuditEntryResponse r = service.get(AuditResource.EMPLOYEES, 32L);

        assertThat(r.newData().get("surname").asString()).isEqualTo("Botha");
        assertThat(r.changedFields()).containsExactly("active");
    }

    @Test
    @DisplayName("An entry can only be read through its own table's endpoint (no reading a client row via /audit/requests)")
    void wrongResourceIsNotFound() {
        when(audit.findById(31L)).thenReturn(Optional.of(entry(31, "client", "UPDATE", "{}", "{}")));

        assertApiError(ErrorCode.NOT_FOUND, () -> service.get(AuditResource.REQUESTS, 31L));
    }
}
