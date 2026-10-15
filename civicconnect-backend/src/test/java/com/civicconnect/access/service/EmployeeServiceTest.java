package com.civicconnect.access.service;

import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import com.civicconnect.requests.dto.CategoryResponse;
import com.civicconnect.access.event.StaffMemberRemovedEvent;

import com.civicconnect.access.dto.EmployeePatch;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.domain.HandlerCategory;
import com.civicconnect.access.dto.HandlerCategoriesUpdate;
import com.civicconnect.access.dto.NewEmployee;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.access.repository.HandlerCategoryRepository;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.requests.repository.CategoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
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

    private ApplicationEventPublisher events;

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
        events = mock(ApplicationEventPublisher.class);
        service = new EmployeeService(employees, clients, categories, scope,
                new BCryptPasswordEncoder(4), currentUser, new StaffDirectory(employees, scope), events);
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
        verify(events).publishEvent(new StaffMemberRemovedEvent(2L, 1L, "removed from the category", Set.of(1L)));
    }

    @Test
    @DisplayName("Only adding categories releases nothing")
    void addingCategoriesReleasesNothing() {
        Employee johan = employee(2, Employee.Role.STAFF, true);
        when(employees.findById(2L)).thenReturn(Optional.of(johan));
        when(scope.findByEmployee(2L)).thenReturn(List.of(new HandlerCategory(johan, category(5, "Maintenance", true))));
        when(categories.findAllById(Set.of(5L, 9L)))
                .thenReturn(List.of(category(5, "Maintenance", true), category(9, "Cleaning", true)));

        service.replaceCategories(2L, new HandlerCategoriesUpdate(List.of(5L, 9L)));

        verifyNoInteractions(events);
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

    // ------------------------------------------------------------------ leaving roles

    @Test
    @DisplayName("Deactivating a staff member tells the Requests module to take them off their requests")
    void deactivatingStaffReleasesRequests() {
        when(employees.findById(3L)).thenReturn(Optional.of(employee(3, Employee.Role.STAFF, true)));

        service.update(3L, patchActive(false));

        verify(events).publishEvent(new StaffMemberRemovedEvent(3L, 1L, "deactivated"));
    }

    @Test
    @DisplayName("Moving a staff member to Management also releases their requests; editing a name does not")
    void promotionReleasesRequests() {
        when(employees.findById(3L)).thenReturn(Optional.of(employee(3, Employee.Role.STAFF, true)));

        service.update(3L, new EmployeePatch("Ayesha", null, null, null, null, null, null, null, null, null, null, null));
        verifyNoInteractions(events);

        service.update(3L, new EmployeePatch(null, null, null, null, null, Employee.Role.MANAGEMENT, null, null, null, null, null, null));
        verify(events).publishEvent(new StaffMemberRemovedEvent(3L, 1L, "moved to Management"));
    }

    @Test
    @DisplayName("The only active manager cannot be deactivated or moved to Staff (422 LAST_MANAGER)")
    void lastManagerProtected() {
        Employee lerato = employee(1, Employee.Role.MANAGEMENT, true);
        when(employees.findById(1L)).thenReturn(Optional.of(lerato));
        when(employees.lockActiveManagers()).thenReturn(List.of(lerato));

        assertApiError(ErrorCode.LAST_MANAGER, () -> service.update(1L, patchActive(false)));
        assertApiError(ErrorCode.LAST_MANAGER, () -> service.update(1L,
                new EmployeePatch(null, null, null, null, null, Employee.Role.STAFF, null, null, null, null, null, null)));
        assertThat(lerato.isActive()).isTrue();
        assertThat(lerato.getRole()).isEqualTo(Employee.Role.MANAGEMENT);
    }

    @Test
    @DisplayName("Once another manager exists, a manager can be deactivated")
    void managerDeactivatedWhenAnotherExists() {
        Employee lerato = employee(1, Employee.Role.MANAGEMENT, true);
        Employee second = employee(6, Employee.Role.MANAGEMENT, true);
        when(employees.findById(1L)).thenReturn(Optional.of(lerato));
        when(employees.lockActiveManagers()).thenReturn(List.of(lerato, second));

        service.update(1L, patchActive(false));

        assertThat(lerato.isActive()).isFalse();
        verifyNoInteractions(events);
    }

    private static EmployeePatch patchActive(boolean active) {
        return new EmployeePatch(null, null, null, null, null, null, active, null, null, null, null, null);
    }

    @Test
    @DisplayName("Every editable field is applied; a new email must be free")
    void updateAppliesFields() {
        Employee e = employee(3, Employee.Role.STAFF, true);
        when(employees.findById(3L)).thenReturn(Optional.of(e));
        when(employees.existsByEmailIgnoreCase("taken@civicconnect.example")).thenReturn(true);

        service.update(3L, new EmployeePatch(" Ayesha ", " Patel ", "New@CivicConnect.example", "Secret123!", "011",
                null, null, LocalDate.of(1990, 1, 1), "1 Main", "Area", "City", "0001"));

        assertThat(e.getEmail()).isEqualTo("new@civicconnect.example");
        assertThat(e.getFirstName()).isEqualTo("Ayesha");
        assertThat(e.getPassword()).startsWith("$2");
        assertThat(e.getPostalCode()).isEqualTo("0001");
        assertApiError(ErrorCode.CONFLICT, () -> service.update(3L,
                new EmployeePatch(null, null, "taken@civicconnect.example", null, null, null, null, null, null, null, null, null)));
    }

    @Test
    @DisplayName("List, get, categories and handlers-for-category; unknown IDs are not found")
    void readOperations() {
        Employee e = employee(3, Employee.Role.STAFF, true);
        when(employees.findById(3L)).thenReturn(Optional.of(e));
        when(employees.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(e)));
        when(scope.findByEmployee(3L)).thenReturn(List.of(new HandlerCategory(e, category(4, "IT Support", true))));
        when(categories.existsById(4L)).thenReturn(true);
        when(employees.findActiveHandlersFor(4L)).thenReturn(List.of());

        assertThat(service.list(PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(service.get(3L).id()).isEqualTo(3L);
        assertThat(service.categories(3L)).extracting(CategoryResponse::catName).containsExactly("IT Support");
        assertThat(service.handlersFor(4L)).isEmpty();
        assertApiError(ErrorCode.NOT_FOUND, () -> service.get(99L));
        assertApiError(ErrorCode.NOT_FOUND, () -> service.handlersFor(99L));
    }
}
