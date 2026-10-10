package com.civicconnect.controller;

import com.civicconnect.dto.EmployeePatch;
import com.civicconnect.dto.EmployeeResponse;
import com.civicconnect.dto.NewEmployee;
import com.civicconnect.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Employee accounts - Management only. No DELETE: set active = false instead. */
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
        return new PagedModel<>(employeeService.list(pageable));
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

    @PatchMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeePatch body) {
        return employeeService.update(id, body);
    }
}
