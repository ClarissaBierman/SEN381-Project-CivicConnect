package com.civicconnect.controller;

import com.civicconnect.dto.CategoryResponse;
import com.civicconnect.dto.HandlerCategoriesUpdate;
import com.civicconnect.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API-16: which categories a handler covers (FR-005). Management only. */
@RestController
@RequestMapping("/api/v1/employees/{id}/categories")
@PreAuthorize("hasRole('MANAGEMENT')")
public class HandlerCategoryController {

    private final EmployeeService employeeService;

    public HandlerCategoryController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public List<CategoryResponse> get(@PathVariable Long id) {
        return employeeService.categories(id);
    }

    /** Replaces the handler's categories with the list sent. */
    @PutMapping
    public List<CategoryResponse> replace(@PathVariable Long id, @Valid @RequestBody HandlerCategoriesUpdate body) {
        return employeeService.replaceCategories(id, body);
    }
}
