package com.civicconnect.controller;

import com.civicconnect.dto.CategoryPatch;
import com.civicconnect.dto.CategoryResponse;
import com.civicconnect.dto.NewCategory;
import com.civicconnect.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API-13 to API-15. No DELETE: categories are deactivated (active = false) because old requests still use them. */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** API-13: requesters always get active categories only. */
    @GetMapping
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean active) {
        return categoryService.list(active);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    /** API-14 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGEMENT')")
    public CategoryResponse create(@Valid @RequestBody NewCategory body) {
        return categoryService.create(body);
    }

    /** API-15 */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryPatch body) {
        return categoryService.update(id, body);
    }
}
