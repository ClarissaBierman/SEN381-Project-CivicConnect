package com.civicconnect.access.controller;

import com.civicconnect.access.dto.HandlerOption;
import com.civicconnect.access.service.EmployeeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Management: the active handlers who cover a category - the choices for "assign to handler" (FR-008). */
@RestController
public class CategoryHandlersController {

    private final EmployeeService employeeService;

    public CategoryHandlersController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/api/v1/categories/{id}/handlers")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public List<HandlerOption> handlers(@PathVariable Long id) {
        return employeeService.handlersFor(id);
    }
}
