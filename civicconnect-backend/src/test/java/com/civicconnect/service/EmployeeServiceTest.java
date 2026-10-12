package com.civicconnect.service;

import com.civicconnect.dto.HandlerCategoriesUpdate;
import com.civicconnect.dto.NewEmployee;
import com.civicconnect.error.ErrorCode;
import com.civicconnect.model.Employee;
import com.civicconnect.model.HandlerCategory;
import com.civicconnect.repository.CategoryRepository;
import com.civicconnect.repository.ClientRepository;
import com.civicconnect.repository.EmployeeRepository;
import com.civicconnect.repository.HandlerCategoryRepository;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Employee accounts and handler categories (FR-005, DEC-002), Management only. */
class EmployeeServiceTest {

    private EmployeeRepository employees;
    private ClientRepository clients;
    private CategoryRepository categories;
    private HandlerCategoryRepository scope;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeRepository.class);
        clients = mock(ClientRepository.class);
        categories = mock(CategoryRepository.class);
        scope = mock(HandlerCategoryRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.get()).thenReturn(manager(1));
        service = new EmployeeService(employees, clients, categories, scope,
                new BCryptPasswordEncoder(4), currentUser, mock(DbSession.class));
        when(employees.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("New employees get a hashed password and a lower-case email")
    void createHashesPassword() {
        service.create(new NewEmployee("Ann", "Lee", "Ann.Lee@CivicConnect.example", "Secret123!", null,
                Employee.Role.STAFF, null, null, null, null, null));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertThat(saved.getValue().getPassword()).startsWith("$2").isNotEqualTo("Secret123!");
        assertThat(saved.getValue().getEmail()).isEqualTo("ann.lee@civicconnect.example");
    }

    @Test
    @DisplayName("An email already used by a client cannot be reused for an employee")
    void createRejectsEmailUsedByClient() {
        when(clients.existsByEmailIgnoreCase("shared@example.com")).thenReturn(true);

        assertApiError(ErrorCode.CONFLICT, () -> service.create(new NewEmployee("A", "B", "shared@example.com",
                "Secret123!", null, Employee.Role.STAFF, null, null, null, null, null)));
    }

    @Test
    @DisplayName("Replacing a handler's categories removes old ones and adds new ones only")
    void replaceCategoriesAddsAndRemoves() {
        Employee johan = employee(2, Employee.Role.STAFF, true);
        when(employees.findById(2L)).thenReturn(Optional.of(johan));
        HandlerCategory facility = new HandlerCategory(johan, category(1, "Facility Faults", true));
        HandlerCategory maintenance = new HandlerCategory(johan, category(5, "Maintenance", true));
        when(scope.findByEmployee(2L)).thenReturn(List.of(facility, maintenance));
        when(categories.findAllById(Set.of(5L, 9L)))
                .thenReturn(List.of(category(5, "Maintenance", true), category(9, "Cleaning", true)));

        var result = service.replaceCategories(2L, new HandlerCategoriesUpdate(List.of(5L, 9L)));

        verify(scope).delete(facility);
        verify(scope, never()).delete(maintenance);
        verify(scope).save(argThat(hc -> hc.getCategory().getCatId().equals(9L)));
        verify(scope, times(1)).save(any());
        assertThat(result).extracting("catName").containsExactlyInAnyOrder("Maintenance", "Cleaning");
    }

    @Test
    @DisplayName("Managers do not have handler categories")
    void managerCannotGetCategories() {
        when(employees.findById(1L)).thenReturn(Optional.of(employee(1, Employee.Role.MANAGEMENT, true)));

        assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> service.replaceCategories(1L, new HandlerCategoriesUpdate(List.of(1L))));
    }

    @Test
    @DisplayName("Unknown category IDs are rejected")
    void unknownCategoryRejected() {
        when(employees.findById(2L)).thenReturn(Optional.of(employee(2, Employee.Role.STAFF, true)));
        when(categories.findAllById(Set.of(99L))).thenReturn(List.of());

        assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> service.replaceCategories(2L, new HandlerCategoriesUpdate(List.of(99L))));
    }
}
