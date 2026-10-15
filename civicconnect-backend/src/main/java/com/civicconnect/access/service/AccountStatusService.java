package com.civicconnect.access.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.common.security.AccountStatusLookup;
import com.civicconnect.common.security.AppRoleOnly;
import com.civicconnect.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * B1: the security layer asks this on every request whether the account in the token may still act.
 * Runs as the application's login role (@AppRoleOnly), which can read these columns.
 */
@Service
public class AccountStatusService implements AccountStatusLookup {

    private final ClientRepository clients;
    private final EmployeeRepository employees;

    public AccountStatusService(ClientRepository clients, EmployeeRepository employees) {
        this.clients = clients;
        this.employees = employees;
    }

    @Override
    @Transactional(readOnly = true)
    @AppRoleOnly
    public Optional<AccountState> find(CurrentUser.Role tokenRole, long id) {
        if (tokenRole == CurrentUser.Role.REQUESTER) {
            return clients.findActive(id).map(active -> new AccountState(active, CurrentUser.Role.REQUESTER));
        }
        return employees.findStatus(id).map(s -> new AccountState(s.getActive(), toUserRole(s.getRole())));
    }

    static CurrentUser.Role toUserRole(Employee.Role role) {
        return role == Employee.Role.MANAGEMENT ? CurrentUser.Role.MANAGEMENT : CurrentUser.Role.STAFF;
    }
}
