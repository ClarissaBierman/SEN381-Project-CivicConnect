package com.civicconnect.access.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.common.security.AccountStatusLookup.AccountState;
import com.civicconnect.common.security.CurrentUser.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccountStatusServiceTest {

    private final ClientRepository clients = mock(ClientRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AccountStatusService service = new AccountStatusService(clients, employees);

    private static EmployeeRepository.EmployeeStatus status(boolean active, Employee.Role role) {
        EmployeeRepository.EmployeeStatus s = mock(EmployeeRepository.EmployeeStatus.class);
        when(s.getActive()).thenReturn(active);
        when(s.getRole()).thenReturn(role);
        return s;
    }

    @Test
    @DisplayName("Requesters are looked up as clients, employees by their current role")
    void looksUpTheRightTable() {
        EmployeeRepository.EmployeeStatus manager = status(true, Employee.Role.MANAGEMENT);
        EmployeeRepository.EmployeeStatus staff = status(true, Employee.Role.STAFF);
        when(clients.findActive(1L)).thenReturn(Optional.of(false));
        when(employees.findStatus(1L)).thenReturn(Optional.of(manager));
        when(employees.findStatus(3L)).thenReturn(Optional.of(staff));

        assertThat(service.find(Role.REQUESTER, 1)).contains(new AccountState(false, Role.REQUESTER));
        assertThat(service.find(Role.MANAGEMENT, 1)).contains(new AccountState(true, Role.MANAGEMENT));
        assertThat(service.find(Role.MANAGEMENT, 3)).contains(new AccountState(true, Role.STAFF));
        assertThat(service.find(Role.STAFF, 42)).isEmpty();
    }
}
