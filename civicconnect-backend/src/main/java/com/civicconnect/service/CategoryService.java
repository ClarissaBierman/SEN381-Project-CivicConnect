package com.civicconnect.service;

import com.civicconnect.error.ErrorCode;
import com.civicconnect.dto.CategoryPatch;
import com.civicconnect.dto.CategoryResponse;
import com.civicconnect.dto.NewCategory;
import com.civicconnect.model.Category;
import com.civicconnect.repository.CategoryRepository;
import com.civicconnect.security.CurrentUser;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Management-controlled category list (FR-015). Categories are deactivated, never deleted. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUser;
    private final DbSession db;

    public CategoryService(CategoryRepository categoryRepository, CurrentUserService currentUser, DbSession db) {
        this.categoryRepository = categoryRepository;
        this.currentUser = currentUser;
        this.db = db;
    }

    /** API-13: requesters always get active categories only. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean activeOnly) {
        CurrentUser user = currentUser.get();
        db.begin(user);
        List<Category> categories = (activeOnly || user.isRequester())
                ? categoryRepository.findByActiveTrueOrderByCatNameAsc()
                : categoryRepository.findAllByOrderByCatNameAsc();
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        db.begin(currentUser.get());
        return categoryRepository.findById(id).map(CategoryResponse::from)
                .orElseThrow(() -> Errors.notFound("Category"));
    }

    /** API-14 */
    @Transactional
    public CategoryResponse create(NewCategory body) {
        db.begin(currentUser.get());
        String name = body.catName().trim();
        if (categoryRepository.existsByCatNameIgnoreCase(name)) {
            throw Errors.conflict(ErrorCode.CONFLICT, "A category with this name already exists.");
        }
        Category category = new Category();
        category.setCatName(name);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    /** API-15: rename and/or activate/deactivate. */
    @Transactional
    public CategoryResponse update(Long id, CategoryPatch body) {
        db.begin(currentUser.get());
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
