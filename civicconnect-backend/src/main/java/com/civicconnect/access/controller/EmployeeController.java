package com.civicconnect.access.controller;

import com.civicconnect.access.dto.EmployeePatch;
import com.civicconnect.access.dto.EmployeeResponse;
import com.civicconnect.access.dto.NewEmployee;
import com.civicconnect.access.dto.PasswordChange;
import com.civicconnect.access.service.EmployeeService;

import com.civicconnect.common.web.SortGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Employee accounts - Management only, except changing your own password. No DELETE: set active = false instead. */
@RestController
@RequestMapping("/api/v1/employees")
@PreAuthorize("hasRole('MANAGEMENT')")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public PagedModel<EmployeeResponse> list(@PageableDefault(size = 20, sort = "surname") Pageable pageable) {
        return new PagedModel<>(employeeService.list(
                SortGuard.allow(pageable, "surname", "firstName", "email", "role", "active", "createdAt")));
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody NewEmployee body) {
        return employeeService.create(body);
    }

    /** API-26: any employee changes their own password; the current password is required. */
    @PatchMapping("/me/password")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGEMENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody PasswordChange body) {
        employeeService.changeOwnPassword(body);
    }

    @PatchMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeePatch body) {
        return employeeService.update(id, body);
    }
}
