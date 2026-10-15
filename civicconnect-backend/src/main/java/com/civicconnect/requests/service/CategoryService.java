package com.civicconnect.requests.service;

import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.requests.domain.Category;
import com.civicconnect.requests.dto.CategoryPatch;
import com.civicconnect.requests.dto.CategoryResponse;
import com.civicconnect.requests.dto.NewCategory;
import com.civicconnect.requests.repository.CategoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUser;

    public CategoryService(CategoryRepository categoryRepository, CurrentUserService currentUser) {
        this.categoryRepository = categoryRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean activeOnly) {
        CurrentUser user = currentUser.get();
        List<Category> categories = (activeOnly || user.isRequester())
                ? categoryRepository.findByActiveTrueOrderByCatNameAsc()
                : categoryRepository.findAllByOrderByCatNameAsc();
        return categories.stream().map(CategoryResponse::from).toList();
    }

    /** Requesters only see active categories, here as in the list (FR-015): an inactive one is "not found". */
    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        CurrentUser user = currentUser.get();
        return categoryRepository.findById(id)
                .filter(c -> c.isActive() || !user.isRequester())
                .map(CategoryResponse::from)
                .orElseThrow(() -> Errors.notFound("Category"));
    }

    @Transactional
    public CategoryResponse create(NewCategory body) {
        String name = body.catName().trim();
        if (categoryRepository.existsByCatNameIgnoreCase(name)) {
            throw Errors.conflict(ErrorCode.CONFLICT, "A category with this name already exists.");
        }
        Category category = new Category();
        category.setCatName(name);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryPatch body) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> Errors.notFound("Category"));
        if (body.catName() != null) {
            String name = body.catName().trim();
            if (!name.equalsIgnoreCase(category.getCatName()) && categoryRepository.existsByCatNameIgnoreCase(name)) {
                throw Errors.conflict(ErrorCode.CONFLICT, "A category with this name already exists.");
            }
            category.setCatName(name);
        }
        if (body.active() != null) {
            category.setActive(body.active());
        }
        return CategoryResponse.from(category);
    }
}
