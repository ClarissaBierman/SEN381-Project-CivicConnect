package com.civicconnect.access.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.dto.HandlerOption;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.access.repository.HandlerCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What other modules may ask the Access module about staff (ARC-02: only the owning module reads its
 * tables). Used by Requests for category scope (FR-005), handler checks (FR-008) and display names.
 * Callers are already inside a transaction that DbSession has switched to the user's database role.
 */
@Service
public class StaffDirectory {

    private final EmployeeRepository employees;
    private final HandlerCategoryRepository scope;

    public StaffDirectory(EmployeeRepository employees, HandlerCategoryRepository scope) {
        this.employees = employees;
        this.scope = scope;
    }

    /** Category IDs a handler may work on (FR-005). */
    public List<Long> categoryIds(Long empId) {
        return scope.findCategoryIds(empId);
    }

    public boolean covers(Long empId, Long catId) {
        return categoryIds(empId).contains(catId);
    }

    /** Management only (staff may not read whole employee rows). */
    public Optional<Employee> find(Long empId) {
        return employees.findById(empId);
    }

    /** "First Surname" by employee ID, for lists and history. */
    public Map<Long, String> names(Collection<Long> empIds) {
        if (empIds.isEmpty()) return Map.of();
        return employees.findNames(empIds).stream()
                .collect(Collectors.toMap(EmployeeRepository.EmployeeName::getId, EmployeeRepository.EmployeeName::fullName));
    }

    public List<HandlerOption> activeHandlersFor(Long catId) {
        return employees.findActiveHandlersFor(catId).stream()
                .map(n -> new HandlerOption(n.getId(), n.fullName())).toList();
    }
}
