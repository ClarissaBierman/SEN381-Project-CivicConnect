package com.civicconnect.reporting.service;

import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.access.repository.HandlerCategoryRepository;
import com.civicconnect.access.service.StaffDirectory;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.security.DbSession;
import com.civicconnect.reporting.domain.AuditEntry;
import com.civicconnect.reporting.domain.AuditResource;
import com.civicconnect.reporting.dto.AuditEntryResponse;
import com.civicconnect.reporting.repository.AuditRepository;
import com.civicconnect.requests.repository.RequestRepository;
import com.civicconnect.requests.service.RequestAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** The per-table audit API: client personal data is never returned (ASR-005), other tables are unchanged. */
class AuditServiceTest {

    private AuditRepository audit;
    private AuditService service;

    @BeforeEach
    void setUp() {
        audit = mock(AuditRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.get()).thenReturn(manager(1));
        StaffDirectory staff = new StaffDirectory(mock(EmployeeRepository.class), mock(HandlerCategoryRepository.class));
        service = new AuditService(audit, new RequestAccess(mock(RequestRepository.class), staff), staff,
                currentUser, mock(DbSession.class));
    }

    private static AuditEntry entry(long id, String table, String operation, String oldData, String newData) {
        AuditEntry e = AuditEntry.probe(table, "6", operation);
        ReflectionTestUtils.setField(e, "audId", id);
        ReflectionTestUtils.setField(e, "oldData", oldData);
        ReflectionTestUtils.setField(e, "newData", newData);
        ReflectionTestUtils.setField(e, "empId", 1L);
        return e;
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
